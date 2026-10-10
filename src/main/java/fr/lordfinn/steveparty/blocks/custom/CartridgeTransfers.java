package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.utils.InventoryChain;
import org.jetbrains.annotations.Nullable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static fr.lordfinn.steveparty.components.ModComponents.*;

/**
 * Item transfers between the containers linked to an inventory cartridge (in their order) and a player, like the inventory board
 * spaces: the items of the cartridge are given to the player from the container, items marked negative are taken
 * from the player into the container. Selection of the cartridge: all items, next item in turn, or a random one.
 * <p>
 * "All items" is all-or-nothing: if the player cannot pay every negative item, or the container misses a positive
 * one, nothing moves (a star bought for 20 coins, never half of it).
 */
public final class CartridgeTransfers {
    private CartridgeTransfers() {}

    /**
     * The containers of an inventory cartridge that are there now (loaded, still containers), end to end in their
     * order ({@link InventoryChain}): taking walks them in order, giving fills the first one with room first. Null
     * for none.
     */
    public static @Nullable Inventory getLinkedInventory(World world, ItemStack cartridge) {
        return getLinkedInventory(world, cartridge, null);
    }

    /**
     * The same for the cartridge of the board space at {@code space}: without a container of its own, the bank of the
     * party running on its board (see {@link CartridgeContainers#availableFor}).
     */
    public static @Nullable Inventory getLinkedInventory(World world, ItemStack cartridge, @Nullable BlockPos space) {
        if (!(cartridge.getItem() instanceof InventoryCartridgeItem)) return null;
        List<Inventory> available = CartridgeContainers.availableFor(cartridge, world, space);
        return available.isEmpty() ? null : new InventoryChain(available);
    }

    /**
     * Applies the cartridge to the player.
     *
     * @param cycleIndex  current index for the "next item in turn" selection
     * @param setCycleIndex receives the next index
     * @return true if the transfer happened (something moved; for "all items", everything)
     */
    public static boolean apply(World world, ItemStack cartridge, PlayerEntity player, IntSupplier cycleIndex, IntConsumer setCycleIndex) {
        Inventory linked = getLinkedInventory(world, cartridge);
        InventoryComponent content = cartridge.getOrDefault(INVENTORY_COMPONENT, null);
        if (linked == null || content == null) return false;
        List<ItemStack> items = content.getItems().stream().filter(stack -> !stack.isEmpty()).toList();
        if (items.isEmpty()) return false;
        return switch (InventoryCartridgeItem.getSelectionState(cartridge)) {
            case 1 -> {
                for (ItemStack stack : items) {
                    Inventory source = isNegative(stack) ? player.getInventory() : linked;
                    if (countMatching(stack, source) < stack.getCount()) yield false;
                }
                boolean moved = false;
                for (ItemStack stack : items) moved |= transfer(stack, linked, player) > 0;
                yield moved;
            }
            case 2 -> {
                int index = Math.floorMod(cycleIndex.getAsInt(), items.size());
                setCycleIndex.accept((index + 1) % items.size());
                yield transfer(items.get(index), linked, player) > 0;
            }
            default -> transfer(items.get(world.getRandom().nextInt(items.size())), linked, player) > 0;
        };
    }

    private static boolean isNegative(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(IS_NEGATIVE));
    }

    /** Number of items matching the cartridge item (item + components, the negative mark excepted) in {@code inventory}. */
    public static int countMatching(ItemStack template, Inventory inventory) {
        ItemStack pattern = template.copyWithCount(1);
        pattern.remove(IS_NEGATIVE);
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, pattern)) count += stack.getCount();
        }
        return count;
    }

    private static int transfer(ItemStack stack, Inventory linked, PlayerEntity player) {
        if (isNegative(stack)) {
            // What does not fit in the container stays with the player
            return InventoryInteractorTileBehavior.extractMatching(stack, player.getInventory(),
                    toMove -> InventoryInteractorTileBehavior.insertLinked(toMove, linked));
        }
        // What does not fit in the player's inventory is dropped at their feet
        int moved = InventoryInteractorTileBehavior.extractMatching(stack, linked, toMove -> {
            int count = toMove.getCount();
            player.getInventory().offerOrDrop(toMove);
            return count;
        });
        player.getInventory().markDirty();
        return moved;
    }
}
