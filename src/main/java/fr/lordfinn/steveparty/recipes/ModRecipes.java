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

    public static void initialize() {
    }
}
