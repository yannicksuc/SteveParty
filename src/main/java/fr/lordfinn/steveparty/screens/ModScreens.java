package fr.lordfinn.steveparty.screens;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreens {
    public static final ScreenHandlerType<TileScreenHandler> TILE_SCREEN_HANDLER =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of(Steveparty.MOD_ID, "tile_screen_handler"),
                    new ScreenHandlerType<>(TileScreenHandler::new, FeatureSet.empty()));
    public static final ScreenHandlerType<CustomizableMerchantScreenHandler> HIDING_TRADER_SCREEN_HANDLER =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of(Steveparty.MOD_ID, "hiding_trader_screen_handler"),
                    new ScreenHandlerType<>(CustomizableMerchantScreenHandler::new, FeatureSet.empty()));
    public static void initialize() {
    }
}
