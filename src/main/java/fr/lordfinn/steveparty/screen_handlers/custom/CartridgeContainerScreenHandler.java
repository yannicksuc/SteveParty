package fr.lordfinn.steveparty.screen_handlers.custom;

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

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_COMPONENT;

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
        return this.inventory.canPlayerUse(player);
    }

    // Shift + Player Inv Slot
    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            originalStack.getOrDefault(INVENTORY_COMPONENT,ItemStack.EMPTY);
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

    public boolean isSingle() {
        return inventory.size() == 1;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.CLOSE_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
