package fr.lordfinn.steveparty.screen_handlers;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;

public class RouterScreenHandler extends CartridgeContainerScreenHandler {
    public RouterScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ModScreensHandlers.ROUTER_SCREEN_HANDLER, syncId, inventory);
        init(playerInventory, 52);
    }
    public RouterScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(1));
    }

    @Override
    public void setupScreen() {
        this.addSlot(new CustomSlot(inventory, 0, 80, 18));
    }
}
