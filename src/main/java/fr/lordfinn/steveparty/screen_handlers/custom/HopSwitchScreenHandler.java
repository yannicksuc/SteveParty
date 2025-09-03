package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;

public class HopSwitchScreenHandler extends CartridgeContainerScreenHandler {
    public HopSwitchScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ModScreensHandlers.HOP_SWITCH_SCREEN_HANDLER, syncId, inventory);
        init(playerInventory, 54);
    }
    public HopSwitchScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(1));
    }

    @Override
    public void setupScreen() {
        this.addSlot(new CustomSlot(inventory, 0, 62, 16));
    }
}
