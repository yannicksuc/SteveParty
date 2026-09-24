package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Loading screen of the stencil gun held by the player: a row of 9 stencil slots, a row of 9 dye slots, and the
 * player inventory. The gun is found again in its inventory slot at every change (never held as a stale copy) and
 * cannot be moved while the screen is open; the screen closes if it goes away.
 */
public class StencilGunScreenHandler extends ScreenHandler {
    /** {@link #gunSlot} value of a gun held in the off hand. */
    public static final int OFF_HAND_SLOT = PlayerInventory.OFF_HAND_SLOT;
    /** Laid out like a 2-row chest (vanilla generic container texture). */
    public static final int GUN_ROWS_Y = 18;
    public static final int PLAYER_INVENTORY_Y = 67;

    private final PlayerInventory playerInventory;
    private final int gunSlot;
    private final SimpleInventory loaded = new SimpleInventory(StencilGunItem.SIZE);
    private boolean loading;

    /** @param gunSlot slot of the player inventory holding the gun (sent to the client when the screen opens) */
    public StencilGunScreenHandler(int syncId, PlayerInventory playerInventory, int gunSlot) {
        super(ModScreensHandlers.STENCIL_GUN_SCREEN_HANDLER, syncId);
        this.playerInventory = playerInventory;
        this.gunSlot = gunSlot;

        ItemStack gun = gun();
        if (!gun.isEmpty()) {
            loading = true;
            List<ItemStack> contents = StencilGunItem.contents(gun);
            for (int i = 0; i < StencilGunItem.SIZE; i++) loaded.setStack(i, contents.get(i));
            loading = false;
        }
        loaded.addListener(inventory -> save());

        for (int i = 0; i < StencilGunItem.STENCIL_SLOTS; i++) {
            addSlot(new FilteredSlot(loaded, i, 8 + i * 18, GUN_ROWS_Y, true));
        }
        for (int i = 0; i < StencilGunItem.DYE_SLOTS; i++) {
            addSlot(new FilteredSlot(loaded, StencilGunItem.STENCIL_SLOTS + i, 8 + i * 18, GUN_ROWS_Y + 18, false));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                addSlot(new LockableSlot(playerInventory, index, 8 + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new LockableSlot(playerInventory, column, 8 + column * 18, PLAYER_INVENTORY_Y + 58));
        }
    }

    private ItemStack gun() {
        if (gunSlot < 0) return ItemStack.EMPTY;
        ItemStack stack = playerInventory.getStack(gunSlot);
        return stack.getItem() instanceof StencilGunItem ? stack : ItemStack.EMPTY;
    }

    private void save() {
        if (loading) return;
        ItemStack gun = gun();
        if (gun.isEmpty()) return;
        List<ItemStack> contents = new ArrayList<>(StencilGunItem.SIZE);
        for (int i = 0; i < StencilGunItem.SIZE; i++) contents.add(loaded.getStack(i).copy());
        StencilGunItem.setContents(gun, contents);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return !gun().isEmpty();
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // The gun itself stays where it is (hotbar number keys included)
        if (actionType == SlotActionType.SWAP && button == gunSlot) return;
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        save();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasStack() || !slot.canTakeItems(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        int gunEnd = StencilGunItem.SIZE;
        if (slotIndex < gunEnd) {
            if (!insertItem(stack, gunEnd, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof StencilItem) {
            if (!insertItem(stack, 0, StencilGunItem.STENCIL_SLOTS, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof DyeItem) {
            if (!insertItem(stack, StencilGunItem.STENCIL_SLOTS, gunEnd, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return original;
    }

    /** Stencil row takes stencils only, dye row dyes only. */
    private static class FilteredSlot extends Slot {
        private final boolean stencils;

        FilteredSlot(SimpleInventory inventory, int index, int x, int y, boolean stencils) {
            super(inventory, index, x, y);
            this.stencils = stencils;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stencils ? stack.getItem() instanceof StencilItem : stack.getItem() instanceof DyeItem;
        }
    }

    /** Player slot that cannot take the gun away. */
    private class LockableSlot extends Slot {
        LockableSlot(PlayerInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canTakeItems(PlayerEntity player) {
            return getIndex() != gunSlot && super.canTakeItems(player);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return getIndex() != gunSlot && super.canInsert(stack);
        }
    }

    public boolean isGunSlot(Slot slot) {
        return slot instanceof LockableSlot && slot.getIndex() == gunSlot;
    }
}
