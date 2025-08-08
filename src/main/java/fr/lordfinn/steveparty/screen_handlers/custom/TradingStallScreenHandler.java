package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class TradingStallScreenHandler extends ScreenHandler {

    // ===== Constants for maintainability =====
    private static final int INVENTORY_SIZE = 28;

    // Block inventory slot layout
    private static final int STALL_INV_ROWS_TOP = 2;
    private static final int STALL_INV_COLS = 9;
    private static final int STALL_INV_TOP_X = 12;
    private static final int STALL_INV_TOP_Y = 53;
    private static final int SLOT_SPACING = 18;

    // Middle row (third row) offset
    private static final int MIDDLE_ROW_INDEX = 2;
    private static final int MIDDLE_ROW_Y_OFFSET = 0;

    // Special slot (index 27)
    private static final int SPECIAL_SLOT_INDEX = 27;
    private static final int SPECIAL_SLOT_X = 182;
    private static final int SPECIAL_SLOT_Y = 62;

    // Player inventory position
    private static final int PLAYER_INV_X = 12;
    private static final int PLAYER_INV_Y = 105;

    private final Inventory inventory;

    public TradingStallScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, getInventoryFromPos(playerInventory, pos));
    }

    private static Inventory getInventoryFromPos(PlayerInventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.getWorld().getBlockEntity(pos);
        if (be instanceof TradingStallBlockEntity stall) {
            return stall;
        }
        return new SimpleInventory(INVENTORY_SIZE); // Fallback
    }

    public TradingStallScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ModScreensHandlers.TRADING_STALL_SCREEN_HANDLER, syncId);
        this.inventory = inventory;

        checkSize(inventory, INVENTORY_SIZE);

        // Top 2 rows of block inventory
        for (int i = 0; i < STALL_INV_ROWS_TOP; ++i) {
            for (int j = 0; j < STALL_INV_COLS; ++j) {
                this.addSlot(new Slot(
                        inventory,
                        j + i * STALL_INV_COLS,
                        STALL_INV_TOP_X + j * SLOT_SPACING,
                        STALL_INV_TOP_Y + i * SLOT_SPACING
                ));
            }
        }

        // Middle row (3rd row)
        for (int j = 0; j < STALL_INV_COLS; ++j) {
            this.addSlot(new Slot(
                    inventory,
                    j + MIDDLE_ROW_INDEX * STALL_INV_COLS,
                    STALL_INV_TOP_X + j * SLOT_SPACING,
                    0
            ));
        }

        // Special slot
        this.addSlot(new Slot(inventory, SPECIAL_SLOT_INDEX, SPECIAL_SLOT_X, SPECIAL_SLOT_Y));

        // Player inventory
        addPlayerSlots(playerInventory, PLAYER_INV_X, PLAYER_INV_Y);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            if (index < this.inventory.size()) {
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

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }
}
