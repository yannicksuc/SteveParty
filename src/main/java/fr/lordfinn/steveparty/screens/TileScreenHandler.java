package fr.lordfinn.steveparty.screens;

import fr.lordfinn.steveparty.items.tilebehaviors.TileBehaviorItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;

public class TileScreenHandler extends ScreenHandler {
    private final Inventory inventory;

    // Constructor for the screen handler
    public TileScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(16));
    }

    public TileScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ModScreens.TILE_SCREEN_HANDLER, syncId);
        checkSize(inventory, 16);
        this.inventory = inventory;
        inventory.onOpen(playerInventory.player);

        // Adding slots to the inventory with item type validation
        int m, l;
        for (m = 0; m < 4; ++m) {
            for (l = 0; l < 4; ++l) {
                this.addSlot(new CustomSlot(inventory, l + m * 4, 53 + l * 18, 17 + m * 18));
            }
        }

        // Adding player inventory slots (no restrictions)
        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 108 + m * 18));
            }
        }
        for (m = 0; m < 9; ++m) {
            this.addSlot(new Slot(playerInventory, m, 8 + m * 18, 166));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    // Custom slot class that only allows certain items
    private static class CustomSlot extends Slot {
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
        return originalStack.getItem() instanceof TileBehaviorItem;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.CLOSE_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
