package fr.lordfinn.steveparty.screen_handlers;

import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;

public abstract class CartridgeContainerScreenHandler extends ScreenHandler {
    protected Inventory inventory = null;

    public CartridgeContainerScreenHandler(ScreenHandlerType<TileScreenHandler> tileScreenHandler, int syncId) {
        super(tileScreenHandler, syncId);
    }

    protected void setupScreen() {
    }

    public CartridgeContainerScreenHandler(ScreenHandlerType<?> type, int syncId, Inventory inventory) {
        super(type, syncId);
        this.inventory = inventory;
    }

    void init(PlayerInventory playerInventory, int playerInventoryTitleY) {
        inventory.onOpen(playerInventory.player);
        setupScreen();
        addPlayerSlots(playerInventory, 8, playerInventoryTitleY);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    // Custom slot class that only allows certain items
    protected static class CustomSlot extends Slot {
        public CustomSlot(Inventory inventory, int index, int x, int y) {
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


    // Shift + Player Inv Slot
    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            if (invSlot < this.inventory.size()) {
                if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(originalStack, 0, this.inventory.size(), false)) {
                return ItemStack.EMPTY;
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return newStack;
    }

    private static boolean isAllowedItem(ItemStack originalStack) {
        return originalStack.getItem() instanceof CartridgeItem;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.CLOSE_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
