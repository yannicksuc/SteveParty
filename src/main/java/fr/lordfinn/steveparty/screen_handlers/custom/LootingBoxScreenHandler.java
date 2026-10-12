package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** The Looting Box's menu: its Inventory Cartridge slot and, on the right, the cartridge's menu (see {@link #addCartridgeMenu}). */
public class LootingBoxScreenHandler extends CartridgeContainerScreenHandler {

    private static final int INVENTORY_SIZE = 1;

    public LootingBoxScreenHandler(int syncId, PlayerInventory playerInventory, Inventory blockInventory) {
        this(syncId, playerInventory, blockInventory, posOf(blockInventory));
    }

    private LootingBoxScreenHandler(int syncId, PlayerInventory playerInventory, Inventory blockInventory, @Nullable BlockPos pos) {
        super(ModScreensHandlers.LOOTING_BOX_SCREEN_HANDLER, syncId);
        this.inventory = blockInventory;
        checkSize(blockInventory, INVENTORY_SIZE);
        init(playerInventory, 54);
        addCartridgeMenu(playerInventory.player, pos);
    }

    /** Client side: the slot's copy, the box's position from the opening data. */
    public LootingBoxScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload payload) {
        this(syncId, playerInventory, new SimpleInventory(INVENTORY_SIZE), payload.pos());
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

