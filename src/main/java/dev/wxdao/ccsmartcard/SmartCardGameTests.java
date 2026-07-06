package dev.wxdao.ccsmartcard;

import dev.wxdao.ccsmartcard.block.ModBlocks;
import dev.wxdao.ccsmartcard.block.SmartCardReaderBlock;
import dev.wxdao.ccsmartcard.block.entity.SmartCardReaderBlockEntity;
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
import dan200.computercraft.core.CoreConfig;
import dan200.computercraft.core.apis.http.options.Action;
import dan200.computercraft.core.apis.http.options.AddressRule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@GameTestHolder(CCSmartCard.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmartCardGameTests {
    private static final BlockPos READER_POS = BlockPos.ZERO;
    private static boolean localHttpRuleInstalled;
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
                    local response, err = http.get("http://127.0.0.1:8765/ping.txt")
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
        allowLocalHttpForGameTest();

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
            helper.assertTrue(body.contains("ok"), "Expected HTTP body to contain ok, got: " + body);
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

    private static void allowLocalHttpForGameTest() {
        if (localHttpRuleInstalled) {
            return;
        }

        List<AddressRule> rules = new ArrayList<>();
        rules.add(AddressRule.parse("127.0.0.1", OptionalInt.of(8765), Action.ALLOW.toPartial()));
        rules.addAll(CoreConfig.httpRules);
        CoreConfig.httpRules = List.copyOf(rules);
        localHttpRuleInstalled = true;
    }

    private static void assertSuccessful(GameTestHelper helper, MethodResult result, String operation) {
        Object[] values = result.getResult();
        boolean success = values.length >= 1 && Boolean.TRUE.equals(values[0]);
        String detail = values.length >= 2 ? String.valueOf(values[1]) : "no detail";
        helper.assertTrue(success, operation + " failed: " + detail);
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
