package fr.lordfinn.steveparty.screen_handlers;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;

public class TileScreenHandler extends CartridgeContainerScreenHandler {
    public TileScreenHandler(int syncId, PlayerInventory playerInventory , Inventory inventory) {
        super(ModScreensHandlers.TILE_SCREEN_HANDLER, syncId, inventory);
        init(playerInventory, 108);
    }
    public TileScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(16));
    }

    @Override
    public void setupScreen() {
        int m, l;
        if (this.inventory.size() == 16) {
            for (m = 0; m < 4; ++m) {
                for (l = 0; l < 4; ++l) {
                    this.addSlot(new CustomSlot(this.inventory, l + m * 4, 53 + l * 18, 17 + m * 18));
                }
            }
        }
    }
}
