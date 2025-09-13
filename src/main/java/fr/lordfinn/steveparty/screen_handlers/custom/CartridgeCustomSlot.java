package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

public class CartridgeCustomSlot extends Slot {
    public CartridgeCustomSlot(Inventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        if (this.hasStack())
            return false;
        if (stack.isEmpty()) {
            return true;
        }
        return isAllowedItem(stack) && stack.getCount() == 1;
    }

    protected boolean isAllowedItem(ItemStack originalStack) {
        return originalStack.getItem() instanceof CartridgeItem;
    }

    @Override
    public int getMaxItemCount(ItemStack stack) {
        return 1;
    }

    @Override
    public void setStack(ItemStack stack) {
        if (this.hasStack())
            return;
        // Ensure that only one item can be in the slot.
        if (!stack.isEmpty() && stack.getCount() > 1) {
            stack.setCount(1);
        }
        super.setStack(stack);
    }
}
