package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_CONTAINERS;
import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_DIMENSION;
import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_POS;

/**
 * The containers of an Inventory Cartridge: a list, in order, of at most {@link #MAX} (dimension and position each).
 * A board space takes the cartridge's items from them in this order (the first that has the item) and puts what it
 * takes from a player in the first that has room; a Party Controller pays its gains from them in this order.
 * <p>
 * A cartridge saved with one container (the former single field, {@code inventory-pos}) reads as a list of that one,
 * in the dimension saved with it or, without, in the world of whatever reads it; it is written as a list the next
 * time it changes.
 */
public final class CartridgeContainers {
    /** The most containers a cartridge holds. */
    public static final int MAX = 8;
    /** The mod's own blocks, never taken as a container even when they hold items (board spaces, controllers...). */
    private static final TagKey<Block> BOARD = TagKey.of(RegistryKeys.BLOCK, Steveparty.id("board_infrastructure"));

    /** What a click on a container did. */
    public enum Toggle { ADDED, REMOVED, FULL }

    private CartridgeContainers() {
    }

    /** Its containers, in order (a legacy single one in {@code fallback} when saved without dimension). */
    public static List<GlobalPos> of(ItemStack stack, RegistryKey<World> fallback) {
        List<GlobalPos> list = stack.get(INVENTORY_CONTAINERS);
        if (list != null) return list;
        BlockPos legacy = stack.get(INVENTORY_POS);
        if (legacy == null) return List.of();
        return List.of(GlobalPos.create(stack.getOrDefault(INVENTORY_DIMENSION, fallback), legacy));
    }

    /** Its containers in {@code world}'s dimension, in order. */
    public static List<BlockPos> in(ItemStack stack, World world) {
        List<BlockPos> positions = new ArrayList<>();
        for (GlobalPos pos : of(stack, world.getRegistryKey())) {
            if (pos.dimension().equals(world.getRegistryKey())) positions.add(pos.pos());
        }
        return positions;
    }

    /** Whether {@code stack} is a cartridge linked to containers (Inventory, Trichaudron: {@link ContainerCartridge}). */
    public static boolean linksContainers(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ContainerCartridge;
    }

    public static boolean isEmpty(ItemStack stack) {
        return !stack.contains(INVENTORY_CONTAINERS) && !stack.contains(INVENTORY_POS);
    }

    /** Writes the list (no twice the same, at most {@link #MAX}); the legacy single field goes. */
    public static void set(ItemStack stack, List<GlobalPos> containers) {
        List<GlobalPos> list = new ArrayList<>();
        for (GlobalPos pos : containers) if (!list.contains(pos) && list.size() < MAX) list.add(pos);
        stack.remove(INVENTORY_POS);
        stack.remove(INVENTORY_DIMENSION);
        if (list.isEmpty()) stack.remove(INVENTORY_CONTAINERS);
        else stack.set(INVENTORY_CONTAINERS, List.copyOf(list));
    }

    /** Adds the container at the end of the list, or removes it if it is in; a full list takes no more. */
    public static Toggle toggle(ItemStack stack, World world, BlockPos pos) {
        List<GlobalPos> list = new ArrayList<>(of(stack, world.getRegistryKey()));
        GlobalPos here = GlobalPos.create(world.getRegistryKey(), pos.toImmutable());
        if (list.remove(here)) {
            set(stack, list);
            return Toggle.REMOVED;
        }
        if (list.size() >= MAX) return Toggle.FULL;
        list.add(here);
        set(stack, list);
        return Toggle.ADDED;
    }

    /** Removes the container at {@code index}. @return false if there is none */
    public static boolean remove(ItemStack stack, int index, RegistryKey<World> fallback) {
        List<GlobalPos> list = new ArrayList<>(of(stack, fallback));
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        set(stack, list);
        return true;
    }

    /** Moves the container at {@code index} one place toward the start ({@code up}) or the end. @return false if it can't */
    public static boolean move(ItemStack stack, int index, boolean up, RegistryKey<World> fallback) {
        List<GlobalPos> list = new ArrayList<>(of(stack, fallback));
        int other = up ? index - 1 : index + 1;
        if (index < 0 || index >= list.size() || other < 0 || other >= list.size()) return false;
        Collections.swap(list, index, other);
        set(stack, list);
        return true;
    }

    // ------------------------------------------------------------------ what can be a container

    /**
     * Whether the block at {@code pos} can be one of the cartridge's containers: a block holding items (chest,
     * trapped chest, barrel, shulker box, hopper, dropper, dispenser, furnaces, brewing stand, crafter, other mods'
     * containers...), except the mod's board blocks (board spaces, routers, hop switch, looting box, piggy bank,
     * controllers, dice forge, shop blocks, pipes) whose items are their own.
     */
    public static boolean accepts(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof Inventory)) return false;
        if (blockEntity instanceof CartridgeContainerBlockEntity || blockEntity instanceof PipeBlockEntity) return false;
        return !world.getBlockState(pos).isIn(BOARD);
    }

    // ------------------------------------------------------------------ using them

    /**
     * The containers of the cartridge that are there now, in order, in {@code world}: loaded (an unloaded one is
     * skipped, never loaded for this), still a container. The two halves of a double chest count once.
     */
    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): an unloaded container is skipped, not loaded
    public static List<Inventory> available(ItemStack stack, World world) {
        List<Inventory> inventories = new ArrayList<>();
        List<BlockPos> seen = new ArrayList<>();
        for (BlockPos pos : in(stack, world)) {
            if (!world.isChunkLoaded(pos) || seen.contains(pos)) continue;
            if (!(world.getBlockEntity(pos) instanceof Inventory inventory)) continue;
            seen.add(pos);
            BlockPos other = otherHalf(world, pos);
            if (other != null) seen.add(other);
            inventories.add(inventory);
        }
        return inventories;
    }

    /** The other half of a double chest, null for anything else. */
    public static @Nullable BlockPos otherHalf(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || !state.contains(ChestBlock.CHEST_TYPE)
                || state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) return null;
        return pos.offset(ChestBlock.getFacing(state));
    }

    /**
     * Puts as much of {@code stack} as fits in the containers, in order: the first fills up (merging, then its empty
     * slots) before the next one is used. {@code stack} is decremented by what went in.
     *
     * @return how many went in
     */
    public static int insertInOrder(ItemStack stack, List<Inventory> inventories) {
        int inserted = 0;
        for (Inventory inventory : inventories) {
            if (stack.isEmpty()) break;
            inserted += InventoryInteractorTileBehavior.insertIntoInventory(stack, inventory);
        }
        return inserted;
    }
}
