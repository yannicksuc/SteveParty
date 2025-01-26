package fr.lordfinn.steveparty.screen_handlers;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreensHandlers {
    public static final ScreenHandlerType<TileScreenHandler> TILE_SCREEN_HANDLER =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of(Steveparty.MOD_ID, "tile_screen_handler"),
                    new ScreenHandlerType<>(TileScreenHandler::new, FeatureSet.empty()));
    public static final ScreenHandlerType<RouterScreenHandler> ROUTER_SCREEN_HANDLER =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of(Steveparty.MOD_ID, "router_screen_handler"),
                    new ScreenHandlerType<>(RouterScreenHandler::new, FeatureSet.empty()));
    public static final ScreenHandlerType<CustomizableMerchantScreenHandler> HIDING_TRADER_SCREEN_HANDLER =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of(Steveparty.MOD_ID, "hiding_trader_screen_handler"),
                    new ScreenHandlerType<>(CustomizableMerchantScreenHandler::new, FeatureSet.empty()));
    public static final ScreenHandlerType<CartridgeInventoryScreenHandler> CARTRIDGE_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Steveparty.MOD_ID, "cartridge_screen_handler"),
            new ScreenHandlerType<>(CartridgeInventoryScreenHandler::new, FeatureSet.empty()));

    @SuppressWarnings("EmptyMethod")
    public static void initialize() {
    }
}
