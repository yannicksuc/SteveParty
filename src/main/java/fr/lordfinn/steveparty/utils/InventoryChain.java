package fr.lordfinn.steveparty.utils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Several inventories read as one, end to end in their order: the slots of the first, then those of the second...
 * What walks its slots in order (counting, taking) uses the first inventories first.
 */
public final class InventoryChain implements Inventory {
    private final List<Inventory> inventories;

    public InventoryChain(List<Inventory> inventories) {
        this.inventories = List.copyOf(inventories);
    }

    public List<Inventory> inventories() {
        return inventories;
    }

    @Override
    public int size() {
        int size = 0;
        for (Inventory inventory : inventories) size += inventory.size();
        return size;
    }

    @Override
    public boolean isEmpty() {
        for (Inventory inventory : inventories) if (!inventory.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        for (Inventory inventory : inventories) {
            if (slot < inventory.size()) return inventory.getStack(slot);
            slot -= inventory.size();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        for (Inventory inventory : inventories) {
            if (slot < inventory.size()) return inventory.removeStack(slot, amount);
            slot -= inventory.size();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot) {
        for (Inventory inventory : inventories) {
            if (slot < inventory.size()) return inventory.removeStack(slot);
            slot -= inventory.size();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        for (Inventory inventory : inventories) {
            if (slot < inventory.size()) {
                inventory.setStack(slot, stack);
                return;
            }
            slot -= inventory.size();
        }
    }

    @Override
    public void markDirty() {
        for (Inventory inventory : inventories) inventory.markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return false;
    }

    @Override
    public void clear() {
        for (Inventory inventory : inventories) inventory.clear();
    }
}
