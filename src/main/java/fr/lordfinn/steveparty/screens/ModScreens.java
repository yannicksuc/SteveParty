package fr.lordfinn.steveparty.screens;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreens {
    public static final ScreenHandlerType<TileScreenHandler> TILE_SCREEN_HANDLER =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of("steveparty", "tile_screen_handler"),
                    new ScreenHandlerType<>(TileScreenHandler::new, FeatureSet.empty()));
    public static final ScreenHandlerType<CustomizableMerchantScreenHandler> CUSTOMIZABLE_MERCHANT_SCREEN_HANDLER =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of("steveparty", "customizable_merchant_screen_handler"),
                    new ScreenHandlerType<>(CustomizableMerchantScreenHandler::new, FeatureSet.empty()));
    public static void initialize() {
    }
}
