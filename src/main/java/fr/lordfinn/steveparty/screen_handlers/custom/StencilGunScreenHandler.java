package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

/**
 * The hammer's slots beside the player's inventory (opened with the inventory key or from its wheel, hammer in hand):
 * the player's inventory as usual (armour, off hand, inventory, hotbar) with its 9 dye slots on the left and its 9
 * stencil slots on the right, each in a 3 x 3 grid. The gun is found again in its inventory slot at every change (never held as a stale copy) and
 * cannot be moved while the screen is open; the screen closes if it goes away.
 * <p>
 * Server side, only the very stack the screen was opened on counts as the gun: another gun swapped into that slot
 * (off-hand swap key, a modified client) must never receive this gun's contents.
 */
public class StencilGunScreenHandler extends ScreenHandler {
    /** {@link #gunSlot} value of a gun held in the off hand. */
    public static final int OFF_HAND_SLOT = PlayerInventory.OFF_HAND_SLOT;
    /** Width of a side panel (3 slots and the borders), and the gap between it and the inventory. */
    public static final int SIDE = 68, GAP = 2;
    /** Top of the hammer's 3 x 3 grids, under their title. */
    public static final int SLOTS_Y = 18;
    /** Where the player's inventory (the vanilla one, 176 x 166) starts. */
    public static final int INVENTORY_X = SIDE + GAP;
    public static final int WIDTH = INVENTORY_X + 176 + GAP + SIDE;
    /** First slot of the player's inventory, of its armour (head first) and its off hand. */
    public static final int PLAYER_START = StencilGunItem.SIZE, ARMOR_START = PLAYER_START + 36, OFF_HAND = ARMOR_START + 4;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Identifier[] ARMOR_SILHOUETTES = {PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE, PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE,
            PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE};

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

        // Dyes on the left, stencils on the right (as on the wheel)
        int stencilsX = INVENTORY_X + 176 + GAP + 8;
        for (int i = 0; i < StencilGunItem.STENCIL_SLOTS; i++) {
            addSlot(new FilteredSlot(loaded, i, stencilsX + i % 3 * 18, SLOTS_Y + i / 3 * 18, true));
        }
        for (int i = 0; i < StencilGunItem.DYE_SLOTS; i++) {
            addSlot(new FilteredSlot(loaded, StencilGunItem.STENCIL_SLOTS + i, 8 + i % 3 * 18, SLOTS_Y + i / 3 * 18, false));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int index = column + row * 9 + 9;
                addSlot(new LockableSlot(playerInventory, index, INVENTORY_X + 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new LockableSlot(playerInventory, column, INVENTORY_X + 8 + column * 18, 142));
        }
        // Armour and off hand, where the vanilla inventory has them
        for (int i = 0; i < 4; i++) {
            addSlot(new ArmorSlot(playerInventory, 39 - i, INVENTORY_X + 8, 8 + i * 18, ARMOR[i], ARMOR_SILHOUETTES[i]));
        }
        addSlot(new LockableSlot(playerInventory, PlayerInventory.OFF_HAND_SLOT, INVENTORY_X + 77, 62) {
            @Override
            public Pair<Identifier, Identifier> getBackgroundSprite() {
                return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, PlayerScreenHandler.EMPTY_OFFHAND_ARMOR_SLOT);
            }
        });
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
        if (slotIndex < gunEnd || slotIndex >= ARMOR_START) {
            if (!insertItem(stack, PLAYER_START, ARMOR_START, true)) return ItemStack.EMPTY;
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

        @Override
        public Pair<Identifier, Identifier> getBackgroundSprite() {
            return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,
                    fr.lordfinn.steveparty.Steveparty.id(stencils ? "item/empty_slot_stencil" : "item/empty_slot_dye"));
        }
    }

    /** An armour slot of the player: only what goes there, one at a time, a cursed piece stays on (out of creative). */
    private class ArmorSlot extends LockableSlot {
        private final EquipmentSlot equipment;
        private final Identifier silhouette;

        ArmorSlot(PlayerInventory inventory, int index, int x, int y, EquipmentSlot equipment, Identifier silhouette) {
            super(inventory, index, x, y);
            this.equipment = equipment;
            this.silhouette = silhouette;
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return playerInventory.player.getPreferredEquipmentSlot(stack) == equipment && super.canInsert(stack);
        }

        @Override
        public boolean canTakeItems(PlayerEntity player) {
            ItemStack stack = getStack();
            if (!stack.isEmpty() && !player.isCreative()
                    && EnchantmentHelper.hasAnyEnchantmentsWith(stack, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) return false;
            return super.canTakeItems(player);
        }

        @Override
        public Pair<Identifier, Identifier> getBackgroundSprite() {
            return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, silhouette);
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
