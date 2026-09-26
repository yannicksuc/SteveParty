package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.items.custom.TileBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/** A tile (or advanced tile) alone in the crafting grid turns into its next size: standard, then small, then large. */
public class TileSizeRecipe extends SpecialCraftingRecipe {
    public TileSizeRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    /** @return the only stack in the grid, if it is a tile. */
    private static ItemStack tile(CraftingRecipeInput input) {
        ItemStack tile = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (tile != null || !(stack.getItem() instanceof TileBlockItem)) return null;
            tile = stack;
        }
        return tile;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return tile(input) != null;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack tile = tile(input);
        if (tile == null) return ItemStack.EMPTY;
        // Keeps everything else (cartridges held by a picked tile...)
        return TileSize.with(tile.copyWithCount(1), TileSize.of(tile).next());
    }

    @Override
    public RecipeSerializer<TileSizeRecipe> getSerializer() {
        return ModRecipes.TILE_SIZE;
    }
}
