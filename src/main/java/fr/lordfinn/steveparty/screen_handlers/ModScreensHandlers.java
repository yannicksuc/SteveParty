package fr.lordfinn.steveparty.screen_handlers;

import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronScreenHandler;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.payloads.custom.GoalPoleBasePayload;
import fr.lordfinn.steveparty.payloads.custom.GoalPolePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.*;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlag;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

public class ModScreensHandlers {
    // Screen handler registrations
    public static final ExtendedScreenHandlerType<BoardSpaceScreenHandler, BlockPosPayload> TILE_SCREEN_HANDLER =
            register("tile_screen_handler", BoardSpaceScreenHandler::new, BlockPosPayload.PACKET_CODEC);

    public static final ExtendedScreenHandlerType<HopSwitchScreenHandler, BlockPosPayload> HOP_SWITCH_SCREEN_HANDLER =
            register(
                    "hop_switch_screen_handler",
                    HopSwitchScreenHandler::new,
                    BlockPosPayload.PACKET_CODEC
            );

    /** A tamed Trichaudron's saddle slot (opening data: its entity id). */
    public static final ExtendedScreenHandlerType<TrichaudronScreenHandler, Integer> TRICHAUDRON_SCREEN_HANDLER =
            Registry.register(Registries.SCREEN_HANDLER, Steveparty.id("trichaudron"),
                    new ExtendedScreenHandlerType<>(TrichaudronScreenHandler::new, PacketCodecs.VAR_INT));

    public static final ScreenHandlerType<RouterScreenHandler> ROUTER_SCREEN_HANDLER =
            register("router_screen_handler", RouterScreenHandler::new, FeatureSet.empty());

    public static final ScreenHandlerType<CustomizableMerchantScreenHandler> BOXED_TRADER_SCREEN_HANDLER =
            register("boxed_trader_screen_handler", CustomizableMerchantScreenHandler::new, FeatureSet.empty());

    /** The merchant's screen opened by a shop stop (with « Buy nothing », the time left and the purchases allowed). */
    public static final ScreenHandlerType<ShopStopScreenHandler> SHOP_STOP_SCREEN_HANDLER =
            register("shop_stop_screen_handler", ShopStopScreenHandler::new, FeatureSet.empty());

    /** A cartridge's menu (opening data: where the cartridge is, see CartridgeMenus). */
    public static final ExtendedScreenHandlerType<CartridgeScreenHandler, CartridgeRef> CARTRIDGE_SCREEN_HANDLER =
            Registry.register(Registries.SCREEN_HANDLER, Steveparty.id("cartridge_screen_handler"),
                    new ExtendedScreenHandlerType<>(CartridgeScreenHandler::new, CartridgeRef.PACKET_CODEC));


    public static final ScreenHandlerType<MiniGamesCatalogueScreenHandler> MINI_GAMES_CATALOGUE_SCREEN_HANDLER =
            register("mini_games_catalogue_screen_handler", MiniGamesCatalogueScreenHandler::new, FeatureSet.empty());


    public static final ScreenHandlerType<StencilMakerScreenHandler> STENCIL_MAKER_SCREEN_HANDLER =
            register("stencil_maker_screen_handler", StencilMakerScreenHandler::new, BlockPosPayload.PACKET_CODEC);

    public static final ExtendedScreenHandlerType<TradingStallScreenHandler, BlockPosPayload> TRADING_STALL_SCREEN_HANDLER =
            register("trading_stall_screen_handler",
                    (syncId, playerInventory, payload) -> new TradingStallScreenHandler(syncId, playerInventory, payload.pos()),
                    BlockPosPayload.PACKET_CODEC);
    public static final ScreenHandlerType<CashRegisterScreenHandler> CASH_REGISTER_SCREEN_HANDLER = register("cash_register_screen_handler",
            CashRegisterScreenHandler::new, BlockPosPayload.PACKET_CODEC);

    public static final ScreenHandlerType<GoalPoleBaseScreenHandler> GOAL_POLE_BASE_SCREEN_HANDLER =
            register("goal_pole_base-screen_handler", GoalPoleBaseScreenHandler::new, GoalPoleBasePayload.CODEC);

    public static final ScreenHandlerType<GoalPoleScreenHandler> GOAL_POLE_SCREEN_HANDLER =
            register("goal_pole_screen_handler", GoalPoleScreenHandler::new, GoalPolePayload.CODEC);

    /** The Party Controller's dashboard (opening data: the controller's position). */
    public static final ExtendedScreenHandlerType<PartyControllerScreenHandler, BlockPosPayload> PARTY_CONTROLLER_SCREEN_HANDLER =
            register("party_controller_screen_handler", PartyControllerScreenHandler::new, BlockPosPayload.PACKET_CODEC);

    /** The Mini-game Controller's screen (opening data: the controller's position). */
    public static final ExtendedScreenHandlerType<MiniGameControllerScreenHandler, BlockPosPayload> MINI_GAME_CONTROLLER_SCREEN_HANDLER =
            register("mini_game_controller_screen_handler", MiniGameControllerScreenHandler::new, BlockPosPayload.PACKET_CODEC);

    public static final ScreenHandlerType<LootingBoxScreenHandler> LOOTING_BOX_SCREEN_HANDLER =
            register("looting_box_screen_handler", LootingBoxScreenHandler::new);
    public static final ScreenHandlerType<DiceForgeScreenHandler> DICE_FORGE_SCREEN_HANDLER = register("dice_forge_screen_handler", DiceForgeScreenHandler::new);

    /** Opening data: the player inventory slot holding the gun. */
    public static final ExtendedScreenHandlerType<StencilGunScreenHandler, Integer> STENCIL_GUN_SCREEN_HANDLER =
            Registry.register(Registries.SCREEN_HANDLER, Steveparty.id("stencil_gun_screen_handler"),
                    new ExtendedScreenHandlerType<>(StencilGunScreenHandler::new, PacketCodecs.VAR_INT.cast()));

    private static <T extends ScreenHandler> ScreenHandlerType<T> register(String id, ScreenHandlerType.Factory<T> factory) {
        return (ScreenHandlerType)Registry.register(Registries.SCREEN_HANDLER, Steveparty.id(id), new ScreenHandlerType(factory, FeatureFlags.VANILLA_FEATURES));
    }

    private static <T extends ScreenHandler> ScreenHandlerType<T> register(String id, ScreenHandlerType.Factory<T> factory, FeatureFlag... requiredFeatures) {
        return (ScreenHandlerType)Registry.register(Registries.SCREEN_HANDLER, Steveparty.id(id), new ScreenHandlerType(factory, FeatureFlags.FEATURE_MANAGER.featureSetOf(requiredFeatures)));
    }

    public static <T extends ScreenHandler, D extends CustomPayload> ExtendedScreenHandlerType<T, D>
    register(String name, ExtendedScreenHandlerType.ExtendedFactory<T, D> factory, PacketCodec<? super RegistryByteBuf, D> codec) {
        return Registry.register(Registries.SCREEN_HANDLER, Steveparty.id(name), new ExtendedScreenHandlerType<>(factory, codec));
    }

    // Overloaded register method for screen handlers with a FeatureSet
    public static <T extends ScreenHandler> ScreenHandlerType<T>
    register(String name, ScreenHandlerType.Factory<T> factory, FeatureSet featureSet) {
        return Registry.register(Registries.SCREEN_HANDLER, Steveparty.id(name), new ScreenHandlerType<>(factory, featureSet));
    }
    public static void initialize() {
    }
}
