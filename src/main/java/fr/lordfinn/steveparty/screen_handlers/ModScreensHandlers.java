package fr.lordfinn.steveparty.screen_handlers;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.payloads.custom.HopSwitchPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.*;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

public class ModScreensHandlers {

    public static <T extends ScreenHandler, D extends CustomPayload> ExtendedScreenHandlerType<T, D>
    register(String name, ExtendedScreenHandlerType.ExtendedFactory<T, D> factory, PacketCodec<? super RegistryByteBuf, D> codec) {
        return Registry.register(Registries.SCREEN_HANDLER, Steveparty.id(name), new ExtendedScreenHandlerType<>(factory, codec));
    }

    // Overloaded register method for screen handlers with a FeatureSet
    public static <T extends ScreenHandler> ScreenHandlerType<T>
    register(String name, ScreenHandlerType.Factory<T> factory, FeatureSet featureSet) {
        return Registry.register(Registries.SCREEN_HANDLER, Steveparty.id(name), new ScreenHandlerType<>(factory, featureSet));
    }

    // Screen handler registrations
    public static final ExtendedScreenHandlerType<TileScreenHandler, BlockPosPayload> TILE_SCREEN_HANDLER =
            register("tile_screen_handler", TileScreenHandler::new, BlockPosPayload.PACKET_CODEC);

    public static final ExtendedScreenHandlerType<HopSwitchScreenHandler, HopSwitchPayload> HOP_SWITCH_SCREEN_HANDLER =
            register(
                    "hop_switch_screen_handler",
                    HopSwitchScreenHandler::new,
                    HopSwitchPayload.PACKET_CODEC
            );

    public static final ScreenHandlerType<RouterScreenHandler> ROUTER_SCREEN_HANDLER =
            register("router_screen_handler", RouterScreenHandler::new, FeatureSet.empty());

    public static final ScreenHandlerType<CustomizableMerchantScreenHandler> HIDING_TRADER_SCREEN_HANDLER =
            register("hiding_trader_screen_handler", CustomizableMerchantScreenHandler::new, FeatureSet.empty());

    public static final ScreenHandlerType<CartridgeInventoryScreenHandler> CARTRIDGE_SCREEN_HANDLER =
            register("cartridge_screen_handler", CartridgeInventoryScreenHandler::new, FeatureSet.empty());

    public static final ScreenHandlerType<MiniGamePageScreenHandler> MINI_GAME_PAGE_SCREEN_HANDLER =
            register("mini_game_page_screen_handler", MiniGamePageScreenHandler::new, FeatureSet.empty());

    public static final ScreenHandlerType<MiniGamesCatalogueScreenHandler> MINI_GAMES_CATALOGUE_SCREEN_HANDLER =
            register("mini_games_catalogue_screen_handler", MiniGamesCatalogueScreenHandler::new, FeatureSet.empty());

    public static final ScreenHandlerType<HereWeGoBookScreenHandler> HERE_WE_GO_BOOK_SCREEN_HANDLER =
            register("here_we_go_book_screen_handler", HereWeGoBookScreenHandler::new, FeatureSet.empty());

    public static final ScreenHandlerType<HereWeComeBookScreenHandler> HERE_WE_COME_BOOK_SCREEN_HANDLER =
            register("here_we_come_screen_handler", HereWeComeBookScreenHandler::new, FeatureSet.empty());
    public static final ScreenHandlerType<StencilMakerScreenHandler> STENCIL_MAKER_SCREEN_HANDLER =
            register("stencil_maker_screen_handler", StencilMakerScreenHandler::new, BlockPosPayload.PACKET_CODEC);

    public static final ExtendedScreenHandlerType<TradingStallScreenHandler, BlockPosPayload> TRADING_STALL_SCREEN_HANDLER =
            register("trading_stall_screen_handler",
                    (syncId, playerInventory, payload) -> new TradingStallScreenHandler(syncId, playerInventory, payload.pos()),
                    BlockPosPayload.PACKET_CODEC);
    public static final ScreenHandlerType<CashRegisterScreenHandler> CASH_REGISTER_SCREEN_HANDLER = register("cash_register_screen_handler",
            CashRegisterScreenHandler::new, BlockPosPayload.PACKET_CODEC);

    @SuppressWarnings("EmptyMethod")
    public static void initialize() {
    }
}
