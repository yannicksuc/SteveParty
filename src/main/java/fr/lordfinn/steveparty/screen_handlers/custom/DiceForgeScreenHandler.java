package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import static fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers.DICE_FORGE_SCREEN_HANDLER;

public class DiceForgeScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private static int INVENTORY_SIZE = 13;

    public DiceForgeScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(DICE_FORGE_SCREEN_HANDLER, syncId);
        checkSize(inventory, 13); // enforce correct inventory size
        this.inventory = inventory;
        inventory.onOpen(playerInventory.player);

        // --- Add the 12 face slots ---
        int[][] positions = {
                {80, 9},   {54, 19},  {106, 19},
                {36, 37},  {124, 37}, {26, 63},
                {134, 63}, {36, 89},  {124, 89},
                {54, 107}, {106, 107},{80, 117}
        };

        for (int i = 0; i < 12; i++) {
            int x = positions[i][0];
            int y = positions[i][1];
            this.addSlot(new Slot(inventory, i, x, y));
        }

        // --- Add the special Power Star slot (index 12) ---
        this.addSlot(new Slot(inventory, 12, 80, 63) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(ModItems.POWER_STAR);
            }
        });

        // --- Player inventory ---
        addPlayerSlots(playerInventory, 8, 140); // adjust Y offset to fit under your GUI
    }

    public DiceForgeScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(INVENTORY_SIZE));
    }


    private void addPlayerSlots(PlayerInventory playerInventory, int left, int top) {
        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        left + col * 18, top + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col,
                    left + col * 18, top + 58));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack stackInSlot = slot.getStack();
            itemStack = stackInSlot.copy();

            // If it's from block inventory → try to move to player
            if (index < 13) {
                if (!this.insertItem(stackInSlot, 13, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From player → try power star slot first
                if (stackInSlot.isOf(ModItems.POWER_STAR)) {
                    if (!this.insertItem(stackInSlot, 12, 13, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // then try face slots
                    if (!this.insertItem(stackInSlot, 0, 12, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return itemStack;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.inventory.onClose(player);
    }

    public Inventory getInventory() {
        return this.inventory;
    }
}
