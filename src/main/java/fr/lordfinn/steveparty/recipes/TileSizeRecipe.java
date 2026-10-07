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
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Tiles change size in the crafting grid, the material kept (the stonecutter cuts a tile into 2 small ones):
 * <ul>
 *     <li>4 standard tiles of one kind in a 2x2 square: 1 large (2x2) tile;</li>
 *     <li>a large tile alone: its 4 standard tiles back;</li>
 *     <li>2 small tiles of one kind: 1 standard tile.</li>
 * </ul>
 * Merged, the first tile (in the grid's order) gives the result its cartridges and its look; each other one may hold
 * one cartridge, given back in its slot (more, or a look of its own: no craft, they would be lost). A large tile split
 * back gives 4 empty tiles and its cartridge back (more than one, or a look: no craft).
 */
public class TileSizeRecipe extends SpecialCraftingRecipe {
    public TileSizeRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    /** A large tile alone is enough. */
    @Override
    public boolean fits(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return result(input) != null;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        Plan plan = plan(input);
        return plan == null ? ItemStack.EMPTY : plan.result();
    }

    @Override
    public DefaultedList<ItemStack> getRemainder(CraftingRecipeInput input) {
        Plan plan = plan(input);
        return plan == null ? super.getRemainder(input) : plan.remainders();
    }

    private record Plan(ItemStack result, DefaultedList<ItemStack> remainders) {
    }

    private static @Nullable ItemStack result(CraftingRecipeInput input) {
        Plan plan = plan(input);
        return plan == null ? null : plan.result();
    }

    private static @Nullable Plan plan(CraftingRecipeInput input) {
        Item item = null;
        TileSize size = null;
        int count = 0;
        int first = -1;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof TileBlockItem)) return null;
            // One kind of tile, one size
            if (item != null && (stack.getItem() != item || TileSize.of(stack) != size)) return null;
            item = stack.getItem();
            size = TileSize.of(stack);
            if (first < 0) first = i;
            count++;
        }
        if (item == null) return null;
        DefaultedList<ItemStack> remainders = DefaultedList.ofSize(input.getSize(), ItemStack.EMPTY);
        ItemStack result;
        // The input is trimmed to its items: a 2x2 square is a 2x2 input
        if (size == TileSize.STANDARD && count == 4 && input.getWidth() == 2 && input.getHeight() == 2) {
            result = TileSize.with(input.getStackInSlot(first).copyWithCount(1), TileSize.LARGE);
        } else if (size == TileSize.SMALL && count == 2) {
            result = TileSize.with(input.getStackInSlot(first).copyWithCount(1), TileSize.STANDARD);
        } else if (size == TileSize.LARGE && count == 1) {
            ItemStack back = given(input.getStackInSlot(first));
            if (back == null) return null;
            remainders.set(first, back);
            return new Plan(new ItemStack(item, 4), remainders);
        } else {
            return null;
        }
        // Merged: the other tiles' cartridge back in their slot
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty() || i == first) continue;
            ItemStack back = given(stack);
            if (back == null) return null;
            remainders.set(i, back);
        }
        return new Plan(result, remainders);
    }

    /** The one cartridge given back from a tile used up (empty: none); null if it holds more, or a look of its own. */
    private static @Nullable ItemStack given(ItemStack tile) {
        if (TileContents.ownStamp(tile) != null) return null;
        List<TileContents.Slot> cartridges = TileContents.cartridges(tile);
        if (cartridges.size() > 1) return null;
        return cartridges.isEmpty() ? ItemStack.EMPTY : cartridges.getFirst().cartridge().copy();
    }

    @Override
    public RecipeSerializer<TileSizeRecipe> getSerializer() {
        return ModRecipes.TILE_SIZE;
    }
}
