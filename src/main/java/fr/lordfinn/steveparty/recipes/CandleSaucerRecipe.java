package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlock;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/**
 * The Frousseux candle holder and its Candle Saucer, shapeless, both ways, nothing lost:
 * <ul>
 *     <li>a candle holder (not on a saucer) + a Candle Saucer: the candle holder on its saucer;</li>
 *     <li>a candle holder on its saucer alone: the candle holder off it, the saucer left in the grid.</li>
 * </ul>
 * The Frousseux it keeps (its block entity data, its name...) stays on the item.
 */
public class CandleSaucerRecipe extends SpecialCraftingRecipe {
    public CandleSaucerRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 1;
    }

    /** The candle holder in the grid, if the grid is one of the two ways; else null. */
    private static ItemStack candle(CraftingRecipeInput input) {
        ItemStack candle = null;
        int saucers = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.isOf(ModItems.CANDLE_SAUCER)) saucers++;
            else if (stack.isOf(ModBlocks.FROUSSEUX_CANDLE_HOLDER.asItem()) && candle == null) candle = stack;
            else return null;
        }
        if (candle == null) return null;
        boolean on = FrousseuxCandleHolderBlock.isOnSaucer(candle);
        return (on && saucers == 0) || (!on && saucers == 1) ? candle : null;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return candle(input) != null;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack candle = candle(input);
        if (candle == null) return ItemStack.EMPTY;
        ItemStack result = candle.copyWithCount(1);
        FrousseuxCandleHolderBlock.setOnSaucer(result, !FrousseuxCandleHolderBlock.isOnSaucer(candle));
        return result;
    }

    /** Off its saucer: the saucer stays in the grid, where the candle holder was. */
    @Override
    public DefaultedList<ItemStack> getRemainder(CraftingRecipeInput input) {
        DefaultedList<ItemStack> remainder = super.getRemainder(input);
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isOf(ModBlocks.FROUSSEUX_CANDLE_HOLDER.asItem()) && FrousseuxCandleHolderBlock.isOnSaucer(stack)) {
                remainder.set(i, new ItemStack(ModItems.CANDLE_SAUCER));
            }
        }
        return remainder;
    }

    @Override
    public RecipeSerializer<CandleSaucerRecipe> getSerializer() {
        return ModRecipes.CANDLE_SAUCER;
    }
}
