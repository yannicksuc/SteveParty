package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.LootingBoxBlockEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public class LootingBoxScreenHandler extends CartridgeContainerScreenHandler {
    public LootingBoxScreenHandler(int syncId, PlayerInventory playerInventory, LootingBoxBlockEntity blockEntity) {
        super(ModScreensHandlers.LOOTING_BOX_SCREEN_HANDLER, syncId);
        this.inventory = blockEntity;
        init(playerInventory, 54);
    }

    public LootingBoxScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload payload) {
        this(syncId, playerInventory, (LootingBoxBlockEntity) playerInventory.player.getWorld().getBlockEntity(payload.pos()));
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
