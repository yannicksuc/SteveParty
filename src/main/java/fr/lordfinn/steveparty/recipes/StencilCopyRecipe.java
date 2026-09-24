package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/** Like map cloning: one cut stencil + blank stencils gives that many more copies of the cut one. */
public class StencilCopyRecipe extends SpecialCraftingRecipe {
    public StencilCopyRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    private static boolean isStencil(ItemStack stack) {
        return stack.getItem() instanceof StencilItem;
    }

    private static boolean isBlank(ItemStack stack) {
        return StencilShape.isBlank(StencilItem.getShape(stack));
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return model(input) != null;
    }

    /** @return the one cut stencil, if the grid holds only it and at least one blank stencil. */
    private static ItemStack model(CraftingRecipeInput input) {
        ItemStack model = null;
        int blanks = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!isStencil(stack)) return null;
            if (isBlank(stack)) {
                blanks++;
            } else {
                if (model != null) return null;
                model = stack;
            }
        }
        return blanks > 0 ? model : null;
    }

    /** The cut stencil is used up by the grid too: it comes back in the result count. */
    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack model = model(input);
        if (model == null) return ItemStack.EMPTY;
        int blanks = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (!stack.isEmpty() && isBlank(stack)) blanks++;
        }
        return model.copyWithCount(blanks + 1);
    }

    @Override
    public RecipeSerializer<StencilCopyRecipe> getSerializer() {
        return ModRecipes.STENCIL_COPY;
    }
}
