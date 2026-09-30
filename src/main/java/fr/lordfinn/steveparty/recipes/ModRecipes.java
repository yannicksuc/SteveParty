package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModRecipes {
    /** Shaped recipe whose tag ingredient (planks, rock, plastic) becomes what the crafted sign is made of. */
    public static final MaterialShapedRecipe.Serializer MATERIAL_SHAPED = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("material_shaped"), new MaterialShapedRecipe.Serializer());
    public static final RecipeSerializer<StencilCopyRecipe> STENCIL_COPY = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("crafting_special_stencil_copy"), new SpecialCraftingRecipe.SpecialRecipeSerializer<>(StencilCopyRecipe::new));
    /** The flag from 3 wools: the wool's colour is the flag's (mixed when the wools differ). */
    public static final FlagFromWoolRecipe.Serializer FLAG_FROM_WOOL = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("flag_from_wool"), new FlagFromWoolRecipe.Serializer());
    /** Flag + dyes: a dyed flag (mixed like leather armour). */
    public static final RecipeSerializer<FlagDyeRecipe> FLAG_DYE = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("crafting_special_flag_dye"), new SpecialCraftingRecipe.SpecialRecipeSerializer<>(FlagDyeRecipe::new));

    /** Tiles change size in the grid: 4 in a square make a large one (and back), 2 small ones a standard one. */
    public static final RecipeSerializer<TileSizeRecipe> TILE_SIZE = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("crafting_special_tile_size"), new SpecialCraftingRecipe.SpecialRecipeSerializer<>(TileSizeRecipe::new));

    public static void initialize() {
    }
}
