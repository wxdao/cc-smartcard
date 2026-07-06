package dev.wxdao.ccsmartcard.recipe;

import dev.wxdao.ccsmartcard.CCSmartCard;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CCSmartCard.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SmartCardRecipe>> SMART_CARD =
            RECIPE_SERIALIZERS.register("smart_card", () -> new SimpleCraftingRecipeSerializer<>(SmartCardRecipe::new));

    private ModRecipes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
