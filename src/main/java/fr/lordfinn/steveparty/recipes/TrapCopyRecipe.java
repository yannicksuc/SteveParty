package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.components.TrapSetupComponent;
import fr.lordfinn.steveparty.powerups.PowerUps;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Like the written books: one signed Trap + unsigned Traps (anywhere in the grid) gives as many signed copies (same
 * effect, same signer); the signed Trap stays in the grid.
 */
public class TrapCopyRecipe extends SpecialCraftingRecipe {
    public TrapCopyRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return signed(input) != null;
    }

    /** The one signed Trap, if the grid holds only it and at least one unsigned Trap. */
    private static @Nullable ItemStack signed(CraftingRecipeInput input) {
        ItemStack signed = null;
        int blanks = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!stack.isOf(PowerUps.TRAP.item())) return null;
            if (!TrapSetupComponent.isSigned(stack)) blanks++;
            else if (signed == null) signed = stack;
            else return null;
        }
        return blanks > 0 ? signed : null;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack signed = signed(input);
        if (signed == null) return ItemStack.EMPTY;
        int blanks = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (!stack.isEmpty() && !TrapSetupComponent.isSigned(stack)) blanks++;
        }
        return signed.copyWithCount(blanks);
    }

    /** The signed Trap stays in the grid. */
    @Override
    public DefaultedList<ItemStack> getRemainder(CraftingRecipeInput input) {
        DefaultedList<ItemStack> remainder = DefaultedList.ofSize(input.getSize(), ItemStack.EMPTY);
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (TrapSetupComponent.isSigned(stack)) remainder.set(i, stack.copyWithCount(1));
        }
        return remainder;
    }

    @Override
    public RecipeSerializer<TrapCopyRecipe> getSerializer() {
        return ModRecipes.TRAP_COPY;
    }
}
