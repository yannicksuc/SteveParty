package fr.lordfinn.steveparty.client;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.screens.TileScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

import static fr.lordfinn.steveparty.screens.ModScreens.TILE_SCREEN_HANDLER;

public class StevepartyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(TILE_SCREEN_HANDLER, TileScreen::new);
        Steveparty.LOGGER.info("Registering {} screens", Steveparty.MOD_ID);
    }
}
