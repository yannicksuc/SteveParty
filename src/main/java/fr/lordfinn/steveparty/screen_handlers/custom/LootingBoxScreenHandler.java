package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.LootingBoxBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

public class LootingBoxScreenHandler extends CartridgeContainerScreenHandler {

    private static final int INVENTORY_SIZE = 1;

    public LootingBoxScreenHandler(int syncId, PlayerInventory playerInventory, Inventory blockInventory) {
        super(ModScreensHandlers.LOOTING_BOX_SCREEN_HANDLER, syncId);
        this.inventory = blockInventory;
        checkSize(blockInventory, INVENTORY_SIZE);
        init(playerInventory, 54);
    }

    public LootingBoxScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(INVENTORY_SIZE));
    }

    @Override
    public void setupScreen() {
        this.addSlot(new InventoryCartridgeCustomSlot(inventory, 0, 62, 16));
    }

    private static class InventoryCartridgeCustomSlot extends CartridgeCustomSlot {
        public InventoryCartridgeCustomSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        protected boolean isAllowedItem(ItemStack originalStack) {
            return originalStack.getItem() instanceof InventoryCartridgeItem;
        }
    }
}

