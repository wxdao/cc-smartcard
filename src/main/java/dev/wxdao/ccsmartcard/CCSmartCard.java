package dev.wxdao.ccsmartcard;

import dev.wxdao.ccsmartcard.block.ModBlockEntities;
import dev.wxdao.ccsmartcard.block.ModBlocks;
import dev.wxdao.ccsmartcard.block.entity.SmartCardReaderBlockEntity;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(CCSmartCard.MOD_ID)
public final class CCSmartCard {
    public static final String MOD_ID = "cc_smartcard";

    public CCSmartCard(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        modContainer.registerConfig(Type.SERVER, ModConfig.SPEC);

        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::addCreativeTabItems);
        modEventBus.addListener(this::registerGameTests);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModBlockEntities.SMART_CARD_READER.get(),
                SmartCardReaderBlockEntity::getPeripheral);
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModBlocks.SMART_CARD_READER_ITEM);
            event.accept(ModBlocks.SMART_CARD);
        }
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        event.register(SmartCardGameTests.class);
    }
}
