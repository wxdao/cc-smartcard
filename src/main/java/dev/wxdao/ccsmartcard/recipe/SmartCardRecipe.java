package dev.wxdao.ccsmartcard.recipe;

import dev.wxdao.ccsmartcard.block.ModBlocks;
import dev.wxdao.ccsmartcard.item.CardColours;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;

public final class SmartCardRecipe extends CustomRecipe {
    public SmartCardRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean hasPaper = false;
        boolean hasRedstone = false;
        boolean hasCopper = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.is(Items.PAPER)) {
                if (hasPaper) {
                    return false;
                }
                hasPaper = true;
            } else if (stack.is(Tags.Items.DUSTS_REDSTONE)) {
                if (hasRedstone) {
                    return false;
                }
                hasRedstone = true;
            } else if (stack.is(Tags.Items.INGOTS_COPPER)) {
                if (hasCopper) {
                    return false;
                }
                hasCopper = true;
            } else if (CardColours.getStackDye(stack) == null) {
                return false;
            }
        }

        return hasPaper && hasRedstone && hasCopper;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = new ItemStack(ModBlocks.SMART_CARD.get());
        CardColours.ColourTracker tracker = new CardColours.ColourTracker();

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            var dye = CardColours.getStackDye(stack);
            if (dye != null) {
                tracker.addColour(dye);
            }
        }

        if (tracker.hasColour()) {
            result.set(DataComponents.DYED_COLOR, new DyedItemColor(tracker.getColour(), false));
        }
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(ModBlocks.SMART_CARD.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SMART_CARD.get();
    }
}
