package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
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

    public CartridgeContainerScreenHandler(ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    protected void setupScreen() {
    }

    public CartridgeContainerScreenHandler(ScreenHandlerType<?> type, int syncId, Inventory inventory) {
        super(type, syncId);
        this.inventory = inventory;
    }

    void init(PlayerInventory playerInventory, int playerInventoryTitleY) {
        if (inventory != null)
            inventory.onOpen(playerInventory.player);
        setupScreen();
        addPlayerSlots(playerInventory, 8, playerInventoryTitleY);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return ScreenHandlerChecks.canUseInventory(this.inventory, player);
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
                if (!this.insertItem(originalStack, this.inventory.size(), realSlotsEnd(), true)) {
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

    /** The end of the slots shift-clicks move items between (the cartridge slots, then the player's). */
    protected int realSlotsEnd() {
        return this.slots.size();
    }

    /** The number of cartridge slots (they come first). */
    public int getInventorySize() {
        return inventory == null ? 0 : inventory.size();
    }

    public boolean isSingle() {
        return inventory.size() == 1;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.CLOSE_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    /** The player's inventory (3 rows) at (left, top) and its hotbar 58 pixels under it. */
    private void addPlayerSlots(PlayerInventory playerInventory, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, left + col * 18, top + 58));
        }
    }
}
