package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.items.custom.TileBlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Tiles change size in the crafting grid, the material kept (the stonecutter cuts a tile into 2 small ones):
 * <ul>
 *     <li>4 standard tiles of one kind in a 2x2 square: 1 large (2x2) tile;</li>
 *     <li>a large tile alone: its 4 standard tiles back;</li>
 *     <li>2 small tiles of one kind: 1 standard tile.</li>
 * </ul>
 */
public class TileSizeRecipe extends SpecialCraftingRecipe {
    public TileSizeRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return result(input) != null;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack result = result(input);
        return result == null ? ItemStack.EMPTY : result;
    }

    private static @Nullable ItemStack result(CraftingRecipeInput input) {
        Item item = null;
        TileSize size = null;
        int count = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof TileBlockItem)) return null;
            // A tile holding cartridges or a look (taken with Silk Touch) is not cut up: they would be lost
            if (TileContents.holdsContents(stack)) return null;
            // One kind of tile, one size
            if (item != null && (stack.getItem() != item || TileSize.of(stack) != size)) return null;
            item = stack.getItem();
            size = TileSize.of(stack);
            count++;
        }
        if (item == null) return null;
        // The input is trimmed to its items: a 2x2 square is a 2x2 input
        if (size == TileSize.STANDARD && count == 4 && input.getWidth() == 2 && input.getHeight() == 2) {
            return TileSize.with(new ItemStack(item), TileSize.LARGE);
        }
        if (size == TileSize.LARGE && count == 1) return new ItemStack(item, 4);
        if (size == TileSize.SMALL && count == 2) return new ItemStack(item);
        return null;
    }

    @Override
    public RecipeSerializer<TileSizeRecipe> getSerializer() {
        return ModRecipes.TILE_SIZE;
    }
}
