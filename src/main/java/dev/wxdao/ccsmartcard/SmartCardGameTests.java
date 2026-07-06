package dev.wxdao.ccsmartcard;

import dev.wxdao.ccsmartcard.block.ModBlocks;
import dev.wxdao.ccsmartcard.block.FingerprintScannerBlock;
import dev.wxdao.ccsmartcard.block.SmartCardReaderBlock;
import dev.wxdao.ccsmartcard.block.entity.FingerprintScannerBlockEntity;
import dev.wxdao.ccsmartcard.block.entity.SmartCardReaderBlockEntity;
import dev.wxdao.ccsmartcard.peripheral.FingerprintScannerPeripheral;
import dev.wxdao.ccsmartcard.peripheral.SmartCardReaderPeripheral;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTask;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.lua.ObjectArguments;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.WorkMonitor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@GameTestHolder(CCSmartCard.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmartCardGameTests {
    private static final BlockPos READER_POS = BlockPos.ZERO;
    private static final ILuaContext INLINE_MAIN_THREAD_CONTEXT = new ILuaContext() {
        @Override
        public long issueMainThreadTask(LuaTask task) {
            throw new UnsupportedOperationException("GameTest context executes main-thread tasks inline");
        }

        @Override
        public MethodResult executeMainThreadTask(LuaTask task) throws LuaException {
            Object[] result = task.execute();
            return MethodResult.yield(new Object[]{"task_complete", 0L, true},
                    event -> MethodResult.of(result == null ? new Object[0] : result));
        }
    };
    private static final String HTTP_SOURCE = """
            return {
                handle = function()
                    local response, err = http.get("https://example.com")
                    if not response then
                        return "http_error: " .. tostring(err)
                    end

                    local body = response.readAll()
                    response.close()
                    return body
                end
            }
            """;

    private SmartCardGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 8000)
    public static void httpRuntimeSmoke(GameTestHelper helper) {
        helper.setBlock(READER_POS, ModBlocks.SMART_CARD_READER.get().defaultBlockState());
        SmartCardReaderBlockEntity reader = helper.getBlockEntity(READER_POS);

        helper.assertTrue(reader.insertCard(new ItemStack(ModBlocks.SMART_CARD.get())),
                "Smart Card should insert into Smart Card Reader");

        SmartCardReaderPeripheral peripheral = (SmartCardReaderPeripheral) reader.getPeripheral(Direction.NORTH);
        assertSuccessful(helper, peripheral.issueSource(HTTP_SOURCE), "issueSource");

        FakeComputerAccess computer = new FakeComputerAccess();
        peripheral.attach(computer);
        MethodResult call = peripheral.call(computer, INLINE_MAIN_THREAD_CONTEXT, new ObjectArguments("ping"));
        helper.assertTrue(call.getCallback() != null, "reader.call should yield while card runtime runs");
        PendingCall pendingCall = new PendingCall(call);

        helper.succeedWhen(() -> {
            Object[] values = pendingCall.completedResult(helper, computer, "HTTP card call");
            helper.assertTrue(values.length >= 1, "reader.call should return the card result body");
            String body = String.valueOf(values[0]);
            helper.assertTrue(body.contains("Example Domain"),
                    "Expected HTTP body to contain Example Domain, got: " + body);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void insertCardStoresClickedFace(GameTestHelper helper) {
        helper.setBlock(READER_POS, ModBlocks.SMART_CARD_READER.get().defaultBlockState());
        SmartCardReaderBlockEntity reader = helper.getBlockEntity(READER_POS);

        helper.assertTrue(reader.insertCard(new ItemStack(ModBlocks.SMART_CARD.get()), Direction.NORTH),
                "Smart Card should insert into Smart Card Reader");
        helper.assertTrue(helper.getBlockState(READER_POS).getValue(SmartCardReaderBlock.CARD_FACE) == Direction.NORTH,
                "Smart Card Reader should store the clicked card face");

        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void detachCancelsWaitingRuntime(GameTestHelper helper) {
        helper.setBlock(READER_POS, ModBlocks.SMART_CARD_READER.get().defaultBlockState());
        SmartCardReaderBlockEntity reader = helper.getBlockEntity(READER_POS);

        helper.assertTrue(reader.insertCard(new ItemStack(ModBlocks.SMART_CARD.get())),
                "Smart Card should insert into Smart Card Reader");

        SmartCardReaderPeripheral peripheral = (SmartCardReaderPeripheral) reader.getPeripheral(Direction.NORTH);
        assertSuccessful(helper, peripheral.issueSource("""
                return {
                    handle = function(command)
                        if command == "warmup" then
                            return "ready"
                        end
                        sleep(60)
                        return "unexpected"
                    end
                }
                """), "issueSource");

        FakeComputerAccess computer = new FakeComputerAccess();
        peripheral.attach(computer);
        MethodResult call = peripheral.call(computer, INLINE_MAIN_THREAD_CONTEXT, new ObjectArguments("wait"));
        helper.assertTrue(call.getCallback() != null, "Long card call should yield while card runtime runs");
        helper.assertTrue(peripheral.activeInvocationCount(computer) > 0, "Long card call did not become active");
        helper.assertTrue(computer.pollEvent("smart_card_call_complete") == null,
                "Long card call should still be waiting before detach");
        PendingCall pendingCall = new PendingCall(call);

        helper.runAfterDelay(5, () -> peripheral.detach(computer));

        helper.succeedWhen(() -> {
            Object[] values = pendingCall.completedResult(helper, computer, "detached card call");
            helper.assertTrue(values.length >= 2 && values[0] == null && "cancelled".equals(values[1]),
                    "Expected cancelled error after detach, got: " + Arrays.toString(values));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void readerBlockRemovalCancelsWaitingRuntime(GameTestHelper helper) {
        helper.setBlock(READER_POS, ModBlocks.SMART_CARD_READER.get().defaultBlockState());
        SmartCardReaderBlockEntity reader = helper.getBlockEntity(READER_POS);

        helper.assertTrue(reader.insertCard(new ItemStack(ModBlocks.SMART_CARD.get())),
                "Smart Card should insert into Smart Card Reader");

        SmartCardReaderPeripheral peripheral = (SmartCardReaderPeripheral) reader.getPeripheral(Direction.NORTH);
        assertSuccessful(helper, peripheral.issueSource("""
                return {
                    handle = function()
                        sleep(60)
                        return "unexpected"
                    end
                }
                """), "issueSource");

        FakeComputerAccess computer = new FakeComputerAccess();
        peripheral.attach(computer);
        MethodResult call = peripheral.call(computer, INLINE_MAIN_THREAD_CONTEXT, new ObjectArguments("wait"));
        helper.assertTrue(call.getCallback() != null, "Long card call should yield while card runtime runs");
        helper.assertTrue(peripheral.activeInvocationCount(computer) > 0, "Long card call did not become active");
        PendingCall pendingCall = new PendingCall(call);

        helper.runAfterDelay(5, () -> helper.getLevel().destroyBlock(helper.absolutePos(READER_POS), true));

        helper.succeedWhen(() -> {
            Object[] values = pendingCall.completedResult(helper, computer, "removed reader card call");
            helper.assertTrue(values.length >= 2 && values[0] == null && "cancelled".equals(values[1]),
                    "Expected cancelled error after reader removal, got: " + Arrays.toString(values));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void fingerprintScanDoesNotConsumeOldScan(GameTestHelper helper) {
        helper.setBlock(READER_POS, ModBlocks.FINGERPRINT_SCANNER.get().defaultBlockState());
        FingerprintScannerBlockEntity scanner = helper.getBlockEntity(READER_POS);
        FingerprintScannerPeripheral peripheral = (FingerprintScannerPeripheral) scanner.getPeripheral(Direction.NORTH);
        FakeComputerAccess computer = new FakeComputerAccess();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        peripheral.attach(computer);
        scanner.scan(player, Direction.NORTH);

        MethodResult call = peripheral.scan(computer);
        helper.assertTrue(call.getCallback() != null, "scanner.scan should yield until the next scan");
        Object[] oldEvent = computer.pollEvent("fingerprint_scanner_scan_complete");
        PendingScan pendingScan = new PendingScan(call);
        MethodResult afterOldEvent = pendingScan.resume(helper, oldEvent, "old fingerprint scan");
        helper.assertTrue(afterOldEvent.getCallback() != null, "scanner.scan consumed an old scan event");

        scanner.scan(player, Direction.SOUTH);
        Object[] values = new PendingScan(afterOldEvent).completedResult(helper, computer, "new fingerprint scan");
        assertPlayerIdentity(helper, player, values);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void fingerprintScanWakesMultipleWaitingCoroutines(GameTestHelper helper) {
        helper.setBlock(READER_POS, ModBlocks.FINGERPRINT_SCANNER.get().defaultBlockState());
        FingerprintScannerBlockEntity scanner = helper.getBlockEntity(READER_POS);
        FingerprintScannerPeripheral peripheral = (FingerprintScannerPeripheral) scanner.getPeripheral(Direction.NORTH);
        FakeComputerAccess computer = new FakeComputerAccess();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        peripheral.attach(computer);
        PendingScan first = new PendingScan(peripheral.scan(computer));
        PendingScan second = new PendingScan(peripheral.scan(computer));

        scanner.scan(player, Direction.EAST);
        Object[] scanEvent = computer.pollEvent("fingerprint_scanner_scan_complete");
        assertPlayerIdentity(helper, player, first.completedResult(helper, scanEvent, "first fingerprint scan waiter"));
        assertPlayerIdentity(helper, player, second.completedResult(helper, scanEvent, "second fingerprint scan waiter"));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void fingerprintScanDetachCancelsPendingScan(GameTestHelper helper) {
        helper.setBlock(READER_POS, ModBlocks.FINGERPRINT_SCANNER.get().defaultBlockState());
        FingerprintScannerBlockEntity scanner = helper.getBlockEntity(READER_POS);
        FingerprintScannerPeripheral peripheral = (FingerprintScannerPeripheral) scanner.getPeripheral(Direction.NORTH);
        FakeComputerAccess computer = new FakeComputerAccess();

        peripheral.attach(computer);
        PendingScan pendingScan = new PendingScan(peripheral.scan(computer));
        peripheral.detach(computer);

        Object[] cancelEvent = computer.pollEvent("fingerprint_scanner_scan_complete");
        pendingScan.assertErrors(helper, cancelEvent, "scan cancelled", "detached fingerprint scan");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void fingerprintScannerReplacementCancelsPendingScan(GameTestHelper helper) {
        helper.setBlock(READER_POS, ModBlocks.FINGERPRINT_SCANNER.get().defaultBlockState());
        FingerprintScannerBlockEntity scanner = helper.getBlockEntity(READER_POS);
        FingerprintScannerPeripheral peripheral = (FingerprintScannerPeripheral) scanner.getPeripheral(Direction.NORTH);
        FakeComputerAccess computer = new FakeComputerAccess();

        peripheral.attach(computer);
        PendingScan pendingScan = new PendingScan(peripheral.scan(computer));

        helper.runAfterDelay(5,
                () -> helper.setBlock(READER_POS, ModBlocks.SMART_CARD_READER.get().defaultBlockState()));

        helper.succeedWhen(() -> {
            Object[] cancelEvent = computer.pollEvent("fingerprint_scanner_scan_complete");
            pendingScan.assertErrors(helper, cancelEvent, "scan cancelled", "replaced fingerprint scanner scan");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void fingerprintScannerLightsClickedFaceBriefly(GameTestHelper helper) {
        helper.setBlock(READER_POS, ModBlocks.FINGERPRINT_SCANNER.get().defaultBlockState());
        FingerprintScannerBlockEntity scanner = helper.getBlockEntity(READER_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        scanner.scan(player, Direction.WEST);
        helper.assertTrue(helper.getBlockState(READER_POS).getValue(FingerprintScannerBlock.LIT),
                "Fingerprint Scanner should light after a scan");
        helper.assertTrue(helper.getBlockState(READER_POS).getValue(FingerprintScannerBlock.LIT_FACE) == Direction.WEST,
                "Fingerprint Scanner should light the scanned face");

        helper.runAfterDelay(5, () -> scanner.scan(player, Direction.UP));
        helper.runAfterDelay(6, () -> helper.assertTrue(
                helper.getBlockState(READER_POS).getValue(FingerprintScannerBlock.LIT_FACE) == Direction.UP,
                "Fingerprint Scanner should keep the last scanned face lit"));
        helper.runAfterDelay(14, () -> helper.assertTrue(
                helper.getBlockState(READER_POS).getValue(FingerprintScannerBlock.LIT),
                "Fingerprint Scanner should stay lit until 10 ticks after the last scan"));

        helper.succeedOnTickWhen(15, () -> helper.assertFalse(
                helper.getBlockState(READER_POS).getValue(FingerprintScannerBlock.LIT),
                "Fingerprint Scanner should clear its light after 10 ticks from the last scan"));
    }

    private static void assertSuccessful(GameTestHelper helper, MethodResult result, String operation) {
        Object[] values = result.getResult();
        boolean success = values.length >= 1 && Boolean.TRUE.equals(values[0]);
        String detail = values.length >= 2 ? String.valueOf(values[1]) : "no detail";
        helper.assertTrue(success, operation + " failed: " + detail);
    }

    private static void assertPlayerIdentity(GameTestHelper helper, ServerPlayer player, Object[] values) {
        helper.assertTrue(values.length == 2, "scanner.scan should return UUID and name, got: " + Arrays.toString(values));
        helper.assertTrue(player.getUUID().toString().equals(values[0]),
                "scanner.scan returned wrong UUID: " + Arrays.toString(values));
        helper.assertTrue(player.getGameProfile().getName().equals(values[1]),
                "scanner.scan returned wrong player name: " + Arrays.toString(values));
    }

    private static final class PendingCall {
        private final MethodResult call;
        private Object[] completedResult;

        private PendingCall(MethodResult call) {
            this.call = call;
        }

        private Object[] completedResult(GameTestHelper helper, FakeComputerAccess computer, String operation) {
            if (completedResult != null) {
                return completedResult;
            }

            try {
                Object[] event = computer.pollEvent("smart_card_call_complete");
                helper.assertTrue(event != null, operation + " has not queued completion yet");
                MethodResult completed = call.getCallback().resume(event);
                helper.assertTrue(completed.getCallback() == null, operation + " yielded after completion");
                completedResult = completed.getResult();
                return completedResult;
            } catch (LuaException e) {
                helper.fail(operation + " failed while resuming: " + e);
                return new Object[0];
            }
        }
    }

    private static final class PendingScan {
        private final MethodResult call;

        private PendingScan(MethodResult call) {
            this.call = call;
        }

        private Object[] completedResult(GameTestHelper helper, FakeComputerAccess computer, String operation) {
            Object[] event = computer.pollEvent("fingerprint_scanner_scan_complete");
            return completedResult(helper, event, operation);
        }

        private Object[] completedResult(GameTestHelper helper, Object[] event, String operation) {
            MethodResult completed = resume(helper, event, operation);
            helper.assertTrue(completed.getCallback() == null, operation + " yielded after completion");
            return completed.getResult();
        }

        private MethodResult resume(GameTestHelper helper, Object[] event, String operation) {
            try {
                helper.assertTrue(event != null, operation + " has not queued a scanner event yet");
                return call.getCallback().resume(event);
            } catch (LuaException e) {
                helper.fail(operation + " failed while resuming: " + e);
                return MethodResult.of();
            }
        }

        private void assertErrors(GameTestHelper helper, Object[] event, String expectedMessage, String operation) {
            try {
                helper.assertTrue(event != null, operation + " has not queued cancellation yet");
                call.getCallback().resume(event);
                helper.fail(operation + " completed instead of raising a Lua error");
            } catch (LuaException e) {
                helper.assertTrue(expectedMessage.equals(e.getMessage()),
                        operation + " raised wrong error: " + e.getMessage());
            }
        }
    }

    private static final class FakeComputerAccess implements IComputerAccess {
        private static final WorkMonitor WORK_MONITOR = new WorkMonitor() {
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
        private final BlockingQueue<Object[]> events = new LinkedBlockingQueue<>();

        @Override
        public String mount(String desiredLocation, Mount mount, String driveName) {
            return desiredLocation;
        }

        @Override
        public String mountWritable(String desiredLocation, WritableMount mount, String driveName) {
            return desiredLocation;
        }

        @Override
        public void unmount(String location) {
        }

        @Override
        public int getID() {
            return 1;
        }

        @Override
        public void queueEvent(String event, Object... arguments) {
            Object[] queued = new Object[1 + arguments.length];
            queued[0] = event;
            System.arraycopy(arguments, 0, queued, 1, arguments.length);
            events.add(queued);
        }

        @Override
        public String getAttachmentName() {
            return "test";
        }

        @Override
        public Map<String, IPeripheral> getAvailablePeripherals() {
            return Map.of();
        }

        @Override
        public IPeripheral getAvailablePeripheral(String name) {
            return null;
        }

        @Override
        public WorkMonitor getMainThreadMonitor() {
            return WORK_MONITOR;
        }

        private Object[] pollEvent(String event) {
            while (true) {
                Object[] next = events.poll();
                if (next == null) {
                    return null;
                }
                if (next.length >= 1 && event.equals(next[0])) {
                    return next;
                }
            }
        }

    }
}
