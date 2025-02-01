package fr.lordfinn.steveparty.screen_handlers;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandlerType;

import static fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers.HERE_WE_COME_BOOK_SCREEN_HANDLER;

public class HereWeComeBookScreenHandler extends TeleportationBookScreenHandler {
    protected HereWeComeBookScreenHandler(ScreenHandlerType<HereWeGoBookScreenHandler> type, int syncId) {
        super(HERE_WE_COME_BOOK_SCREEN_HANDLER, syncId);
    }

    public HereWeComeBookScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(HERE_WE_COME_BOOK_SCREEN_HANDLER, syncId);
    }
}