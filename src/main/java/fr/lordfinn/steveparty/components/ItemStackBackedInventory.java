package fr.lordfinn.steveparty.components;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;

/**
 * Working copy of an {@link InventoryComponent} stored on an item stack (the "holder").
 * Every {@link #markDirty()} writes an immutable snapshot back to the holder.
 * <p>
 * The holder is tracked by identity: as soon as it leaves the player's inventory (dropped, split, destroyed...)
 * the inventory stops being usable, which prevents writing into / reading from a stale copy.
 */
public class ItemStackBackedInventory extends SimpleInventory {
    private final ItemStack holder;
    private final Item holderItem;

    public ItemStackBackedInventory(ItemStack holder, int size) {
        super(size);
        this.holder = holder;
        this.holderItem = holder.getItem();
        InventoryComponent component = holder.get(INVENTORY_COMPONENT);
        if (component != null) {
            for (int i = 0; i < Math.min(size, component.size()); i++) {
                this.heldStacks.set(i, component.getStack(i));
            }
        }
    }

    public ItemStack getHolder() {
        return holder;
    }

    /** @return true while the holder stack is still a valid, live stack. */
    public boolean isHolderValid() {
        return !holder.isEmpty() && holder.getItem() == holderItem;
    }

    /** @return true if the holder is still valid and owned by {@code player} (inventory or cursor). */
    public boolean isHeldBy(PlayerEntity player) {
        if (!isHolderValid()) return false;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i) == holder) return true;
        }
        return player.currentScreenHandler != null && player.currentScreenHandler.getCursorStack() == holder;
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return isHeldBy(player);
    }

    @Override
    public void markDirty() {
        super.markDirty();
        writeBack();
    }

    public void writeBack() {
        if (isHolderValid()) {
            InventoryComponent.writeToStack(holder, this);
        }
    }
}
