package fr.lordfinn.steveparty.recipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.DefaultDiceItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A die + module items: the same die (its faces and the modules it already carries kept) with those modules added.
 * Any die: a plain one, a forged one, a Double or Triple Dice.
 * <ul>
 *     <li>each module item of the grid adds one of its module (several of a module that stacks add as many);</li>
 *     <li>the module items are not consumed: they stay in the grid, like in the Dice Forge;</li>
 *     <li>no craft if the die would carry more of a module than it may: a module that doesn't stack the die already
 *     carries (or put twice in the grid), a module that stacks beyond its maximum.</li>
 * </ul>
 * There is one such recipe per module ({@code "type": "steveparty:dice_module", "module": "<id>"}), so that the
 * recipe book shows each one (a die and the module item: the die carrying it). A grid holding several modules is
 * crafted by the recipe of the first of them (in the order of {@link DiceModules}): one recipe matches a grid.
 */
public class DiceModuleRecipe implements CraftingRecipe {
    private final DiceModule module;

    public DiceModuleRecipe(DiceModule module) {
        this.module = module;
    }

    public DiceModule module() {
        return module;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return firstModule(input) == module && result(input) != null;
    }

    /** A die and at least one module item. */
    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack result = result(input);
        return result == null ? ItemStack.EMPTY : result;
    }

    /** The first module (in registration order) a module item of the grid stands for, null if there is none. */
    private static @Nullable DiceModule firstModule(CraftingRecipeInput input) {
        DiceModule first = null;
        int firstIndex = Integer.MAX_VALUE;
        List<DiceModule> order = List.copyOf(DiceModules.all());
        for (int i = 0; i < input.getSize(); i++) {
            DiceModule module = DiceModules.fromItem(input.getStackInSlot(i));
            if (module != null && order.indexOf(module) < firstIndex) {
                first = module;
                firstIndex = order.indexOf(module);
            }
        }
        return first;
    }

    /** The die of the grid with the modules of the grid added, null if the grid is not this craft. */
    public static @Nullable ItemStack result(CraftingRecipeInput input) {
        ItemStack die = ItemStack.EMPTY;
        Map<DiceModule, Integer> added = new LinkedHashMap<>();
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            DiceModule module = DiceModules.fromItem(stack);
            if (module != null) {
                added.merge(module, 1, Integer::sum);
            } else if (stack.getItem() instanceof DefaultDiceItem && die.isEmpty()) {
                die = stack;
            } else {
                return null;
            }
        }
        if (die.isEmpty() || added.isEmpty()) return null;
        Map<DiceModule, Integer> modules = DiceModules.of(die);
        for (Map.Entry<DiceModule, Integer> entry : added.entrySet()) {
            int count = modules.getOrDefault(entry.getKey(), 0) + entry.getValue();
            if (count > entry.getKey().maxCount()) return null;
            modules.put(entry.getKey(), count);
        }
        return DiceModules.set(die.copyWithCount(1), modules);
    }

    /** The module items stay in the grid. */
    @Override
    public DefaultedList<ItemStack> getRemainder(CraftingRecipeInput input) {
        DefaultedList<ItemStack> remainders = DefaultedList.ofSize(input.getSize(), ItemStack.EMPTY);
        for (int i = 0; i < remainders.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (DiceModules.isModuleItem(stack)) remainders.set(i, stack.copyWithCount(1));
        }
        return remainders;
    }

    // ------------------------------------------------------------------ recipe book

    private static Ingredient anyDie() {
        return Ingredient.ofItems(ModItems.DEFAULT_DICE, ModItems.DOUBLE_DICE, ModItems.TRIPLE_DICE);
    }

    /** A die and the module item. */
    @Override
    public DefaultedList<Ingredient> getIngredients() {
        return DefaultedList.copyOf(Ingredient.EMPTY, anyDie(), Ingredient.ofItems(module.item()));
    }

    /** The plain die carrying the module. */
    @Override
    public ItemStack getResult(RegistryWrapper.WrapperLookup registries) {
        return DiceModules.set(new ItemStack(ModItems.DEFAULT_DICE), Map.of(module, 1));
    }

    @Override
    public CraftingRecipeCategory getCategory() {
        return CraftingRecipeCategory.EQUIPMENT;
    }

    @Override
    public String getGroup() {
        return "dice_module";
    }

    @Override
    public RecipeSerializer<DiceModuleRecipe> getSerializer() {
        return ModRecipes.DICE_MODULE;
    }

    public static class Serializer implements RecipeSerializer<DiceModuleRecipe> {
        private static final Codec<DiceModule> MODULE = Codec.STRING.comapFlatMap(id -> {
            DiceModule module = DiceModules.get(id);
            return module == null ? DataResult.error(() -> "Unknown dice module: " + id) : DataResult.success(module);
        }, DiceModule::id);
        private static final MapCodec<DiceModuleRecipe> CODEC = MODULE.fieldOf("module").xmap(DiceModuleRecipe::new, DiceModuleRecipe::module);
        private static final PacketCodec<RegistryByteBuf, DiceModuleRecipe> PACKET_CODEC = PacketCodecs.STRING
                .<RegistryByteBuf>cast().xmap(id -> new DiceModuleRecipe(DiceModules.get(id)), recipe -> recipe.module().id());

        @Override
        public MapCodec<DiceModuleRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, DiceModuleRecipe> packetCodec() {
            return PACKET_CODEC;
        }
    }
}
