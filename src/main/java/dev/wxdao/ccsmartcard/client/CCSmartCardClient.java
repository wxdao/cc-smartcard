package dev.wxdao.ccsmartcard.client;

import dev.wxdao.ccsmartcard.CCSmartCard;
import dev.wxdao.ccsmartcard.block.ModBlockEntities;
import dev.wxdao.ccsmartcard.block.ModBlocks;
import dev.wxdao.ccsmartcard.item.CardColours;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = CCSmartCard.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CCSmartCardClient {
    private CCSmartCardClient() {
    }

    @SubscribeEvent
    public static void registerItemColours(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, layer) -> layer == 0 ? CardColours.getCardBodyColour(stack) : -1,
                ModBlocks.SMART_CARD.get());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.SMART_CARD_READER.get(),
                SmartCardReaderRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.GATE_CABINET.get(),
                GateCabinetRenderer::new);
    }
}
