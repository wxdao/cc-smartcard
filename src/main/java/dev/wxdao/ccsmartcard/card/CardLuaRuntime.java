package dev.wxdao.ccsmartcard.card;

import com.mojang.logging.LogUtils;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.LuaTask;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;
import dan200.computercraft.core.ComputerContext;
import dan200.computercraft.core.apis.FSAPI;
import dan200.computercraft.core.apis.HTTPAPI;
import dan200.computercraft.core.apis.IAPIEnvironment;
import dan200.computercraft.core.apis.OSAPI;
import dan200.computercraft.core.apis.PeripheralAPI;
import dan200.computercraft.core.apis.RedstoneAPI;
import dan200.computercraft.core.apis.TermAPI;
import dan200.computercraft.core.computer.ComputerEnvironment;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.core.computer.GlobalEnvironment;
import dan200.computercraft.core.computer.TimeoutState;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;
import dan200.computercraft.core.lua.CobaltLuaMachine;
import dan200.computercraft.core.lua.MachineEnvironment;
import dan200.computercraft.core.lua.MachineResult;
import dan200.computercraft.core.metrics.MetricsObserver;
import dan200.computercraft.core.redstone.RedstoneState;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.impl.AbstractComputerCraftAPI;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class CardLuaRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long COMPUTER_TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final int EVENT_QUEUE_LIMIT = 256;

    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "CC SmartCard Lua timeout");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private static final WorkMonitor IMMEDIATE_WORK_MONITOR = new WorkMonitor() {
        @Override
        public boolean canWork() {
            return true;
        }

        @Override
        public boolean shouldWork() {
            return true;
        }

        @Override
        public void trackWork(long time, TimeUnit unit) {
        }
    };

    private static final String BOOTSTRAP_LUA = """
            local expect

            _G.bit = {
                bnot = bit32.bnot,
                band = bit32.band,
                bor = bit32.bor,
                bxor = bit32.bxor,
                brshift = bit32.arshift,
                blshift = bit32.lshift,
                blogic_rshift = bit32.rshift,
            }

            function os.version()
                return "CraftOS 1.9"
            end

            function os.pullEventRaw(filter)
                return coroutine.yield(filter)
            end

            function os.pullEvent(filter)
                local event = table.pack(os.pullEventRaw(filter))
                if event[1] == "terminate" then
                    error("Terminated", 0)
                end
                return table.unpack(event, 1, event.n)
            end

            function loadfile(filename, mode, env)
                if type(mode) == "table" and env == nil then
                    mode, env = nil, mode
                end

                if expect then
                    expect(1, filename, "string")
                    expect(2, mode, "string", "nil")
                    expect(3, env, "table", "nil")
                elseif type(filename) ~= "string" then
                    error("bad argument #1 (string expected, got " .. type(filename) .. ")", 2)
                end

                local file, err = fs.open(filename, "r")
                if not file then return nil, err or "File not found" end
                local contents = file.readAll()
                file.close()
                return load(contents, "@" .. filename, mode, env)
            end

            function dofile(filename)
                if expect then
                    expect(1, filename, "string")
                elseif type(filename) ~= "string" then
                    error("bad argument #1 (string expected, got " .. type(filename) .. ")", 2)
                end
                local fn, err = loadfile(filename, nil, _G)
                if not fn then error(err, 2) end
                return fn()
            end

            expect = dofile("/rom/modules/main/cc/expect.lua").expect

            function sleep(time)
                expect(1, time, "number", "nil")
                local timer = os.startTimer(time or 0)
                repeat
                    local _, token = os.pullEvent("timer")
                until token == timer
            end

            function write(value)
                expect(1, value, "string", "number")
                term.write(tostring(value))
                return 0
            end

            function print(...)
                local count = select("#", ...)
                for i = 1, count do
                    if i > 1 then term.write("\\t") end
                    term.write(tostring(select(i, ...)))
                end
                term.write("\\n")
                return 1
            end

            function printError(...)
                local oldColour
                if colors and term.isColour and term.isColour() then
                    oldColour = term.getTextColour()
                    term.setTextColour(colors.red)
                end
                print(...)
                if oldColour then term.setTextColour(oldColour) end
            end

            function read(_, _, _, default)
                return default or ""
            end

            function os.run(env, path, ...)
                expect(1, env, "table")
                expect(2, path, "string")
                setmetatable(env, { __index = _G })
                local fn, err = loadfile(path, nil, env)
                if not fn then
                    if err and err ~= "" then printError(err) end
                    return false
                end
                local ok, runErr = pcall(fn, ...)
                if not ok then
                    if runErr and runErr ~= "" then printError(runErr) end
                    return false
                end
                return true
            end

            local loading = {}
            function os.loadAPI(path)
                expect(1, path, "string")
                local name = fs.getName(path)
                if name:sub(-4) == ".lua" then name = name:sub(1, -5) end
                if loading[name] then error("API " .. name .. " is already being loaded", 2) end
                loading[name] = true

                local env = setmetatable({}, { __index = _G })
                local fn, err = loadfile(path, nil, env)
                if not fn then
                    loading[name] = nil
                    error("Failed to load API " .. name .. " due to " .. err, 2)
                end
                local ok, runErr = pcall(fn)
                if not ok then
                    loading[name] = nil
                    error("Failed to load API " .. name .. " due to " .. runErr, 2)
                end

                local api = {}
                for key, value in pairs(env) do
                    if key ~= "_ENV" then api[key] = value end
                end
                _G[name] = api
                loading[name] = nil
                return true
            end

            function os.unloadAPI(name)
                expect(1, name, "string")
                if name ~= "_G" and type(_G[name]) == "table" then _G[name] = nil end
            end

            os.sleep = sleep

            local nativeShutdown = os.shutdown
            function os.shutdown()
                nativeShutdown()
                while true do coroutine.yield() end
            end

            local nativeReboot = os.reboot
            function os.reboot()
                nativeReboot()
                while true do coroutine.yield() end
            end

            local requireBuilder = dofile("/rom/modules/main/cc/require.lua")
            require, package = requireBuilder.make(_ENV, "/")

            local function load_api(path)
                local ok, err = pcall(os.loadAPI, path)
                if not ok then error(err, 0) end
            end

            local apis = {
                "/rom/apis/colors.lua",
                "/rom/apis/colours.lua",
                "/rom/apis/keys.lua",
                "/rom/apis/term.lua",
                "/rom/apis/fs.lua",
                "/rom/apis/io.lua",
                "/rom/apis/parallel.lua",
                "/rom/apis/textutils.lua",
                "/rom/apis/settings.lua",
                "/rom/apis/vector.lua",
                "/rom/apis/paintutils.lua",
                "/rom/apis/window.lua",
                "/rom/apis/help.lua",
                "/rom/apis/peripheral.lua",
                "/rom/apis/disk.lua",
                "/rom/apis/gps.lua",
                "/rom/apis/rednet.lua",
                "/rom/apis/http/http.lua",
            }

            for _, path in ipairs(apis) do
                if fs.exists(path) and not fs.isDir(path) then load_api(path) end
            end

            if fs.exists("/.settings") then settings.load("/.settings") end

            local function finish(ok, ...)
                if ok then
                    cc_smartcard_runtime.finish(true, ...)
                else
                    cc_smartcard_runtime.finish(false, tostring(...))
                end
                while true do coroutine.yield() end
            end

            local function find_handle(loaded)
                if type(loaded) == "function" then return loaded end
                if type(loaded) == "table" then return loaded.handle end
                return rawget(_G, "handle")
            end

            local ok, loaded = pcall(dofile, "/main.lua")
            if not ok then finish(false, loaded) end

            local handle = find_handle(loaded)
            if type(handle) ~= "function" then finish(false, "missing handle") end

            finish(pcall(handle,
                cc_smartcard_runtime.command(),
                cc_smartcard_runtime.args(),
                cc_smartcard_runtime.context()))
            """;

    private CardLuaRuntime() {
    }

    public static Object[] call(MinecraftServer server, int cardId, String label, String command, Object args)
            throws Exception {
        return call(server, cardId, label, command, args, new CardInvocation());
    }

    public static Object[] call(MinecraftServer server, int cardId, String label, String command, Object args,
            CardInvocation invocation) throws Exception {
        CardResultApi resultApi = new CardResultApi(command, args, cardContext(cardId, label));
        WritableMount cardMount = CardStorage.mount(server, cardId);
        CardGlobalEnvironment globalEnvironment = new CardGlobalEnvironment(server);
        ComputerContext context = ComputerContext.builder(globalEnvironment).build();
        FileSystem fileSystem = null;
        CobaltLuaMachine machine = null;
        CardTimeoutState timeout = new CardTimeoutState();
        List<ILuaAPI> apis = null;

        try {
            fileSystem = createFileSystem(cardMount, globalEnvironment);
            Terminal terminal = new Terminal(51, 19, true);
            CardComputerEnvironment computerEnvironment = new CardComputerEnvironment(server, cardMount);
            CardApiEnvironment apiEnvironment = new CardApiEnvironment(
                    server, cardId, label, computerEnvironment, globalEnvironment, terminal, fileSystem);

            apis = createApis(apiEnvironment, context, resultApi);
            MachineEnvironment machineEnvironment = new MachineEnvironment(
                    apiEnvironment,
                    MetricsObserver.discard(),
                    timeout,
                    apis,
                    context.luaMethods(),
                    globalEnvironment.getHostString());

            machine = new CobaltLuaMachine(machineEnvironment, new ByteArrayInputStream(BOOTSTRAP_LUA.getBytes(StandardCharsets.UTF_8)));
            CobaltLuaMachine runningMachine = machine;
            invocation.setCancelAction(() -> {
                apiEnvironment.cancel();
                timeout.cancel();
                runningMachine.close();
            });
            startupApis(apis);
            Object[] result = run(machine, apiEnvironment, apis, resultApi, timeout, invocation);
            if (result.length == 0 || !Boolean.TRUE.equals(result[0])) {
                throw new CardRuntimeException(result.length >= 2 && result[1] != null ? result[1].toString() : "runtime error");
            }
            Object[] values = new Object[result.length - 1];
            System.arraycopy(result, 1, values, 0, values.length);
            return values;
        } finally {
            invocation.setCancelAction(null);
            timeout.stop();
            if (machine != null) {
                machine.close();
            }
            if (apis != null) {
                shutdownApis(apis);
            }
            if (fileSystem != null) {
                fileSystem.close();
            }
            if (!context.close(1, TimeUnit.SECONDS)) {
                LOGGER.warn("Timed out while closing smart card Lua runtime for card {}", cardId);
            }
        }
    }

    private static FileSystem createFileSystem(WritableMount cardMount, GlobalEnvironment globalEnvironment)
            throws CardRuntimeException, FileSystemException {
        FileSystem fileSystem = new FileSystem("hdd", cardMount);
        Mount romMount = globalEnvironment.createResourceMount("computercraft", "lua/rom");
        if (romMount == null) {
            fileSystem.close();
            throw new CardRuntimeException("Cannot mount ROM");
        }
        try {
            fileSystem.mount("rom", "rom", romMount);
        } catch (FileSystemException e) {
            fileSystem.close();
            throw e;
        }
        return fileSystem;
    }

    private static List<ILuaAPI> createApis(CardApiEnvironment environment, ComputerContext context, CardResultApi resultApi) {
        List<ILuaAPI> apis = new ArrayList<>();
        apis.add(new TermAPI(environment));
        apis.add(new RedstoneAPI(new RedstoneState()));
        apis.add(new FSAPI(environment));
        apis.add(new PeripheralAPI(environment, context.peripheralMethods()));
        apis.add(new OSAPI(environment));
        apis.add(new HTTPAPI(environment));
        apis.add(resultApi);
        return apis;
    }

    private static void startupApis(List<ILuaAPI> apis) {
        for (ILuaAPI api : apis) {
            api.startup();
        }
    }

    private static void updateApis(List<ILuaAPI> apis) {
        for (ILuaAPI api : apis) {
            api.update();
        }
    }

    private static void shutdownApis(List<ILuaAPI> apis) {
        for (ILuaAPI api : apis) {
            try {
                api.shutdown();
            } catch (RuntimeException e) {
                LOGGER.warn("Error shutting down smart card Lua API {}", String.join(",", api.getNames()), e);
            }
        }
    }

    private static Object[] run(CobaltLuaMachine machine, CardApiEnvironment environment, List<ILuaAPI> apis,
            CardResultApi resultApi, CardTimeoutState timeout, CardInvocation invocation) throws Exception {
        long nextApiUpdate = System.nanoTime();

        checkCancelled(invocation, machine);
        handleMachineResult(handleEvent(machine, timeout, null, new Object[0]));
        while (!resultApi.result().isDone()) {
            checkCancelled(invocation, machine);
            long now = System.nanoTime();

            environment.queueExpiredTimers(now);
            if (now >= nextApiUpdate) {
                updateApis(apis);
                nextApiUpdate = now + COMPUTER_TICK_NANOS;
            }

            CardEvent event = environment.pollEvent(waitMillis(environment, now, nextApiUpdate));
            checkCancelled(invocation, machine);
            if (event != null) {
                handleMachineResult(handleEvent(machine, timeout, event.name(), event.args()));
            }
            if (environment.isStopped() && !resultApi.result().isDone()) {
                throw new CardRuntimeException("shutdown");
            }
        }

        try {
            return resultApi.result().get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new CardRuntimeException(cause == null ? "runtime error" : cause.getMessage());
        }
    }

    private static void checkCancelled(CardInvocation invocation, CobaltLuaMachine machine) throws CardRuntimeException {
        if (invocation.isCancelled()) {
            machine.close();
            throw new CardRuntimeException("cancelled");
        }
    }

    private static long waitMillis(CardApiEnvironment environment, long now, long nextApiUpdate) {
        long waitNanos = Math.min(TimeUnit.MILLISECONDS.toNanos(10), Math.max(0, nextApiUpdate - now));
        long timerNanos = environment.nanosUntilNextTimer(now);
        if (timerNanos >= 0) {
            waitNanos = Math.min(waitNanos, timerNanos);
        }
        return waitNanos <= 0 ? 0 : Math.max(1, TimeUnit.NANOSECONDS.toMillis(waitNanos));
    }

    private static MachineResult handleEvent(CobaltLuaMachine machine, CardTimeoutState timeout, String event, Object[] args) {
        timeout.beforeHandleEvent();
        try {
            return machine.handleEvent(event, args);
        } finally {
            timeout.afterHandleEvent();
        }
    }

    private static void handleMachineResult(MachineResult result) throws CardRuntimeException {
        if (result == MachineResult.TIMEOUT) {
            throw new CardRuntimeException("timeout");
        }
        if (result.isError()) {
            String message = result.getMessage();
            throw new CardRuntimeException(message == null || message.isEmpty() ? "runtime error" : message);
        }
    }

    private static long currentDayTime(MinecraftServer server) {
        if (server.isSameThread()) {
            return server.overworld().getDayTime();
        }

        CompletableFuture<Long> dayTime = new CompletableFuture<>();
        server.execute(() -> dayTime.complete(server.overworld().getDayTime()));
        try {
            return dayTime.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading world time", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to read world time", e.getCause());
        }
    }

    private static Map<String, Object> cardContext(int cardId, String label) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("cardId", cardId);
        if (label != null) {
            ctx.put("label", label);
        }
        return ctx;
    }

    public static final class CardRuntimeException extends Exception {
        private CardRuntimeException(String message) {
            super(message);
        }
    }

    public static final class CardInvocation {
        private final long id;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final Object cancelLock = new Object();
        private Runnable cancelAction;

        public CardInvocation() {
            this(0L);
        }

        public CardInvocation(long id) {
            this.id = id;
        }

        public long id() {
            return id;
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            Runnable action;
            synchronized (cancelLock) {
                action = cancelAction;
            }
            if (action != null) {
                action.run();
            }
        }

        private void setCancelAction(Runnable action) {
            boolean runNow;
            synchronized (cancelLock) {
                cancelAction = action;
                runNow = action != null && cancelled.get();
            }
            if (runNow) {
                action.run();
            }
        }
    }

    private record CardGlobalEnvironment(MinecraftServer server) implements GlobalEnvironment {
        @Override
        public String getHostString() {
            return "ComputerCraft " + ComputerCraftAPI.getInstalledVersion()
                    + " (Minecraft " + SharedConstants.getCurrentVersion().getName() + ")";
        }

        @Override
        public String getUserAgent() {
            return "computercraft/" + ComputerCraftAPI.getInstalledVersion();
        }

        @Override
        public Mount createResourceMount(String domain, String subPath) {
            return ComputerCraftAPI.createResourceMount(server, domain, subPath);
        }

        @Override
        public InputStream createResourceFile(String domain, String path) {
            return AbstractComputerCraftAPI.getResourceFile(server, domain, path);
        }
    }

    private record CardComputerEnvironment(MinecraftServer server, WritableMount cardMount) implements ComputerEnvironment {
        @Override
        public int getDay() {
            long shiftedDayTime = currentDayTime(server) + 6000L;
            return (int) Math.floorDiv(shiftedDayTime, 24000L) + 1;
        }

        @Override
        public double getTimeOfDay() {
            long shiftedTime = Math.floorMod(currentDayTime(server) + 6000L, 24000L);
            return shiftedTime / 1000.0;
        }

        @Override
        public MetricsObserver getMetrics() {
            return MetricsObserver.discard();
        }

        @Override
        public WritableMount createRootMount() {
            return cardMount;
        }
    }

    private static final class CardApiEnvironment implements IAPIEnvironment, ILuaContext {
        private final MinecraftServer server;
        private final int cardId;
        private final ComputerEnvironment computerEnvironment;
        private final GlobalEnvironment globalEnvironment;
        private final Terminal terminal;
        private final FileSystem fileSystem;
        private final MetricsObserver metrics = MetricsObserver.discard();
        private final Object queueLock = new Object();
        private final ArrayDeque<CardEvent> eventQueue = new ArrayDeque<>();
        private final PriorityQueue<CardTimer> timers = new PriorityQueue<>();
        private final AtomicLong nextTaskId = new AtomicLong();
        private IPeripheralChangeListener peripheralChangeListener;
        private String label;
        private int nextTimerToken;
        private volatile boolean stopped;

        private CardApiEnvironment(MinecraftServer server, int cardId, String label, ComputerEnvironment computerEnvironment,
                GlobalEnvironment globalEnvironment, Terminal terminal, FileSystem fileSystem) {
            this.server = server;
            this.cardId = cardId;
            this.label = label;
            this.computerEnvironment = computerEnvironment;
            this.globalEnvironment = globalEnvironment;
            this.terminal = terminal;
            this.fileSystem = fileSystem;
        }

        @Override
        public int getComputerID() {
            return cardId;
        }

        @Override
        public ComputerEnvironment getComputerEnvironment() {
            return computerEnvironment;
        }

        @Override
        public GlobalEnvironment getGlobalEnvironment() {
            return globalEnvironment;
        }

        @Override
        public WorkMonitor getMainThreadMonitor() {
            return IMMEDIATE_WORK_MONITOR;
        }

        @Override
        public Terminal getTerminal() {
            return terminal;
        }

        @Override
        public FileSystem getFileSystem() {
            return fileSystem;
        }

        @Override
        public void shutdown() {
            stopped = true;
            wake();
        }

        @Override
        public void reboot() {
            stopped = true;
            wake();
        }

        @Override
        public void queueEvent(String event, Object... args) {
            if (event == null) {
                return;
            }
            synchronized (queueLock) {
                if (eventQueue.size() >= EVENT_QUEUE_LIMIT) {
                    return;
                }
                eventQueue.add(new CardEvent(event, args == null ? new Object[0] : args));
                queueLock.notifyAll();
            }
        }

        @Override
        public void setPeripheralChangeListener(IPeripheralChangeListener listener) {
            peripheralChangeListener = listener;
        }

        @Override
        public IPeripheral getPeripheral(ComputerSide side) {
            return null;
        }

        @Override
        public String getLabel() {
            return label;
        }

        @Override
        public void setLabel(String label) {
            this.label = label;
        }

        @Override
        public int startTimer(long ticks) {
            long delay = Math.max(0, ticks) * COMPUTER_TICK_NANOS;
            synchronized (queueLock) {
                int token = nextTimerToken++;
                timers.add(new CardTimer(token, System.nanoTime() + delay));
                queueLock.notifyAll();
                return token;
            }
        }

        @Override
        public void cancelTimer(int token) {
            synchronized (queueLock) {
                timers.removeIf(timer -> timer.token() == token);
            }
        }

        @Override
        public MetricsObserver metrics() {
            return metrics;
        }

        @Override
        public long issueMainThreadTask(LuaTask task) throws LuaException {
            long taskId = nextTaskId.incrementAndGet();
            if (server.isSameThread()) {
                completeMainThreadTask(taskId, task);
            } else {
                server.execute(() -> completeMainThreadTask(taskId, task));
            }
            return taskId;
        }

        private void completeMainThreadTask(long taskId, LuaTask task) {
            try {
                Object[] result = task.execute();
                Object[] event = new Object[2 + (result == null ? 0 : result.length)];
                event[0] = taskId;
                event[1] = true;
                if (result != null) {
                    System.arraycopy(result, 0, event, 2, result.length);
                }
                queueEvent("task_complete", event);
            } catch (LuaException e) {
                queueEvent("task_complete", taskId, false, e.getMessage());
            } catch (Exception e) {
                LOGGER.warn("Error running smart card Lua main-thread task", e);
                queueEvent("task_complete", taskId, false, e.toString());
            }
        }

        private CardEvent pollEvent(long waitMillis) throws InterruptedException {
            synchronized (queueLock) {
                if (eventQueue.isEmpty() && waitMillis > 0 && !stopped) {
                    queueLock.wait(waitMillis);
                }
                return eventQueue.poll();
            }
        }

        private void cancel() {
            stopped = true;
            wake();
        }

        private void wake() {
            synchronized (queueLock) {
                queueLock.notifyAll();
            }
        }

        private void queueExpiredTimers(long now) {
            synchronized (queueLock) {
                while (!timers.isEmpty() && timers.peek().deadlineNanos() <= now) {
                    CardTimer timer = timers.poll();
                    eventQueue.add(new CardEvent(IAPIEnvironment.TIMER_EVENT, new Object[]{timer.token()}));
                }
                if (!eventQueue.isEmpty()) {
                    queueLock.notifyAll();
                }
            }
        }

        private long nanosUntilNextTimer(long now) {
            synchronized (queueLock) {
                CardTimer timer = timers.peek();
                return timer == null ? -1 : Math.max(0, timer.deadlineNanos() - now);
            }
        }

        private boolean isStopped() {
            return stopped;
        }
    }

    private static final class CardTimeoutState extends TimeoutState {
        private long generation;
        private ScheduledFuture<?> softAbortTask;
        private ScheduledFuture<?> hardAbortTask;

        @Override
        public void refresh() {
        }

        private void beforeHandleEvent() {
            long nextGeneration;
            synchronized (this) {
                stopLocked();
                nextGeneration = ++generation;
                paused = false;
                softAbort = false;
                hardAbort = false;
                softAbortTask = TIMEOUT_EXECUTOR.schedule(
                        () -> softAbort(nextGeneration), TimeoutState.TIMEOUT, TimeUnit.NANOSECONDS);
                hardAbortTask = TIMEOUT_EXECUTOR.schedule(
                        () -> hardAbort(nextGeneration),
                        TimeoutState.TIMEOUT + TimeoutState.ABORT_TIMEOUT,
                        TimeUnit.NANOSECONDS);
            }
            updateListeners();
        }

        private void afterHandleEvent() {
            synchronized (this) {
                ++generation;
                stopLocked();
                paused = false;
                softAbort = false;
                hardAbort = false;
            }
            updateListeners();
        }

        private synchronized void stop() {
            ++generation;
            stopLocked();
            paused = false;
            softAbort = false;
            hardAbort = false;
            updateListeners();
        }

        private synchronized void cancel() {
            ++generation;
            stopLocked();
            paused = false;
            softAbort = true;
            hardAbort = true;
            updateListeners();
        }

        private void stopLocked() {
            if (softAbortTask != null) {
                softAbortTask.cancel(false);
                softAbortTask = null;
            }
            if (hardAbortTask != null) {
                hardAbortTask.cancel(false);
                hardAbortTask = null;
            }
        }

        private void softAbort(long taskGeneration) {
            synchronized (this) {
                if (taskGeneration != generation) {
                    return;
                }
                if (softAbort) {
                    return;
                }
                softAbort = true;
            }
            updateListeners();
        }

        private void hardAbort(long taskGeneration) {
            synchronized (this) {
                if (taskGeneration != generation) {
                    return;
                }
                softAbort = true;
                hardAbort = true;
            }
            updateListeners();
        }
    }

    private static final class CardResultApi implements ILuaAPI {
        private final String command;
        private final Object args;
        private final Map<String, Object> context;
        private final CompletableFuture<Object[]> result = new CompletableFuture<>();

        private CardResultApi(String command, Object args, Map<String, Object> context) {
            this.command = command;
            this.args = args;
            this.context = context;
        }

        @Override
        public String[] getNames() {
            return new String[]{"cc_smartcard_runtime"};
        }

        @LuaFunction
        public final String command() {
            return command;
        }

        @LuaFunction
        public final Object args() {
            return args;
        }

        @LuaFunction
        public final Object context() {
            return context;
        }

        @LuaFunction
        public final void finish(IArguments arguments) {
            try {
                result.complete(arguments.getAll());
            } catch (LuaException e) {
                result.complete(new Object[]{false, e.getMessage()});
            }
        }

        private CompletableFuture<Object[]> result() {
            return result;
        }
    }

    private record CardEvent(String name, Object[] args) {
    }

    private record CardTimer(int token, long deadlineNanos) implements Comparable<CardTimer> {
        @Override
        public int compareTo(CardTimer other) {
            return Long.compare(deadlineNanos, other.deadlineNanos);
        }
    }
}
