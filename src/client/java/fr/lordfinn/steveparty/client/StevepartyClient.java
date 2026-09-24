package fr.lordfinn.steveparty.client;

import fr.lordfinn.steveparty.client.blockentity.StencilCanvasBlockEntityRenderer;
import fr.lordfinn.steveparty.client.gui.StencilGunHud;
import fr.lordfinn.steveparty.client.model.sign.MaterialSprites;
import fr.lordfinn.steveparty.client.model.sign.StencilSignModelPlugin;
import fr.lordfinn.steveparty.client.screens.StencilGunScreen;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import net.minecraft.block.Block;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.client.blockentity.*;
import fr.lordfinn.steveparty.client.debug.RecipeExporter;
import fr.lordfinn.steveparty.client.entity.HidingTraderEntityRenderer;
import fr.lordfinn.steveparty.client.entity.DiceEntityRenderer;
import fr.lordfinn.steveparty.client.entity.DirectionDisplayRenderer;
import fr.lordfinn.steveparty.client.entity.MulaEntityRenderer;
import fr.lordfinn.steveparty.client.gui.PartyStepsHud;
import fr.lordfinn.steveparty.client.items.StencilItemRenderer;
import fr.lordfinn.steveparty.client.model.ConnectedPlasticModelPlugin;
import fr.lordfinn.steveparty.client.model.TradingStallModelPlugin;
import fr.lordfinn.steveparty.client.particle.ArrowParticle;
import fr.lordfinn.steveparty.client.particle.EnchantedCircularParticle;
import fr.lordfinn.steveparty.client.particle.ForgeBeamParticle;
import fr.lordfinn.steveparty.client.particle.HereParticle;
import fr.lordfinn.steveparty.client.payloads.PayloadReceivers;
import fr.lordfinn.steveparty.client.squish.SquishAnimations;
import fr.lordfinn.steveparty.client.tokenspell.MobTextureColors;
import fr.lordfinn.steveparty.client.tokenspell.TokenSpellPreview;
import fr.lordfinn.steveparty.client.renderer.DestinationsRenderer;
import fr.lordfinn.steveparty.client.renderer.FloatingTextRenderer;
import fr.lordfinn.steveparty.client.renderer.items.TripleJumpShoesRenderer;
import fr.lordfinn.steveparty.client.screens.*;
import fr.lordfinn.steveparty.client.utils.BoardSpaceClientUtils;
import fr.lordfinn.steveparty.client.utils.ConfigurationManager;
import fr.lordfinn.steveparty.components.CarpetColorComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.client.flip.GoalPoleFlipTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentModel;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

import java.util.Map;

import static fr.lordfinn.steveparty.blocks.ModBlocks.*;
import static fr.lordfinn.steveparty.blocks.custom.TradingStallBlock.COLOR1;
import static fr.lordfinn.steveparty.blocks.custom.TradingStallBlock.COLOR2;
import static fr.lordfinn.steveparty.items.ModItems.TRIPLE_JUMP_SHOES;
import static fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers.*;
import static fr.lordfinn.steveparty.utils.MessageUtils.getColorFromText;
import static fr.lordfinn.steveparty.utils.WoolColorsUtils.*;

