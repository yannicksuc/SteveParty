package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModRecipeTypes {
    public static final RecipeType<TradingStallRecipe> TRADING_STALL_RECIPE_TYPE =
            new RecipeType<>() {
                @Override
                public String toString() {
                    return "steveparty:trading_stall";
                }
            };

    public static void register() {
        Registry.register(Registries.RECIPE_TYPE, Steveparty.id("trading_stall"), TRADING_STALL_RECIPE_TYPE);
    }
}

