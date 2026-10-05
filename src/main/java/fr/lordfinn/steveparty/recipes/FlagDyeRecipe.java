package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.items.custom.FlagItem;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** One flag and one or more dyes, anywhere in the grid: the flag takes their colour, mixed like leather armour. */
public class FlagDyeRecipe extends SpecialCraftingRecipe {
    public FlagDyeRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    /** A flag and at least one dye. */
    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return !craft(input, null).isEmpty();
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack flag = ItemStack.EMPTY;
        List<DyeItem> dyes = new ArrayList<>();
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof FlagItem) {
                if (!flag.isEmpty()) return ItemStack.EMPTY;
                flag = stack;
            } else if (stack.getItem() instanceof DyeItem dye) {
                dyes.add(dye);
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (flag.isEmpty() || dyes.isEmpty()) return ItemStack.EMPTY;
        return FlagItem.withColor(flag.copyWithCount(1), FlagItem.mix(FlagItem.getColor(flag), dyes));
    }

    @Override
    public RecipeSerializer<FlagDyeRecipe> getSerializer() {
        return ModRecipes.FLAG_DYE;
    }
}
