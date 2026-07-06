package dev.wxdao.ccsmartcard;

import dev.wxdao.ccsmartcard.block.ModBlocks;
import dev.wxdao.ccsmartcard.block.FingerprintScannerBlock;
import dev.wxdao.ccsmartcard.block.SmartCardReaderBlock;
import dev.wxdao.ccsmartcard.block.entity.FingerprintScannerBlockEntity;
import dev.wxdao.ccsmartcard.block.entity.SmartCardReaderBlockEntity;
import dev.wxdao.ccsmartcard.item.CardColours;
import dev.wxdao.ccsmartcard.item.SmartCardItem;
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
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
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
    public static void smartCardRecipeCreatesDefaultAndDyedCards(GameTestHelper helper) {
        ItemStack plain = craft(helper,
                new ItemStack(Items.PAPER),
                new ItemStack(Items.REDSTONE),
                new ItemStack(Items.COPPER_INGOT));
        helper.assertTrue(SmartCardItem.isSmartCard(plain), "Smart Card recipe should craft a Smart Card");
        helper.assertFalse(plain.has(DataComponents.DYED_COLOR), "Smart Card recipe without dye should create the default card");

        ItemStack dyed = craft(helper,
                new ItemStack(Items.PAPER),
                new ItemStack(Items.REDSTONE),
                new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.RED_DYE),
                new ItemStack(Items.BLUE_DYE));
        CardColours.ColourTracker tracker = new CardColours.ColourTracker();
        tracker.addColour(DyeColor.RED);
        tracker.addColour(DyeColor.BLUE);
        assertDyedColour(helper, dyed, tracker.getColour(), "dyed Smart Card recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void dyeAndClearKeepCardMetadata(GameTestHelper helper) {
        ItemStack card = metadataCard();

        ItemStack dyed = craft(helper, card.copy(), new ItemStack(Items.RED_DYE));
        assertCardMetadata(helper, dyed, "dyed card");
        assertDyedColour(helper, dyed, CardColours.getDyeColour(DyeColor.RED), "dyed card");

        ItemStack cleared = craft(helper, dyed.copy(), new ItemStack(Items.WET_SPONGE));
        assertCardMetadata(helper, cleared, "cleared card");
        helper.assertFalse(cleared.has(DataComponents.DYED_COLOR), "Wet Sponge should clear Smart Card dyed colour");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void readerCardPathsKeepDyedColour(GameTestHelper helper) {
        helper.setBlock(READER_POS, ModBlocks.SMART_CARD_READER.get().defaultBlockState());
        SmartCardReaderBlockEntity reader = helper.getBlockEntity(READER_POS);
        ItemStack card = metadataCard();
        card.set(DataComponents.DYED_COLOR, new DyedItemColor(CardColours.getDyeColour(DyeColor.CYAN), false));

        helper.assertTrue(reader.insertCard(card.copy(), Direction.WEST),
                "Smart Card Reader should accept a dyed Smart Card");
        assertDyedColour(helper, reader.getCard(), CardColours.getDyeColour(DyeColor.CYAN), "inserted reader card");

        ItemStack updateCard = ItemStack.parseOptional(
                helper.getLevel().registryAccess(),
                reader.getUpdateTag(helper.getLevel().registryAccess()).getCompound("Card"));
        assertCardMetadata(helper, updateCard, "reader update tag card");
        assertDyedColour(helper, updateCard, CardColours.getDyeColour(DyeColor.CYAN), "reader update tag card");
        helper.assertTrue(reader.getUpdatePacket() != null, "Smart Card Reader should provide a block entity update packet");

        ItemStack removed = reader.removeCard();
        assertCardMetadata(helper, removed, "removed reader card");
        assertDyedColour(helper, removed, CardColours.getDyeColour(DyeColor.CYAN), "removed reader card");

        helper.assertTrue(reader.insertCard(removed.copy(), Direction.SOUTH),
                "Smart Card Reader should reinsert a dyed Smart Card");
        ItemStack destroyed = reader.removeCardForBlockDestroy();
        assertCardMetadata(helper, destroyed, "destroyed reader card");
        assertDyedColour(helper, destroyed, CardColours.getDyeColour(DyeColor.CYAN), "destroyed reader card");
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

    private static ItemStack craft(GameTestHelper helper, ItemStack... stacks) {
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < stacks.length; i++) {
            items.set(i, stacks[i]);
        }

        CraftingInput input = CraftingInput.of(3, 3, items);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "Expected a crafting recipe for " + Arrays.toString(stacks));
        ItemStack result = recipe.orElseThrow().value().assemble(input, helper.getLevel().registryAccess());
        helper.assertFalse(result.isEmpty(), "Crafting recipe returned an empty result for " + Arrays.toString(stacks));
        return result;
    }

    private static ItemStack metadataCard() {
        ItemStack card = new ItemStack(ModBlocks.SMART_CARD.get());
        SmartCardItem.setCardId(card, 42);
        SmartCardItem.setLabel(card, "Test Card");
        SmartCardItem.setIssued(card, true);
        return card;
    }

    private static void assertCardMetadata(GameTestHelper helper, ItemStack stack, String operation) {
        helper.assertTrue(Integer.valueOf(42).equals(SmartCardItem.getCardId(stack)),
                operation + " should keep the Card ID");
        helper.assertTrue("Test Card".equals(SmartCardItem.getLabel(stack)),
                operation + " should keep the Card Label");
        helper.assertTrue(SmartCardItem.isIssued(stack), operation + " should keep the Issued state");
    }

    private static void assertDyedColour(GameTestHelper helper, ItemStack stack, int expectedColour, String operation) {
        DyedItemColor colour = stack.get(DataComponents.DYED_COLOR);
        helper.assertTrue(colour != null, operation + " should have a dyed colour");
        helper.assertTrue(colour.rgb() == expectedColour,
                operation + " should have colour 0x" + Integer.toHexString(expectedColour)
                        + ", got 0x" + Integer.toHexString(colour.rgb()));
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
