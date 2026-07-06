package dev.wxdao.ccsmartcard;

import dev.wxdao.ccsmartcard.block.ModBlockEntities;
import dev.wxdao.ccsmartcard.block.ModBlocks;
import dev.wxdao.ccsmartcard.block.entity.FingerprintScannerBlockEntity;
import dev.wxdao.ccsmartcard.block.entity.SmartCardReaderBlockEntity;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@Mod(CCSmartCard.MOD_ID)
public final class CCSmartCard {
    public static final String MOD_ID = "cc_smartcard";

    public CCSmartCard(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        modContainer.registerConfig(Type.SERVER, ModConfig.SPEC);

        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerGameTests);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModBlockEntities.SMART_CARD_READER.get(),
                SmartCardReaderBlockEntity::getPeripheral);
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModBlockEntities.FINGERPRINT_SCANNER.get(),
                FingerprintScannerBlockEntity::getPeripheral);
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        event.register(SmartCardGameTests.class);
    }
}
