package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
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
 * Item transfers between the container linked to an inventory cartridge and a player, like the inventory board
 * spaces: the items of the cartridge are given to the player from the container, items marked negative are taken
 * from the player into the container. Selection of the cartridge: all items, next item in turn, or a random one.
 * <p>
 * "All items" is all-or-nothing: if the player cannot pay every negative item, or the container misses a positive
 * one, nothing moves (a star bought for 20 coins, never half of it).
 */
public final class CartridgeTransfers {
    private CartridgeTransfers() {}

    /** @return the container linked to the cartridge, if it is an inventory cartridge linked to a loaded container */
    public static Inventory getLinkedInventory(World world, ItemStack cartridge) {
        if (!(cartridge.getItem() instanceof InventoryCartridgeItem)) return null;
        if (!(cartridge.get(INVENTORY_POS) instanceof BlockPos linkedPos) || !world.isChunkLoaded(linkedPos)) return null;
        return world.getBlockEntity(linkedPos) instanceof Inventory inventory ? inventory : null;
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
                    toMove -> InventoryInteractorTileBehavior.insertIntoInventory(toMove, linked));
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
