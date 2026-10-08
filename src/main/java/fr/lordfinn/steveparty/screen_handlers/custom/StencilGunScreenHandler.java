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
import net.minecraft.util.Identifier;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

/**
 * The hammer's refill (from its wheel, hammer in hand): its 18 slots laid out in a wheel, the 9 dye slots down the
 * left and the 9 stencil slots down the right, real slots (click, drag, shift-click), with the player's inventory and
 * hotbar under it. The gun is found again in its inventory slot at every change (never held as a stale copy) and
 * cannot be moved while the screen is open; the screen closes if it goes away.
 * <p>
 * Server side, only the very stack the screen was opened on counts as the gun: another gun swapped into that slot
 * (off-hand swap key, a modified client) must never receive this gun's contents.
 */
public class StencilGunScreenHandler extends ScreenHandler {
    /** {@link #gunSlot} value of a gun held in the off hand. */
    public static final int OFF_HAND_SLOT = PlayerInventory.OFF_HAND_SLOT;
    /** The screen, one sheet of the mod's paper: the wheel of the hammer's slots, the player's inventory under it. */
    public static final int WIDTH = 264, CENTER_X = 132, CENTER_Y = 128;
    /** The wheel's ring (its slots on the middle circle), and the degrees of each title half at its top. */
    public static final int RING_INNER = 74, RING_OUTER = 118, SLOT_RADIUS = 96;
    public static final float HEADER = 36;
    /**
     * Top of the player's inventory panel (the mod's shared one, its tab fitting under the hammer's panel 7 pixels up:
     * see gui/inventory_panel.png), its slots 15 lower, its hotbar 73 lower.
     */
    public static final int INVENTORY_Y = 249, INVENTORY_X = (WIDTH - 176) / 2;
    /** The hammer's panel (the wheel) above it, and the whole screen. */
    public static final int TOP_HEIGHT = INVENTORY_Y + 7, HEIGHT = INVENTORY_Y + 97;
    /** First slot of the player's inventory. */
    public static final int PLAYER_START = StencilGunItem.SIZE;

    /**
     * The angle (degrees, 0: top, clockwise) of the middle of a hammer slot: the stencils down the right from under
     * their title, the dyes down the left from under theirs.
     */
    public static double angle(int slot) {
        boolean stencil = slot < StencilGunItem.STENCIL_SLOTS;
        int index = stencil ? slot : slot - StencilGunItem.STENCIL_SLOTS;
        int count = stencil ? StencilGunItem.STENCIL_SLOTS : StencilGunItem.DYE_SLOTS;
        double step = (180 - HEADER) / count;
        return stencil ? HEADER + step * (index + 0.5) : 360 - HEADER - step * (index + 0.5);
    }

    private final PlayerInventory playerInventory;
    private final int gunSlot;
    /** The gun stack the screen was opened on (see {@link #gun()}). */
    private final ItemStack openedGun;
    private final SimpleInventory loaded = new SimpleInventory(StencilGunItem.SIZE);
    private boolean loading;

    /** @param gunSlot slot of the player inventory holding the gun (sent to the client when the screen opens) */
    public StencilGunScreenHandler(int syncId, PlayerInventory playerInventory, int gunSlot) {
        super(ModScreensHandlers.STENCIL_GUN_SCREEN_HANDLER, syncId);
        this.playerInventory = playerInventory;
        this.gunSlot = gunSlot;
        ItemStack found = gunSlot < 0 ? ItemStack.EMPTY : playerInventory.getStack(gunSlot);
        this.openedGun = found.getItem() instanceof StencilGunItem ? found : ItemStack.EMPTY;

        ItemStack gun = gun();
        if (!gun.isEmpty()) {
            loading = true;
            List<ItemStack> contents = StencilGunItem.contents(gun);
            for (int i = 0; i < StencilGunItem.SIZE; i++) loaded.setStack(i, contents.get(i));
            loading = false;
        }
        loaded.addListener(inventory -> save());

        // The hammer's slots around the wheel: dyes on the left, stencils on the right (as on its wheel)
        for (int i = 0; i < StencilGunItem.SIZE; i++) {
            double angle = Math.toRadians(angle(i));
            int x = CENTER_X + (int) Math.round(Math.sin(angle) * SLOT_RADIUS) - 8;
            int y = CENTER_Y - (int) Math.round(Math.cos(angle) * SLOT_RADIUS) - 8;
            addSlot(new FilteredSlot(loaded, i, x, y, i < StencilGunItem.STENCIL_SLOTS));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                addSlot(new LockableSlot(playerInventory, index, INVENTORY_X + 8 + column * 18, INVENTORY_Y + 15 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new LockableSlot(playerInventory, column, INVENTORY_X + 8 + column * 18, INVENTORY_Y + 73));
        }
    }

    /**
     * @return the gun being loaded, or EMPTY if it is no longer in its slot. On the server it must be the very stack
     * the screen was opened on; the client's copy of the slot is replaced by every sync, so it only checks the item.
     */
    private ItemStack gun() {
        if (gunSlot < 0) return ItemStack.EMPTY;
        ItemStack stack = playerInventory.getStack(gunSlot);
        if (!(stack.getItem() instanceof StencilGunItem)) return ItemStack.EMPTY;
        if (!playerInventory.player.getWorld().isClient && stack != openedGun) return ItemStack.EMPTY;
        return stack;
    }

    /** @return the slot of the player inventory holding the gun ({@link #OFF_HAND_SLOT} for the off hand). */
    public int getGunSlot() {
        return gunSlot;
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
        // Gun gone (dropped, swapped away) but the screen not closed yet: its contents can no longer be saved
        if (gun().isEmpty()) return;
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
            if (!insertItem(stack, PLAYER_START, this.slots.size(), true)) return ItemStack.EMPTY;
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

    /** The stencil slots take stencils only, the dye slots dyes only; empty, they show the silhouette of what they take. */
    public static class FilteredSlot extends Slot {
        private final boolean stencils;

        FilteredSlot(SimpleInventory inventory, int index, int x, int y, boolean stencils) {
            super(inventory, index, x, y);
            this.stencils = stencils;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stencils ? stack.getItem() instanceof StencilItem : stack.getItem() instanceof DyeItem;
        }

        public boolean takesStencils() {
            return stencils;
        }

        /**
         * The see-through silhouette shown in it while empty (a sprite of the block atlas). Not vanilla's background
         * sprite: that one is drawn with whatever blending the previous slot left (opaque after a hovered or filled
         * slot), the screen draws this one itself, blended.
         */
        public Identifier silhouette() {
            return fr.lordfinn.steveparty.Steveparty.id(stencils ? "item/empty_slot_stencil" : "item/empty_slot_dye");
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
