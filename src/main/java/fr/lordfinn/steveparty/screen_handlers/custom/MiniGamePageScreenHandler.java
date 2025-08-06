package fr.lordfinn.steveparty.screen_handlers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

public class MiniGamePageScreenHandler extends ScreenHandler {
    public MiniGamePageScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ModScreensHandlers.MINI_GAME_PAGE_SCREEN_HANDLER, syncId);
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
