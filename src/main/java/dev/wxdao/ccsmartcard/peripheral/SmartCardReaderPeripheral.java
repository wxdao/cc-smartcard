package dev.wxdao.ccsmartcard.peripheral;

import dev.wxdao.ccsmartcard.card.CardLuaRuntime;
import dev.wxdao.ccsmartcard.card.CardStorage;
import dev.wxdao.ccsmartcard.block.entity.SmartCardReaderBlockEntity;
import dev.wxdao.ccsmartcard.item.SmartCardItem;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaCallback;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public final class SmartCardReaderPeripheral implements IPeripheral {
    private static final String CALL_COMPLETE_EVENT = "smart_card_call_complete";
    private static final ExecutorService INVOCATION_EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicLong nextThreadId = new AtomicLong();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "CC SmartCard invocation " + nextThreadId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    private final SmartCardReaderBlockEntity reader;
    private final Map<IComputerAccess, Set<CardLuaRuntime.CardInvocation>> activeInvocations = new IdentityHashMap<>();
    private final AtomicLong nextInvocationId = new AtomicLong();

    public SmartCardReaderPeripheral(SmartCardReaderBlockEntity reader) {
        this.reader = reader;
    }

    @Override
    public String getType() {
        return "smart_card_reader";
    }

    @Override
    public void attach(IComputerAccess computer) {
        reader.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        cancelInvocations(computer);
        reader.detach(computer);
    }

    @Override
    public Object getTarget() {
        return reader;
    }

    @LuaFunction(mainThread = true)
    public final boolean hasCard() {
        return reader.hasCard();
    }

    @LuaFunction(mainThread = true)
    public final Object getCardId() {
        ItemStack card = reader.getCard();
        if (card.isEmpty()) {
            return null;
        }
        Integer cardId = SmartCardItem.getCardId(card);
        if (cardId == null) {
            MinecraftServer server = server();
            if (server == null) return null;
            cardId = CardStorage.allocateCardId(server);
            SmartCardItem.setCardId(card, cardId);
            reader.updateCardMetadata();
        }
        return cardId;
    }

    @LuaFunction(mainThread = true)
    public final Object getLabel() {
        ItemStack card = reader.getCard();
        return card.isEmpty() ? null : SmartCardItem.getLabel(card);
    }

    @LuaFunction(mainThread = true)
    public final MethodResult setLabel(IArguments arguments) {
        ItemStack card = reader.getCard();
        if (card.isEmpty()) {
            return error("no_card");
        }
        try {
            SmartCardItem.setLabel(card, arguments.getString(0));
            reader.updateCardMetadata();
            return MethodResult.of(true);
        } catch (Exception e) {
            return error("bad_label", e.getMessage());
        }
    }

    @LuaFunction(mainThread = true)
    public final Object isIssued() {
        ItemStack card = reader.getCard();
        return card.isEmpty() ? null : SmartCardItem.isIssued(card);
    }

    @LuaFunction(mainThread = true)
    public final MethodResult issueSource(String source) {
        return issueFiles(Map.of("/main.lua", source));
    }

    @LuaFunction(mainThread = true)
    public final MethodResult issueFiles(Map<?, ?> rawFiles) {
        ItemStack card = reader.getCard();
        if (card.isEmpty()) {
            return error("no_card");
        }
        if (SmartCardItem.isIssued(card)) {
            return error("already_issued");
        }

        Map<String, String> files;
        try {
            files = validateFiles(rawFiles);
        } catch (IllegalArgumentException e) {
            return error("bad_files", e.getMessage());
        }

        MinecraftServer server = server();
        if (server == null) {
            return error("no_server");
        }

        try {
            Integer cardId = SmartCardItem.getCardId(card);
            if (cardId == null) {
                cardId = CardStorage.allocateCardId(server);
                SmartCardItem.setCardId(card, cardId);
            } else {
                CardStorage.clear(server, cardId);
            }
            CardStorage.writeFiles(server, cardId, files);
            SmartCardItem.setIssued(card, true);
            reader.updateCardMetadata();
            return MethodResult.of(true);
        } catch (IOException e) {
            return error("io_error", e.getMessage());
        }
    }

    @LuaFunction
    public final MethodResult call(IComputerAccess computer, ILuaContext context, IArguments arguments) {
        CardLuaRuntime.CardInvocation invocation = new CardLuaRuntime.CardInvocation(nextInvocationId.incrementAndGet());
        registerInvocation(computer, invocation);
        try {
            String command = arguments.getString(0);
            Object args = arguments.count() >= 2 ? arguments.get(1) : null;
            MethodResult snapshotTask = context.executeMainThreadTask(this::snapshotCallContext);
            return resumeAfterSnapshot(computer, invocation, snapshotTask.getCallback(), command, args, snapshotTask.getResult());
        } catch (Exception e) {
            unregisterInvocation(computer, invocation);
            return runtimeError(e);
        }
    }

    private MethodResult resumeAfterSnapshot(IComputerAccess computer, CardLuaRuntime.CardInvocation invocation,
            ILuaCallback snapshotTask, String command, Object args, Object[] event)
            throws LuaException {
        if (invocation.isCancelled()) {
            unregisterInvocation(computer, invocation);
            return error("cancelled");
        }
        MethodResult taskResult = snapshotTask.resume(event);
        if (taskResult.getCallback() != null) {
            return MethodResult.yield(taskResult.getResult(),
                    nextEvent -> resumeAfterSnapshot(computer, invocation, taskResult.getCallback(), command, args, nextEvent));
        }

        Object[] snapshotResult = taskResult.getResult();
        if (snapshotResult.length == 0 || !(snapshotResult[0] instanceof CallContextSnapshot snapshot)) {
            unregisterInvocation(computer, invocation);
            return MethodResult.of(snapshotResult);
        }

        startInvocation(computer, invocation, snapshot, command, args);
        return waitForInvocation(invocation);
    }

    private void startInvocation(IComputerAccess computer, CardLuaRuntime.CardInvocation invocation,
            CallContextSnapshot snapshot, String command, Object args) {
        CompletableFuture.runAsync(() -> {
            Object[] eventArgs;
            try {
                Object[] values = CardLuaRuntime.call(
                        snapshot.server(), snapshot.cardId(), snapshot.label(), command, args, invocation);
                eventArgs = completionResult(invocation.id(), true, values);
            } catch (Exception e) {
                eventArgs = completionResult(invocation.id(), false, runtimeErrorValues(e));
            } finally {
                unregisterInvocation(computer, invocation);
            }
            computer.queueEvent(CALL_COMPLETE_EVENT, eventArgs);
        }, INVOCATION_EXECUTOR);
    }

    private MethodResult waitForInvocation(CardLuaRuntime.CardInvocation invocation) {
        return MethodResult.pullEvent(CALL_COMPLETE_EVENT, event -> resumeInvocation(invocation, event));
    }

    private MethodResult resumeInvocation(CardLuaRuntime.CardInvocation invocation, Object[] event) {
        int offset = event.length >= 1 && CALL_COMPLETE_EVENT.equals(event[0]) ? 1 : 0;
        if (event.length < offset + 2 || !Long.valueOf(invocation.id()).equals(asLong(event[offset]))) {
            return waitForInvocation(invocation);
        }

        boolean success = Boolean.TRUE.equals(event[offset + 1]);
        int valueCount = event.length - offset - 2;
        Object[] values = new Object[valueCount];
        if (valueCount > 0) {
            System.arraycopy(event, offset + 2, values, 0, valueCount);
        }
        if (success) {
            return MethodResult.of(values);
        }
        return MethodResult.of(errorValues(values));
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Object[] completionResult(long invocationId, boolean success, Object[] values) {
        Object[] event = new Object[2 + values.length];
        event[0] = invocationId;
        event[1] = success;
        System.arraycopy(values, 0, event, 2, values.length);
        return event;
    }

    private static Object[] errorValues(Object[] values) {
        Object[] result = new Object[values.length + 1];
        result[0] = null;
        System.arraycopy(values, 0, result, 1, values.length);
        return result;
    }

    private static Object[] runtimeErrorValues(Exception e) {
        if ("cancelled".equals(e.getMessage())) {
            return new Object[]{"cancelled"};
        }
        return new Object[]{"runtime_error", e.getMessage()};
    }

    private void registerInvocation(IComputerAccess computer, CardLuaRuntime.CardInvocation invocation) {
        synchronized (activeInvocations) {
            activeInvocations.computeIfAbsent(computer, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                    .add(invocation);
        }
    }

    private void unregisterInvocation(IComputerAccess computer, CardLuaRuntime.CardInvocation invocation) {
        synchronized (activeInvocations) {
            Set<CardLuaRuntime.CardInvocation> invocations = activeInvocations.get(computer);
            if (invocations == null) {
                return;
            }
            invocations.remove(invocation);
            if (invocations.isEmpty()) {
                activeInvocations.remove(computer);
            }
        }
    }

    private void cancelInvocations(IComputerAccess computer) {
        Set<CardLuaRuntime.CardInvocation> invocations;
        synchronized (activeInvocations) {
            invocations = activeInvocations.remove(computer);
        }
        if (invocations != null) {
            invocations.forEach(CardLuaRuntime.CardInvocation::cancel);
        }
    }

    public void cancelAllInvocations() {
        Map<IComputerAccess, Set<CardLuaRuntime.CardInvocation>> invocationsByComputer;
        synchronized (activeInvocations) {
            invocationsByComputer = new IdentityHashMap<>(activeInvocations);
            activeInvocations.clear();
        }
        invocationsByComputer.values().forEach(invocations -> invocations.forEach(CardLuaRuntime.CardInvocation::cancel));
    }

    public int activeInvocationCount(IComputerAccess computer) {
        synchronized (activeInvocations) {
            Set<CardLuaRuntime.CardInvocation> invocations = activeInvocations.get(computer);
            return invocations == null ? 0 : invocations.size();
        }
    }

    private Object[] snapshotCallContext() {
        ItemStack card = reader.getCard();
        if (card.isEmpty()) {
            return errorResult("no_card");
        }
        if (!SmartCardItem.isIssued(card)) {
            return errorResult("not_issued");
        }
        Integer cardId = SmartCardItem.getCardId(card);
        if (cardId == null) {
            return errorResult("no_card_id");
        }
        MinecraftServer server = server();
        if (server == null) {
            return errorResult("no_server");
        }
        try {
            if (!CardStorage.exists(server, cardId, "/main.lua")) {
                return errorResult("missing_main");
            }
        } catch (IOException e) {
            return errorResult("runtime_error", e.getMessage());
        }
        return new Object[]{new CallContextSnapshot(server, cardId, SmartCardItem.getLabel(card))};
    }

    private Map<String, String> validateFiles(Map<?, ?> rawFiles) {
        Map<String, String> files = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawFiles.entrySet()) {
            if (!(entry.getKey() instanceof String path)) {
                throw new IllegalArgumentException("file paths must be strings");
            }
            if (!(entry.getValue() instanceof String contents)) {
                throw new IllegalArgumentException("file contents must be strings");
            }
            if (!path.startsWith("/") || path.contains("..") || path.endsWith("/")) {
                throw new IllegalArgumentException("invalid path: " + path);
            }
            files.put(path, contents);
        }
        if (!files.containsKey("/main.lua")) {
            throw new IllegalArgumentException("missing /main.lua");
        }
        return files;
    }

    private MinecraftServer server() {
        return reader.getLevel() == null ? null : reader.getLevel().getServer();
    }

    private static MethodResult error(String code) {
        return MethodResult.of(null, code);
    }

    private static MethodResult error(String code, String detail) {
        return MethodResult.of(null, code, detail);
    }

    private static MethodResult runtimeError(Exception e) {
        if ("cancelled".equals(e.getMessage())) {
            return error("cancelled");
        }
        return error("runtime_error", e.getMessage());
    }

    private static Object[] errorResult(String code) {
        return new Object[]{null, code};
    }

    private static Object[] errorResult(String code, String detail) {
        return new Object[]{null, code, detail};
    }

    private record CallContextSnapshot(MinecraftServer server, int cardId, String label) {
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof SmartCardReaderPeripheral peripheral && peripheral.reader == reader;
    }
}
