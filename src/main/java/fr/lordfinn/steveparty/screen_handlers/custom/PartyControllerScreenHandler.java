package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity.*;

/**
 * Settings of a party controller: the coin and star items (one of each, any item but a party card), then the
 * program: party cards read left to right, top to bottom.
 */
public class PartyControllerScreenHandler extends ScreenHandler {
    public static final int PLAYER_INVENTORY_Y = 86;
    private final Inventory inventory;
    private final @Nullable PartyControllerEntity controller;

    /** Client side. */
    public PartyControllerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(SETTINGS_SIZE), null);
    }

    public PartyControllerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, @Nullable PartyControllerEntity controller) {
        super(ModScreensHandlers.PARTY_CONTROLLER_SCREEN_HANDLER, syncId);
        checkSize(inventory, SETTINGS_SIZE);
        this.inventory = inventory;
        this.controller = controller;
        inventory.onOpen(playerInventory.player);
        addSlot(new CurrencySlot(inventory, SLOT_COIN, 8, 18));
        addSlot(new CurrencySlot(inventory, SLOT_STAR, 26, 18));
        for (int i = 0; i < PROGRAM_SLOTS; i++)
            addSlot(new CardSlot(inventory, PROGRAM_FIRST_SLOT + i, 8 + (i % 9) * 18, 36 + (i / 9) * 18));
        addPlayerSlots(playerInventory, 8, PLAYER_INVENTORY_Y);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return controller == null ? inventory.canPlayerUse(player) : ScreenHandlerChecks.canUseBlockEntity(controller, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (slotIndex < SETTINGS_SIZE) {
            if (!insertItem(stack, SETTINGS_SIZE, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof PartyCardItem) {
            if (!insertItem(stack, PROGRAM_FIRST_SLOT, SETTINGS_SIZE, false)) return ItemStack.EMPTY;
        } else if (!insertItem(stack, SLOT_COIN, PROGRAM_FIRST_SLOT, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return original;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        inventory.onClose(player);
        player.getWorld().playSound(null, player.getBlockPos(), ModSounds.CLOSE_TILE_GUI_SOUND_EVENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    /** The coin / star item: one item, which is only a model (the players keep their own). */
    private static class CurrencySlot extends Slot {
        CurrencySlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return !(stack.getItem() instanceof PartyCardItem);
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }

    private static class CardSlot extends Slot {
        CardSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.getItem() instanceof PartyCardItem;
        }
    }
}
