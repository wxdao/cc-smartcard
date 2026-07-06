package dev.wxdao.ccsmartcard.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.common.Tags;

public final class CardColours {
    public static final int DEFAULT_BODY_RGB = 0xF0F0F0;
    public static final int DEFAULT_BODY_ARGB = FastColor.ARGB32.opaque(DEFAULT_BODY_RGB);
    public static final int CHIP_ARGB = FastColor.ARGB32.opaque(0xD8A63A);

    private static final int[] COMPUTERCRAFT_COLOURS = {
            0x111111,
            0xCC4C4C,
            0x57A64E,
            0x7F664C,
            0x3366CC,
            0xB266E5,
            0x4C99B2,
            0x999999,
            0x4C4C4C,
            0xF2B2CC,
            0x7FCC19,
            0xDEDE6C,
            0x99B2F2,
            0xE57FD8,
            0xF2B233,
            DEFAULT_BODY_RGB,
    };

    private CardColours() {
    }

    public static int getCardBodyColour(ItemStack stack) {
        return DyedItemColor.getOrDefault(stack, DEFAULT_BODY_ARGB);
    }

    public static int getDyeColour(DyeColor dye) {
        return COMPUTERCRAFT_COLOURS[15 - dye.getId()];
    }

    public static ItemStack createDyedCard(Item item, DyeColor dye) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(getDyeColour(dye), false));
        return stack;
    }

    public static DyeColor getStackDye(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof DyeItem dyeItem) {
            return dyeItem.getDyeColor();
        }
        for (DyeColor dye : DyeColor.values()) {
            if (stack.is(getDyeTag(dye))) {
                return dye;
            }
        }
        return null;
    }

    private static TagKey<Item> getDyeTag(DyeColor dye) {
        return switch (dye) {
            case WHITE -> Tags.Items.DYES_WHITE;
            case ORANGE -> Tags.Items.DYES_ORANGE;
            case MAGENTA -> Tags.Items.DYES_MAGENTA;
            case LIGHT_BLUE -> Tags.Items.DYES_LIGHT_BLUE;
            case YELLOW -> Tags.Items.DYES_YELLOW;
            case LIME -> Tags.Items.DYES_LIME;
            case PINK -> Tags.Items.DYES_PINK;
            case GRAY -> Tags.Items.DYES_GRAY;
            case LIGHT_GRAY -> Tags.Items.DYES_LIGHT_GRAY;
            case CYAN -> Tags.Items.DYES_CYAN;
            case PURPLE -> Tags.Items.DYES_PURPLE;
            case BLUE -> Tags.Items.DYES_BLUE;
            case BROWN -> Tags.Items.DYES_BROWN;
            case GREEN -> Tags.Items.DYES_GREEN;
            case RED -> Tags.Items.DYES_RED;
            case BLACK -> Tags.Items.DYES_BLACK;
        };
    }

    public static final class ColourTracker {
        private int total;
        private int totalR;
        private int totalG;
        private int totalB;
        private int count;

        public void addColour(DyeColor dye) {
            int colour = getDyeColour(dye);
            int r = (colour >> 16) & 0xFF;
            int g = (colour >> 8) & 0xFF;
            int b = colour & 0xFF;
            total += Math.max(r, Math.max(g, b));
            totalR += r;
            totalG += g;
            totalB += b;
            count++;
        }

        public boolean hasColour() {
            return count > 0;
        }

        public int getColour() {
            int avgR = totalR / count;
            int avgG = totalG / count;
            int avgB = totalB / count;

            float avgTotal = (float) total / count;
            float avgMax = Math.max(avgR, Math.max(avgG, avgB));
            avgR = (int) (avgR * avgTotal / avgMax);
            avgG = (int) (avgG * avgTotal / avgMax);
            avgB = (int) (avgB * avgTotal / avgMax);

            return (avgR << 16) | (avgG << 8) | avgB;
        }
    }
}
