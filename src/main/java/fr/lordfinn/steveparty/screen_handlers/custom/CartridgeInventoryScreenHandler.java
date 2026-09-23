package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;

import java.util.Optional;

import static fr.lordfinn.steveparty.components.ModComponents.IS_NEGATIVE;

/**
 * Cartridge configuration screen. The 9 first slots are "ghost" slots: they hold a copy of an item
 * (with a quantity set by scrolling) and never consume nor give real items.
 */
public class CartridgeInventoryScreenHandler extends ScreenHandler {
    public static final int GHOST_SLOT_COUNT = 9;
    private final Inventory inventory;

    // Constructor for the screen handler
    public CartridgeInventoryScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(GHOST_SLOT_COUNT));
    }

    public CartridgeInventoryScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
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

    private boolean isGhostSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < GHOST_SLOT_COUNT && slotIndex < this.slots.size()
                && this.slots.get(slotIndex) instanceof CustomSlot;
    }

    /**
     * Ghost slots are handled here so that no real item is ever created or consumed.
     */
    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!isGhostSlot(slotIndex)) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }
        CustomSlot slot = (CustomSlot) this.slots.get(slotIndex);
        switch (actionType) {
            case PICKUP -> {
                // Item on cursor → set a ghost copy, empty cursor → clear the ghost slot
                ItemStack cursor = this.getCursorStack();
                slot.setGhostStack(cursor);
            }
            case SWAP -> {
                // Hotbar/offhand key → ghost copy of that stack (or clear if empty), nothing is moved
                if (button >= 0 && button < player.getInventory().size()) {
                    slot.setGhostStack(player.getInventory().getStack(button));
                }
            }
            case THROW -> {
                if (this.getCursorStack().isEmpty()) {
                    slot.setGhostStack(ItemStack.EMPTY);
                }
            }
            case CLONE -> {
                if (player.isInCreativeMode() && this.getCursorStack().isEmpty() && slot.hasStack()) {
                    ItemStack clone = slot.getStack().copyWithCount(slot.getStack().getMaxCount());
                    clone.remove(IS_NEGATIVE);
                    this.setCursorStack(clone);
                }
            }
            case QUICK_MOVE -> {
                // Nothing: ghost items can't be moved to the player inventory
            }
            default -> super.onSlotClick(slotIndex, button, actionType, player);
        }
    }

    /** Ghost slots never take part in drag-splitting (it would consume real items). */
    @Override
    public boolean canInsertIntoSlot(Slot slot) {
        return !(slot instanceof CustomSlot) && super.canInsertIntoSlot(slot);
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return !(slot instanceof CustomSlot) && super.canInsertIntoSlot(stack, slot);
    }

    /**
     * Server-side entry point of {@link fr.lordfinn.steveparty.payloads.custom.CartridgeSlotScrollPayload}.
     */
    public void handleScroll(PlayerEntity player, int slotIndex, int direction) {
        if (!canUse(player) || !isGhostSlot(slotIndex) || direction == 0) return;
        ((CustomSlot) this.slots.get(slotIndex)).onScroll(direction);
    }

    // Custom slot class that only allows certain items
    public static class CustomSlot extends Slot {
        public CustomSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        /** Sets a ghost copy (count 1) of {@code stack}, or clears the slot if it is empty. Never consumes it. */
        public void setGhostStack(ItemStack stack) {
            if (stack.isEmpty()) {
                this.setStack(ItemStack.EMPTY);
            } else {
                this.setStack(stack.copyWithCount(1));
            }
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
        public ItemStack takeStack(int amount) {
            // Ghost content is never handed out
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
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return false;
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
