package dev.wxdao.ccsmartcard.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public final class SmartCardItem extends Item {
    private static final String CARD_ID = "cardId";
    private static final String LABEL = "label";
    private static final String ISSUED = "issued";

    public SmartCardItem(Properties properties) {
        super(properties);
    }

    public static boolean isSmartCard(ItemStack stack) {
        return stack.getItem() instanceof SmartCardItem;
    }

    public static Integer getCardId(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        return tag.contains(CARD_ID, CompoundTag.TAG_INT) ? tag.getInt(CARD_ID) : null;
    }

    public static void setCardId(ItemStack stack, int cardId) {
        updateTag(stack, tag -> tag.putInt(CARD_ID, cardId));
    }

    public static String getLabel(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        return tag.contains(LABEL, CompoundTag.TAG_STRING) ? tag.getString(LABEL) : null;
    }

    public static void setLabel(ItemStack stack, String label) {
        updateTag(stack, tag -> {
            if (label == null || label.isBlank()) {
                tag.remove(LABEL);
            } else {
                tag.putString(LABEL, label);
            }
        });
    }

    public static boolean isIssued(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        return tag.contains(ISSUED, CompoundTag.TAG_BYTE) && tag.getBoolean(ISSUED);
    }

    public static void setIssued(ItemStack stack, boolean issued) {
        updateTag(stack, tag -> tag.putBoolean(ISSUED, issued));
    }

    private static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void updateTag(ItemStack stack, java.util.function.Consumer<CompoundTag> updater) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, updater);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        Integer cardId = getCardId(stack);
        String label = getLabel(stack);
        if (label != null) {
            tooltipComponents.add(Component.translatable("tooltip.cc_smartcard.smart_card.label", label).withStyle(ChatFormatting.GRAY));
        }
        if (cardId != null) {
            tooltipComponents.add(Component.translatable("tooltip.cc_smartcard.smart_card.id", cardId).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltipComponents.add(Component.translatable(isIssued(stack)
                ? "tooltip.cc_smartcard.smart_card.issued"
                : "tooltip.cc_smartcard.smart_card.blank").withStyle(ChatFormatting.DARK_GRAY));
    }
}
