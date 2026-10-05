package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModRecipes {
    /** Shaped recipe whose tag ingredient (planks, rock, plastic) becomes what the crafted sign is made of. */
    public static final MaterialShapedRecipe.Serializer MATERIAL_SHAPED = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("material_shaped"), new MaterialShapedRecipe.Serializer());
    /** A shaped recipe without its mirror image (the Polished Tiles checker). */
    public static final UnmirroredShapedRecipe.Serializer SHAPED_UNMIRRORED = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("shaped_unmirrored"), new UnmirroredShapedRecipe.Serializer());
    public static final RecipeSerializer<StencilCopyRecipe> STENCIL_COPY = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("crafting_special_stencil_copy"), new SpecialRecipeSerializer<>(StencilCopyRecipe::new));
    /** Mini-game page + paper: linked copies of the page. */
    public static final RecipeSerializer<MiniGamePageCopyRecipe> MINI_GAME_PAGE_COPY = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("crafting_special_mini_game_page_copy"), new SpecialRecipeSerializer<>(MiniGamePageCopyRecipe::new));
    /** The flag from 3 wools: the wool's colour is the flag's (mixed when the wools differ). */
    public static final FlagFromWoolRecipe.Serializer FLAG_FROM_WOOL = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("flag_from_wool"), new FlagFromWoolRecipe.Serializer());
    /** Flag + dyes: a dyed flag (mixed like leather armour). */
    public static final RecipeSerializer<FlagDyeRecipe> FLAG_DYE = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("crafting_special_flag_dye"), new SpecialRecipeSerializer<>(FlagDyeRecipe::new));

    /** Tiles change size in the grid: 4 in a square make a large one (and back), 2 small ones a standard one. */
    public static final RecipeSerializer<TileSizeRecipe> TILE_SIZE = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("crafting_special_tile_size"), new SpecialRecipeSerializer<>(TileSizeRecipe::new));

    /** A die + module items: the die with those modules added (the module items stay in the grid). One per module. */
    public static final RecipeSerializer<DiceModuleRecipe> DICE_MODULE = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("dice_module"), new DiceModuleRecipe.Serializer());
    /** Double / Triple Dice made of dice carrying faces or modules: the result keeps them. */
    public static final RecipeSerializer<MultiDiceRecipe> MULTI_DICE = Registry.register(Registries.RECIPE_SERIALIZER,
            Steveparty.id("crafting_special_multi_dice"), new SpecialRecipeSerializer<>(MultiDiceRecipe::new));

    public static void initialize() {
    }
}
