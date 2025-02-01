package fr.lordfinn.steveparty.screen_handlers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

import static fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers.TELEPORTATION_BOOK_SCREEN_HANDLER;

public class TeleportationBookScreenHandler  extends ScreenHandler {

    protected TeleportationBookScreenHandler(ScreenHandlerType<TeleportationBookScreenHandler> type, int syncId) {
        super(type, syncId);
    }

    public TeleportationBookScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(TELEPORTATION_BOOK_SCREEN_HANDLER, syncId);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return null;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}