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
    private final Inventory inventory;

    public TradingStallScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
        this(syncId, playerInventory, getInventoryFromPos(playerInventory, pos));
    }

    private static Inventory getInventoryFromPos(PlayerInventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.getWorld().getBlockEntity(pos);
        if (be instanceof TradingStallBlockEntity stall) {
            return stall;
        }
        return new SimpleInventory(28); // Fallback
    }


    public TradingStallScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ModScreensHandlers.TRADING_STALL_SCREEN_HANDLER, syncId);
        this.inventory = inventory;

        checkSize(inventory, 28);

        // Block inventory slots
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(inventory, j + i * 9, 8 + j * 18, 18 + i * 18));
            }
        }

        for (int j = 0; j < 9; ++j) {
            this.addSlot(new Slot(inventory, j + 2 * 9, 8 + j * 18, 18 + 3 * 18));
        }

        this.addSlot(new Slot(inventory, 27, 178, 36));

        addPlayerSlots(playerInventory, 8, 102);
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
