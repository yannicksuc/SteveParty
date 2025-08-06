package fr.lordfinn.steveparty.screen_handlers;

import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;

import java.util.Optional;

import static fr.lordfinn.steveparty.components.ModComponents.IS_NEGATIVE;

public class CartridgeInventoryScreenHandler extends ScreenHandler {
    private final InventoryComponent inventory;

    // Constructor for the screen handler
    public CartridgeInventoryScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new InventoryComponent(9));
    }

    public CartridgeInventoryScreenHandler(int syncId, PlayerInventory playerInventory, InventoryComponent inventory) {
        super(ModScreensHandlers.CARTRIDGE_SCREEN_HANDLER, syncId);
        this.inventory = inventory;

        // Adding slots to the inventory with item type validation
        int m, l;
        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 3; ++l) {
                this.addSlot(new CartridgeInventoryScreenHandler.CustomSlot(inventory, l + m * 3, 62 + l * 18, 6 + m * 18));
            }
        }

        // Adding player inventory slots
        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + m * 9 + 9, 8 + l * 18, 105 + m * 18));
            }
        }
        for (m = 0; m < 9; ++m) {
            this.addSlot(new Slot(playerInventory, m, 8 + m * 18, 163));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    // Custom slot class that only allows certain items
    public static class CustomSlot extends Slot {
        public CustomSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public Optional<ItemStack> tryTakeStackRange(int min, int max, PlayerEntity player) {
            this.setStack(ItemStack.EMPTY);
            return Optional.empty();
        }
        @Override
        public ItemStack takeStackRange(int min, int max, PlayerEntity player) {
            this.setStack(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertStack(ItemStack stack, int count) {
            if (stack.isEmpty()) {
                return stack;
            }
            this.setStack(stack.copyWithCount(1));
            return stack;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return true;
        }

        @Override
        public int getMaxItemCount(ItemStack stack) {
            return 1028;
        }

        @Override
        public void setStack(ItemStack stack) {
            super.setStack(stack);
        }

        public boolean isPositive(ItemStack stack) {
            return (!stack.contains(IS_NEGATIVE) || Boolean.FALSE.equals(stack.get(IS_NEGATIVE)));
        }

        // Method to handle scroll interactions
        public void onScroll(double amount) {
            ItemStack stack = this.getStack();
            if (stack.isEmpty()) return;

            boolean isPositive = (!stack.contains(IS_NEGATIVE) || Boolean.FALSE.equals(stack.get(IS_NEGATIVE)));
            int change = amount > 0 ? 1 : -1;
            if (!isPositive)
                change *= -1;
            int newCount = stack.getCount() + change;
            if (newCount <= 0)
                stack.set(IS_NEGATIVE, isPositive);
            if (newCount > 0 && newCount <= getMaxItemCount(stack)) {
                stack.setCount(newCount);
            }
            markDirty();
        }
        @Override
        public boolean canTakePartial(PlayerEntity player) {
            return false;
        }
    }

        // Shift + Player Inv Slot
    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.CLOSE_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
