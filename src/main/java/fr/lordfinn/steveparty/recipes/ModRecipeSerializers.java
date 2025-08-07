package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class ModRecipeSerializers {
    // Déclare ton serializer comme une constante
    public static final RecipeSerializer<TradingStallRecipe> TRADING_STALL_RECIPE_SERIALIZER = new TradingStallRecipe.Serializer();

    public static void register() {
        Registry.register(Registries.RECIPE_SERIALIZER, Steveparty.id("trading_stall"), TRADING_STALL_RECIPE_SERIALIZER);
    }
}
