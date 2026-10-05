package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.minigame.MiniGamePages;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/**
 * Like map cloning: one mini-game page + sheets of paper gives that many more linked copies of the page (same page
 * id: writing on one writes on all of them).
 */
public class MiniGamePageCopyRecipe extends SpecialCraftingRecipe {
    public MiniGamePageCopyRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    /** A page and at least one sheet of paper. */
    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return page(input) != null;
    }

    /** @return the one page, if the grid holds only it and at least one sheet of paper. */
    private static ItemStack page(CraftingRecipeInput input) {
        ItemStack page = null;
        int sheets = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.isOf(Items.PAPER)) {
                sheets++;
            } else if (MiniGamePages.isPage(stack) && page == null) {
                page = stack;
            } else {
                return null;
            }
        }
        return sheets > 0 ? page : null;
    }

    /** The page is used up by the grid too: it comes back in the result count. A page without id gets one here. */
    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack page = page(input);
        if (page == null) return ItemStack.EMPTY;
        int sheets = 0;
        for (int i = 0; i < input.getSize(); i++) {
            if (input.getStackInSlot(i).isOf(Items.PAPER)) sheets++;
        }
        // On a copy: the stack in the grid is not ours to change
        return MiniGamePages.linkedCopy(page.copyWithCount(1), sheets + 1);
    }

    @Override
    public RecipeSerializer<MiniGamePageCopyRecipe> getSerializer() {
        return ModRecipes.MINI_GAME_PAGE_COPY;
    }
}
