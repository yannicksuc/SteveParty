package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Cartridges put in tile items at the crafting grid (2x2 or crafting table), shapeless:
 * <ul>
 *     <li>a Tile + a cartridge: the Tile holding that cartridge; the cartridge it held comes back (left in the grid,
 *     or given to the player when the Tile slot still holds more Tiles);</li>
 *     <li>a Tile or an Advanced Tile alone, holding a single cartridge (not a large one: it splits, see
 *     TileSizeRecipe): emptied, the cartridge back in its slot;</li>
 *     <li>an Advanced Tile + Tiles and / or loose cartridges: their cartridges fill the free slots of the Advanced Tile
 *     in the order {@link #FILL_ORDER} (0, then 15 down to 1), in the order of the grid; the Tiles are used up. More
 *     cartridges than free slots: no craft.</li>
 * </ul>
 * The cartridges come in without links (positions on the board they came from: they would lead nowhere here).
 */
public class TileCartridgeRecipe extends SpecialCraftingRecipe {
    /** The order the free slots of an Advanced Tile are filled: 0 (no power), then 15 down to 1. */
    public static final int[] FILL_ORDER = {0, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    private static final int ADVANCED_SLOTS = 16;

    public TileCartridgeRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    /** What the grid gives: the result, and what stays in each slot of the input. */
    private record Plan(ItemStack result, DefaultedList<ItemStack> remainders) {
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return plan(input) != null;
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

    private static boolean isTile(ItemStack stack) {
        return stack.isOf(ModBlocks.TILE.asItem());
    }

    private static boolean isAdvancedTile(ItemStack stack) {
        return stack.isOf(ModBlocks.ADVANCED_TILE.asItem());
    }

    private static @Nullable Plan plan(CraftingRecipeInput input) {
        int advanced = -1;
        List<Integer> others = new ArrayList<>();
        int tiles = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (isAdvancedTile(stack)) {
                if (advanced >= 0) return null;
                advanced = i;
            } else if (isTile(stack)) {
                tiles++;
                others.add(i);
            } else if (stack.getItem() instanceof CartridgeItem) {
                others.add(i);
            } else {
                return null;
            }
        }
        DefaultedList<ItemStack> remainders = DefaultedList.ofSize(input.getSize(), ItemStack.EMPTY);
        if (others.isEmpty() || (advanced < 0 && others.size() == 1)) {
            // A tile alone holding one cartridge: emptied, the cartridge back (a large one splits instead: TileSizeRecipe)
            int alone = advanced >= 0 ? advanced : others.getFirst();
            ItemStack tile = input.getStackInSlot(alone);
            if (!isTile(tile) && !isAdvancedTile(tile)) return null;
            if (TileSize.of(tile) == TileSize.LARGE) return null;
            List<TileContents.Slot> held = TileContents.cartridges(tile);
            if (held.size() != 1) return null;
            ItemStack emptied = tile.copyWithCount(1);
            emptied.remove(DataComponentTypes.CONTAINER);
            remainders.set(alone, held.getFirst().cartridge().copy());
            return new Plan(emptied, remainders);
        }
        if (advanced >= 0) {
            List<ItemStack> cartridges = new ArrayList<>();
            for (int i : others) {
                ItemStack stack = input.getStackInSlot(i);
                if (isTile(stack)) {
                    List<TileContents.Slot> held = TileContents.cartridges(stack);
                    if (held.isEmpty()) return null; // an empty Tile adds nothing
                    for (TileContents.Slot slot : held) cartridges.add(withoutLinks(slot.cartridge()));
                } else {
                    cartridges.add(withoutLinks(stack));
                }
            }
            ItemStack result = fill(input.getStackInSlot(advanced), cartridges);
            return result == null ? null : new Plan(result, remainders);
        }
        // A Tile and a cartridge
        if (tiles != 1 || others.size() != 2) return null;
        int tileSlot = isTile(input.getStackInSlot(others.get(0))) ? others.get(0) : others.get(1);
        int cartridgeSlot = tileSlot == others.get(0) ? others.get(1) : others.get(0);
        ItemStack tile = input.getStackInSlot(tileSlot);
        ItemStack cartridge = withoutLinks(input.getStackInSlot(cartridgeSlot));
        List<TileContents.Slot> held = TileContents.cartridges(tile);
        ItemStack old = held.isEmpty() ? ItemStack.EMPTY : held.getFirst().cartridge().copy();
        if (!old.isEmpty() && ItemStack.areItemsAndComponentsEqual(old, cartridge)) return null; // nothing would change
        ItemStack result = tile.copyWithCount(1);
        result.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(cartridge)));
        remainders.set(tileSlot, old);
        return new Plan(result, remainders);
    }

    /** One of {@code cartridge}, without its links. */
    public static ItemStack withoutLinks(ItemStack cartridge) {
        ItemStack copy = cartridge.copyWithCount(1);
        copy.remove(ModComponents.DESTINATIONS_COMPONENT);
        return copy;
    }

    /**
     * A copy of the Advanced Tile item {@code advanced} (one) with {@code cartridges} in its free slots, in
     * {@link #FILL_ORDER}; null if they don't all fit.
     */
    public static @Nullable ItemStack fill(ItemStack advanced, List<ItemStack> cartridges) {
        DefaultedList<ItemStack> slots = DefaultedList.ofSize(ADVANCED_SLOTS, ItemStack.EMPTY);
        ContainerComponent container = advanced.get(DataComponentTypes.CONTAINER);
        if (container != null) container.copyTo(slots);
        int next = 0;
        for (ItemStack cartridge : cartridges) {
            while (next < FILL_ORDER.length && !slots.get(FILL_ORDER[next]).isEmpty()) next++;
            if (next >= FILL_ORDER.length) return null;
            slots.set(FILL_ORDER[next], cartridge.copyWithCount(1));
        }
        ItemStack result = advanced.copyWithCount(1);
        result.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(slots));
        return result;
    }

    /** The first free slot of the Advanced Tile item {@code advanced}, in {@link #FILL_ORDER}; -1 if it is full. */
    public static int firstFreeSlot(ItemStack advanced) {
        DefaultedList<ItemStack> slots = DefaultedList.ofSize(ADVANCED_SLOTS, ItemStack.EMPTY);
        ContainerComponent container = advanced.get(DataComponentTypes.CONTAINER);
        if (container != null) container.copyTo(slots);
        for (int slot : FILL_ORDER) if (slots.get(slot).isEmpty()) return slot;
        return -1;
    }

    @Override
    public RecipeSerializer<TileCartridgeRecipe> getSerializer() {
        return ModRecipes.TILE_CARTRIDGE;
    }
}
