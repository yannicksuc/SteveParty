package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.components.ItemStackBackedInventory;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;

public class MiniGamesCatalogueScreenHandler extends ScreenHandler {
    public static final int SIZE = 91;
    private final Inventory inventory;

    public MiniGamesCatalogueScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(SIZE));
    }

    /**
     * @param inventory server side: an {@link ItemStackBackedInventory} bound to the catalogue stack,
     *                  so that the pages are written back to the item on every change.
     */
    public MiniGamesCatalogueScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ModScreensHandlers.MINI_GAMES_CATALOGUE_SCREEN_HANDLER, syncId);
        this.inventory = inventory;

        // Add the catalogue inventory slots
        for (int row = 0; row < 7; ++row) {
            for (int col = 0; col < 13; ++col) {
                this.addSlot(new CustomSlot(inventory, col + row * 13, 8 + col * 18, 18 + row * 18));
            }
        }

        addPlayerInventorySlots(playerInventory, 44, 158);
        addPlayerHotbarSlots(playerInventory, 44, 216);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        // Server side: the catalogue must still be owned by the player (not dropped/moved away), otherwise pages could be duped
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player) {
        if (!this.canUse(player)) return;
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    public Inventory getInventory() {
        return inventory;
    }

    // Custom slot class for the mini-games catalogue items
    public static class CustomSlot extends Slot {
        public CustomSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.getItem() instanceof MiniGamePageItem; // Only allow MiniGamesCatalogueItem
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (this.inventory instanceof ItemStackBackedInventory backed) {
            backed.writeBack();
        }
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.CLOSE_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
