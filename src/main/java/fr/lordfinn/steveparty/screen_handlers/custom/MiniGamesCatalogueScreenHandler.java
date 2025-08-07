package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;

public class MiniGamesCatalogueScreenHandler extends ScreenHandler {
    private final InventoryComponent inventory;

    public MiniGamesCatalogueScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new InventoryComponent(91));
    }

    public MiniGamesCatalogueScreenHandler(int syncId, PlayerInventory playerInventory, InventoryComponent inventory) {
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
        return true;
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
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.CLOSE_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