@SuppressWarnings("unused")
public class StevepartyClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("steveparty");
    public static final PartyStepsHud PARTY_STEPS_HUD = new PartyStepsHud();

    /*
     * Runs on chunk-builder threads: must only READ. The color itself is computed server side
     * (ABoardSpaceBehavior#setColor) and synced through the block entity / UpdateColoredTilePayload.
     */
    private static final BlockColorProvider getTileColor = (state, world, pos, tintIndex) -> {
        if (world == null || pos == null) return 0xFFFFFFFF;
        if (!(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tileEntity)) return 0xFFFFFFFF;
        ItemStack behaviorItemstack = BoardSpaceClientUtils.getDisplayedCartridge(tileEntity);
        if (behaviorItemstack.isEmpty()) return 0xFFFFFFFF;
        return behaviorItemstack.getOrDefault(ModComponents.COLOR, 0xFFFFFFFF);
    };

    private static final BlockColorProvider getTradingStallColor = (state, world, pos, tintIndex) -> {
        int colorValue = getARGBFromDyeColor(DyeColor.WHITE);
        if (world == null || pos == null) return colorValue;
        if (tintIndex == 1) {
            return getARGBFromColorIndex(state.get(COLOR1));
        } else if (tintIndex == 2) {
            return getARGBFromColorIndex(state.get(COLOR2));
        }
        return colorValue;
    };

    private static final ItemColorProvider getTokenIemColor = (stack, tintIndex) -> {
        if (stack == null) return 0xFFFFFFFF;
        Text customName = stack.get(DataComponentTypes.CUSTOM_NAME);
        int color = getColorFromText(customName);
        color = (color & 0x00FFFFFF) | 0xFF000000;
        return color;
    };
    private static final ItemColorProvider getTradingStallItemColor = (stack, tintIndex) -> {
        final int defaultColor = getARGBFromDyeColor(DyeColor.WHITE);
        CarpetColorComponent carpetColorComponent = stack.get(ModComponents.CARPET_COLORS);

        if (carpetColorComponent == null) {
            return defaultColor;
        }

        return switch (tintIndex) {
            case 1 -> getARGBFromDyeColor(carpetColorComponent.color1());
            case 2 -> getARGBFromDyeColor(carpetColorComponent.color2());
            default -> defaultColor;
        };
    };


    @Override
    public void onInitializeClient() {
        ConfigurationManager.loadConfig();
        PayloadReceivers.initialize();
        ModelLoadingPlugin.register(new TradingStallModelPlugin());
        ModelLoadingPlugin.register(new ConnectedPlasticModelPlugin());
        ModelLoadingPlugin.register(new StencilSignModelPlugin());
        StencilResourceManager.registerReloadListener();
        MaterialSprites.registerReloadListener();
        MobTextureColors.registerReloadListener();
        StencilGunHud.initialize();
        SwitchableClient.initialize();
        fr.lordfinn.steveparty.client.token.TokenBaseRenderer.initialize();

        initScreens();
        initParticles();
        initEntitiesRenderers();
        initBlockEntitiesRenderers();
        initItemRenderers();
        DestinationsRenderer.initialize();
        initKeybinds();

        HudRenderCallback.EVENT.register(PARTY_STEPS_HUD);
        PartyStepsHud.registerKeyHandlers();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PartyStepsHud.saveConfigOnExit());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(StevepartyClient::resetClientState));

        initParticleRenderers();

       }

    private void initItemRenderers() {
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.STENCIL, new StencilItemRenderer());
        // Paint can of the stencil gun: colour of the selected dye
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex != 1) return 0xFFFFFFFF;
            StencilGunItem.Load load = StencilGunItem.selectedLoad(stack);
            return load.color() == null ? 0xFF6B6B6B : 0xFF000000 | load.color().getEntityColor();
        }, ModItems.STENCIL_GUN);

        ColorProviderRegistry.ITEM.register(StevepartyClient.getTradingStallItemColor, TRADING_STALL.asItem());
        ColorProviderRegistry.ITEM.register(StevepartyClient.getTokenIemColor, ModItems.TOKEN);
        TRIPLE_JUMP_SHOES.renderProviderHolder.setValue(new GeoRenderProvider() {
            private TripleJumpShoesRenderer renderer;

            @Override
            public <E extends LivingEntity, S extends BipedEntityRenderState>
            BipedEntityModel<?> getGeoArmorRenderer(@Nullable E entity,
                                                    ItemStack stack,
                                                    EquipmentSlot slot,
                                                    EquipmentModel.LayerType type,
                                                    BipedEntityModel<S> original) {
                if (this.renderer == null) {
                    this.renderer = new TripleJumpShoesRenderer();
                }
                return this.renderer;
            }
        });

    }

    private static void initBlockEntitiesRenderers() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CHECK_POINT, RenderLayer.getTranslucent());

        BlockEntityRendererFactories.register(ModBlockEntities.TILE_ENTITY, TileBlockEntityRenderer::new);
        TileBlockEntityRenderer.registerReloadListener();
        BlockEntityRendererFactories.register(ModBlockEntities.BIG_BOOK_ENTITY, TeleportationPadBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.STEP_CONTROLLER_ENTITY, StepControllerBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.TRAFFIC_SIGN_ENTITY, StencilCanvasBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.STENCIL_CANVAS, StencilCanvasBlockEntityRenderer::new);
        // Stencil signs: turned models with see-through texels (stripped logs, pebbles...)
        for (Block sign : new Block[]{OAK_TRAFFIC_SIGN, SPRUCE_TRAFFIC_SIGN, BIRCH_TRAFFIC_SIGN, JUNGLE_TRAFFIC_SIGN, ACACIA_TRAFFIC_SIGN,
                DARK_OAK_TRAFFIC_SIGN, MANGROVE_TRAFFIC_SIGN, CHERRY_TRAFFIC_SIGN, CRIMSON_TRAFFIC_SIGN, WARPED_TRAFFIC_SIGN,
                ModBlocks.TRAFFIC_SIGN, WOODEN_PANEL, WOODEN_CUTOUT_PANEL, ROCK_SIGN, PLASTIC_ROAD_SIGN}) {
            BlockRenderLayerMap.INSTANCE.putBlock(sign, RenderLayer.getCutout());
        }
        BlockEntityRendererFactories.register(ModBlockEntities.STENCIL_MAKER_ENTITY, StencilMakerBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LOOTING_BOX_ENTITY, LootingBoxBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.DICE_FORGE_ENTITY, DiceForgeBlockEntityRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(BLUE_STAR_FRAGMENTS_BLOCK, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(PURPLE_STAR_FRAGMENTS_BLOCK, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(RED_STAR_FRAGMENTS_BLOCK, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(YELLOW_STAR_FRAGMENTS_BLOCK, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(GREEN_STAR_FRAGMENTS_BLOCK, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(BLACK_STAR_FRAGMENTS_BLOCK, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(GRAVITY_CORE, RenderLayer.getTranslucent());

        BlockRenderLayerMap.INSTANCE.putBlock(TRADING_STALL, RenderLayer.getCutout());
        BlockEntityRendererFactories.register(ModBlockEntities.TRADING_STALL, TradingStallBlockEntityRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GOAL_POLE, RenderLayer.getCutout());

        ColorProviderRegistry.BLOCK.register(StevepartyClient.getTileColor, TILE);
        ColorProviderRegistry.BLOCK.register(StevepartyClient.getTradingStallColor, TRADING_STALL);
    }

    private static void initEntitiesRenderers() {
        EntityRendererRegistry.register(ModEntities.DICE_ENTITY, DiceEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.DIRECTION_DISPLAY_ENTITY, DirectionDisplayRenderer::new);
        EntityRendererRegistry.register(ModEntities.HIDING_TRADER_ENTITY, HidingTraderEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.MULA_ENTITY, MulaEntityRenderer::new);
        // The forge core is drawn by the forge: its entity is only a hitbox
        EntityRendererRegistry.register(ModEntities.FORGE_CORE, net.minecraft.client.render.entity.EmptyEntityRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(TRADING_STALL, RenderLayer.getCutout());

    }

    private static void initParticles() {
        ParticleFactoryRegistry.getInstance().register(ModParticles.HERE_PARTICLE, HereParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ARROW_PARTICLE, ArrowParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ENCHANTED_CIRCULAR_PARTICLE, EnchantedCircularParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.FORGE_BEAM, ForgeBeamParticle.Factory::new);
    }

    private static void initScreens() {
        //Initialize HUDs
        PARTY_STEPS_HUD.initialize();
        
        //Initialize Screens
        HandledScreens.register(TILE_SCREEN_HANDLER, BoardSpaceScreen::new);
        HandledScreens.register(ROUTER_SCREEN_HANDLER, RouterScreen::new);
        HandledScreens.register(HOP_SWITCH_SCREEN_HANDLER, HopSwitchScreen::new);
        HandledScreens.register(HIDING_TRADER_SCREEN_HANDLER, HidingTraderScreen::new);
        HandledScreens.register(CARTRIDGE_SCREEN_HANDLER, CartridgeInventoryScreen::new);
        HandledScreens.register(MINI_GAME_PAGE_SCREEN_HANDLER, MiniGamePageScreen::new);
        HandledScreens.register(MINI_GAMES_CATALOGUE_SCREEN_HANDLER, MiniGamesCatalogueScreen::new);
        HandledScreens.register(HERE_WE_GO_BOOK_SCREEN_HANDLER, HereWeGoBookScreen::new);
        HandledScreens.register(HERE_WE_COME_BOOK_SCREEN_HANDLER, HereWeComeBookScreen::new);
        HandledScreens.register(STENCIL_MAKER_SCREEN_HANDLER, StencilMakerScreen::new);
        HandledScreens.register(STENCIL_GUN_SCREEN_HANDLER, StencilGunScreen::new);
        HandledScreens.register(TRADING_STALL_SCREEN_HANDLER, TradingStallScreen::new);
        HandledScreens.register(CASH_REGISTER_SCREEN_HANDLER, CashRegisterScreen::new);
        HandledScreens.register(GOAL_POLE_BASE_SCREEN_HANDLER, GoalPoleBaseScreen::new);
        HandledScreens.register(GOAL_POLE_SCREEN_HANDLER, GoalPoleScreen::new);
        HandledScreens.register(LOOTING_BOX_SCREEN_HANDLER, LootingBoxScreen::new);
        HandledScreens.register(DICE_FORGE_SCREEN_HANDLER, DiceForgeScreen::new);
    }

    private static void initKeybinds() {
        // Recipe export is a developer tool only
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        }
    }


    private static void initParticleRenderers() {
        FloatingTextRenderer.registerRenderCallback();
    }

    /** Client caches are per server connection: drop them on disconnect. */
    private static void resetClientState() {
        PartyService.tokens.clear();
        PartyStepsHud.clearData();
        FloatingTextRenderer.clear();
        GoalPoleFlipTracker.clear();
        SquishAnimations.clear();
        TokenSpellPreview.clear();
    }

    private static boolean lastPressed = false;

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        long window = client.getWindow().getHandle();
        // Raw GLFW read (key bindings are not dispatched while a screen is open)
        boolean isPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_0) == GLFW.GLFW_PRESS;

        if (isPressed && !lastPressed) {
            if (client.currentScreen != null && !isTyping(client.currentScreen)) {
                RecipeExporter.exportRecipe(client);
            }
        }
        lastPressed = isPressed;
    }

    private static boolean isTyping(Element element) {
        if (element instanceof TextFieldWidget textField) return textField.isFocused();
        if (element instanceof ParentElement parent) {
            Element focused = parent.getFocused();
            return focused != null && isTyping(focused);
        }
        return false;
    }

}
