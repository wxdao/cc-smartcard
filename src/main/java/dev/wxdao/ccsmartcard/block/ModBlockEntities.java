package dev.wxdao.ccsmartcard.block;

import dev.wxdao.ccsmartcard.CCSmartCard;
import dev.wxdao.ccsmartcard.block.entity.SmartCardReaderBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CCSmartCard.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmartCardReaderBlockEntity>> SMART_CARD_READER =
            BLOCK_ENTITY_TYPES.register("smart_card_reader", () -> BlockEntityType.Builder.of(
                    SmartCardReaderBlockEntity::new,
                    ModBlocks.SMART_CARD_READER.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
