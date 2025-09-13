package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;

public class RouterScreenHandler extends CartridgeContainerScreenHandler {
    public RouterScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ModScreensHandlers.ROUTER_SCREEN_HANDLER, syncId, inventory);
        init(playerInventory, 54);
    }
    public RouterScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(1));
    }

    @Override
    public void setupScreen() {
        this.addSlot(new CartridgeCustomSlot(inventory, 0, 62, 16));
    }
}
