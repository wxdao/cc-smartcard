package dev.wxdao.ccsmartcard.block;

import dev.wxdao.ccsmartcard.CCSmartCard;
import dev.wxdao.ccsmartcard.item.CardColours;
import dev.wxdao.ccsmartcard.item.SmartCardItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CCSmartCard.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CCSmartCard.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CCSmartCard.MOD_ID);

    public static final DeferredBlock<SmartCardReaderBlock> SMART_CARD_READER = BLOCKS.registerBlock(
            "smart_card_reader",
            SmartCardReaderBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL));

    public static final DeferredItem<BlockItem> SMART_CARD_READER_ITEM = ITEMS.registerSimpleBlockItem(
            "smart_card_reader",
            SMART_CARD_READER);

    public static final DeferredBlock<FingerprintScannerBlock> FINGERPRINT_SCANNER = BLOCKS.registerBlock(
            "fingerprint_scanner",
            FingerprintScannerBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL));

    public static final DeferredItem<BlockItem> FINGERPRINT_SCANNER_ITEM = ITEMS.registerSimpleBlockItem(
            "fingerprint_scanner",
            FINGERPRINT_SCANNER);

    public static final DeferredBlock<GateCabinetBlock> GATE_CABINET = BLOCKS.registerBlock(
            "gate_cabinet",
            GateCabinetBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    public static final DeferredItem<BlockItem> GATE_CABINET_ITEM = ITEMS.registerSimpleBlockItem(
            "gate_cabinet",
            GATE_CABINET);

    public static final DeferredBlock<GateBarrierBlock> GATE_BARRIER = BLOCKS.registerBlock(
            "gate_barrier",
            GateBarrierBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .noLootTable());

    public static final DeferredBlock<PassageSensorBlock> PASSAGE_SENSOR = BLOCKS.registerBlock(
            "passage_sensor",
            PassageSensorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion());

    public static final DeferredItem<BlockItem> PASSAGE_SENSOR_ITEM = ITEMS.registerSimpleBlockItem(
            "passage_sensor",
            PASSAGE_SENSOR);

    public static final DeferredItem<SmartCardItem> SMART_CARD = ITEMS.register(
            "smart_card",
            () -> new SmartCardItem(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_MODE_TABS.register(
            "cc_smartcard",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.cc_smartcard"))
                    .icon(() -> new ItemStack(SMART_CARD.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(SMART_CARD.get());
                        for (DyeColor dye : DyeColor.values()) {
                            output.accept(CardColours.createDyedCard(SMART_CARD.get(), dye));
                        }
                        output.accept(SMART_CARD_READER_ITEM.get());
                        output.accept(FINGERPRINT_SCANNER_ITEM.get());
                        output.accept(GATE_CABINET_ITEM.get());
                        output.accept(PASSAGE_SENSOR_ITEM.get());
                    })
                    .build());

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
