package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Double and Triple Dice made of special dice (forged faces, modules): like the plain recipes (2 dice: a Double
 * Dice; 3 dice, or a Double Dice and a die: a Triple Dice), but what the dice carry is kept.
 * <ul>
 *     <li><b>faces</b>: the dice must all carry the same faces (or none): the result carries them, and each of its
 *     dice rolls them. Dice with different faces don't combine;</li>
 *     <li><b>modules</b>: the result carries the modules of all the dice (their union; for a module that stacks, the
 *     highest count among the dice).</li>
 * </ul>
 * Plain dice (no faces, no modules) go through the ordinary recipes of the recipe book; a Double / Triple Dice
 * carrying faces or modules can't be taken apart again.
 */
public class MultiDiceRecipe extends SpecialCraftingRecipe {
    public MultiDiceRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    /** At least two dice. */
    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
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

    public static @Nullable ItemStack result(CraftingRecipeInput input) {
        List<ItemStack> dice = new ArrayList<>();
        int singles = 0, doubles = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.isOf(ModItems.DEFAULT_DICE)) singles++;
            else if (stack.isOf(ModItems.DOUBLE_DICE)) doubles++;
            else return null;
            dice.add(stack);
        }
        Item result;
        if (doubles == 0 && singles == 2) result = ModItems.DOUBLE_DICE;
        else if ((doubles == 0 && singles == 3) || (doubles == 1 && singles == 1)) result = ModItems.TRIPLE_DICE;
        else return null;

        DiceFacesComponent faces = dice.getFirst().get(DiceFacesComponent.TYPE);
        Map<DiceModule, Integer> modules = new LinkedHashMap<>();
        for (ItemStack die : dice) {
            if (!Objects.equals(faces, die.get(DiceFacesComponent.TYPE))) return null; // different faces don't combine
            modules = DiceModules.union(modules, DiceModules.of(die));
        }
        // Plain dice: the ordinary recipes (shown in the recipe book) make them
        if (faces == null && modules.isEmpty()) return null;

        ItemStack stack = new ItemStack(result);
        if (faces != null) stack.set(DiceFacesComponent.TYPE, faces);
        return DiceModules.set(stack, modules);
    }

    @Override
    public RecipeSerializer<MultiDiceRecipe> getSerializer() {
        return ModRecipes.MULTI_DICE;
    }
}
