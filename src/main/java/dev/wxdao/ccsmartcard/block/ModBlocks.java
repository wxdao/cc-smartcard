package dev.wxdao.ccsmartcard.block;

import dev.wxdao.ccsmartcard.CCSmartCard;
import dev.wxdao.ccsmartcard.item.SmartCardItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CCSmartCard.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CCSmartCard.MOD_ID);

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

    public static final DeferredItem<SmartCardItem> SMART_CARD = ITEMS.register(
            "smart_card",
            () -> new SmartCardItem(new Item.Properties().stacksTo(64)));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
