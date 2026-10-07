package fr.lordfinn.steveparty.client;

import fr.lordfinn.steveparty.client.blockentity.StencilCanvasBlockEntityRenderer;
import fr.lordfinn.steveparty.client.gui.StencilGunHud;
import fr.lordfinn.steveparty.client.model.sign.MaterialSprites;
import fr.lordfinn.steveparty.client.model.sign.StencilSignModelPlugin;
import fr.lordfinn.steveparty.client.screens.StencilGunScreen;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.flag.FlagPalettes;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import net.minecraft.block.Block;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.client.blockentity.*;
import fr.lordfinn.steveparty.client.debug.RecipeExporter;
import fr.lordfinn.steveparty.client.entity.BoxedTraderEntityRenderer;
import fr.lordfinn.steveparty.client.entity.DiceEntityRenderer;
import fr.lordfinn.steveparty.client.entity.DirectionDisplayRenderer;
import fr.lordfinn.steveparty.client.entity.MulaEntityRenderer;
import fr.lordfinn.steveparty.client.gui.party.PartyHud;
import fr.lordfinn.steveparty.client.items.StencilItemRenderer;
import fr.lordfinn.steveparty.client.model.BrickShadeModelPlugin;
import fr.lordfinn.steveparty.client.model.ConnectedPlasticModelPlugin;
import fr.lordfinn.steveparty.client.model.TradingStallModelPlugin;
import fr.lordfinn.steveparty.client.particle.ArrowParticle;
import fr.lordfinn.steveparty.client.particle.EnchantedCircularParticle;
import fr.lordfinn.steveparty.client.particle.ForgeBeamParticle;
import fr.lordfinn.steveparty.client.particle.HereParticle;
import fr.lordfinn.steveparty.client.particle.KamekShapeParticle;
import fr.lordfinn.steveparty.client.particle.StarFlareParticle;
import fr.lordfinn.steveparty.client.particle.MulaSparkleParticle;
import fr.lordfinn.steveparty.client.payloads.PayloadReceivers;
import fr.lordfinn.steveparty.client.squish.SquishAnimations;
import fr.lordfinn.steveparty.client.tokenspell.MobTextureColors;
import fr.lordfinn.steveparty.client.renderer.DestinationsRenderer;
import fr.lordfinn.steveparty.client.renderer.FloatingTextRenderer;
import fr.lordfinn.steveparty.client.renderer.items.TripleJumpShoesRenderer;
import fr.lordfinn.steveparty.client.screens.*;
import fr.lordfinn.steveparty.client.utils.BoardSpaceClientUtils;
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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

import java.util.Map;

import static fr.lordfinn.steveparty.blocks.ModBlocks.*;
import fr.lordfinn.steveparty.client.utils.TileColors;
import static fr.lordfinn.steveparty.blocks.custom.TradingStallBlock.COLOR1;
import static fr.lordfinn.steveparty.blocks.custom.TradingStallBlock.COLOR2;
import static fr.lordfinn.steveparty.items.ModItems.TRIPLE_JUMP_SHOES;
import static fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers.*;
import static fr.lordfinn.steveparty.utils.MessageUtils.getColorFromText;
import static fr.lordfinn.steveparty.utils.WoolColorsUtils.*;

@SuppressWarnings("unused")
public class StevepartyClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("steveparty");

    /*
     * Runs on chunk-builder threads: must only READ. The color itself is computed server side
     * (ABoardSpaceBehavior#setColor) and synced through the block entity / UpdateColoredTilePayload.
     */
    private static final BlockColorProvider getTileColor = (state, world, pos, tintIndex) -> {
        if (world == null || pos == null) return TileColors.tint(TileColors.WHITE, tintIndex);
        if (!(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tileEntity)) return TileColors.tint(TileColors.WHITE, tintIndex);
        ItemStack behaviorItemstack = BoardSpaceClientUtils.getDisplayedCartridge(tileEntity);
        int color = behaviorItemstack.isEmpty() ? TileColors.WHITE : behaviorItemstack.getOrDefault(ModComponents.COLOR, TileColors.WHITE);
        // Each tinted part of the model takes its own shade of the colour (vivid ramps, see TileColors)
        return TileColors.tint(color, tintIndex);
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
        PayloadReceivers.initialize();
        fr.lordfinn.steveparty.client.telescope.TelescopeClient.initialize();
        ModelLoadingPlugin.register(new TradingStallModelPlugin());
        ModelLoadingPlugin.register(new ConnectedPlasticModelPlugin());
        ModelLoadingPlugin.register(new BrickShadeModelPlugin());
        ModelLoadingPlugin.register(new fr.lordfinn.steveparty.client.model.PolishedTilesItemModel.Plugin());
        ModelLoadingPlugin.register(new StencilSignModelPlugin());
        ModelLoadingPlugin.register(new fr.lordfinn.steveparty.client.pipe.PipeModelPlugin());
        fr.lordfinn.steveparty.client.pipe.PipeBulgeRenderer.register();
        BlockEntityRendererFactories.register(ModBlockEntities.MINIGAME_PIPE_ENTITY, fr.lordfinn.steveparty.client.pipe.MiniGamePipeNotchRenderer::new);
        fr.lordfinn.steveparty.client.pipe.MiniGamePipeNotchRenderer.registerHint();
        // What travels inside shows through glass pipes (like glass and stained glass) and windowed ones
        for (fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind kind : fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.values()) {
            RenderLayer layer = kind == fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.STAINED_GLASS ? RenderLayer.getTranslucent()
                    : kind == fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind.OPAQUE || kind.isMiniGame() ? null
                    : RenderLayer.getCutout();
            if (layer != null) for (net.minecraft.block.Block pipe : ModBlocks.PIPES[kind.ordinal()]) BlockRenderLayerMap.INSTANCE.putBlock(pipe, layer);
        }
        StencilResourceManager.registerReloadListener();
        MaterialSprites.registerReloadListener();
        MobTextureColors.registerReloadListener();
        FlagPalettes.registerReloadListener();
        StencilGunHud.initialize();
        fr.lordfinn.steveparty.client.board.WrenchClient.initialize();
        fr.lordfinn.steveparty.client.hammer.StencilHammerStrikes.initialize();
        SwitchableClient.initialize();
        fr.lordfinn.steveparty.client.token.TokenBaseRenderer.initialize();
        fr.lordfinn.steveparty.client.entity.costume.BoxCostumeClient.initialize();

        initScreens();
        initParticles();
        initEntitiesRenderers();
        initBlockEntitiesRenderers();
        initItemRenderers();
        DestinationsRenderer.initialize();
        initKeybinds();

        PartyHud.initialize();
        fr.lordfinn.steveparty.client.minigame.MiniGamePageClient.initialize();
        fr.lordfinn.steveparty.client.gui.party.MiniGameCardHud.initialize();
        fr.lordfinn.steveparty.client.gui.party.MiniGameResultsHud.initialize();
        fr.lordfinn.steveparty.client.gui.party.MiniGamePracticeHud.initialize();
        fr.lordfinn.steveparty.client.minigame.PageZoneClient.initialize();
        fr.lordfinn.steveparty.client.items.InventoryCartridgeClient.initialize();
        fr.lordfinn.steveparty.client.items.BandanaArmorRenderer.register();
        fr.lordfinn.steveparty.client.gui.MiniGamePageTooltipComponent.register();
        fr.lordfinn.steveparty.client.gui.TitleScreenBackground.initialize();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(StevepartyClient::resetClientState));

        initParticleRenderers();
        fr.lordfinn.steveparty.client.entity.DeferredGlows.initialize();

       }

    private void initItemRenderers() {
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.STENCIL, new StencilItemRenderer());
        // Paint can of the stencil gun: colour of the selected dye
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex != 1) return 0xFFFFFFFF;
            StencilGunItem.Load load = StencilGunItem.selectedLoad(stack);
            return load.color() == null ? 0xFF6B6B6B : 0xFF000000 | load.color().getEntityColor();
        }, ModItems.STENCIL_GUN);

        // Tiles: their size shows in hand and in the GUI (0 standard, 0.5 small: a 1x1 tile, 1 large: 4 faces in a 2x2)
        for (net.minecraft.item.Item tile : java.util.List.of(ModBlocks.TILE.asItem(), ModBlocks.ADVANCED_TILE.asItem())) {
            net.minecraft.client.item.ModelPredicateProviderRegistry.register(tile, Steveparty.id("tile_size"),
                    (stack, world, entity, seed) -> switch (fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize.of(stack)) {
                        case STANDARD -> 0f;
                        case SMALL -> 0.5f;
                        case LARGE -> 1f;
                    });
        }
        // Creative pick block with Ctrl (vanilla) or Shift on a tile or a large tile's part: a copy with its cartridges
        fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents.pickWithContents = () -> {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            return client.player != null && client.player.isCreative()
                    && (net.minecraft.client.gui.screen.Screen.hasControlDown() || net.minecraft.client.gui.screen.Screen.hasShiftDown());
        };
        // Bandana: one icon per colour (0, 0.25, ... 1: teal, blue, pink, orange, yellow)
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(ModItems.BANDANA, Steveparty.id("bandana_color"),
                (stack, world, entity, seed) -> fr.lordfinn.steveparty.items.custom.BandanaItem.getColor(stack) / 4f);
        // Teleport Cartridge: its label in the colour of its network (0, 0.25, 0.5, 0.75: violet, green, orange, blue)
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(ModItems.TELEPORT_CARTRIDGE, Steveparty.id("teleport_network"),
                (stack, world, entity, seed) -> fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem.settings(stack).network().ordinal() / 4f);
        // Dyed flag: its own model, one white layer per shading level (and the stick, untinted), each level tinted
        // with the colour of the dye's wool (FlagPalettes)
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(ModItems.FLAG, Steveparty.id("dyed"),
                (stack, world, entity, seed) -> FlagItem.getColor(stack) == FlagItem.NO_COLOR ? 0f : 1f);
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            int color = FlagItem.getColor(stack);
            if (color == FlagItem.NO_COLOR || tintIndex < 0 || tintIndex >= FlagPalettes.LEVELS) return 0xFFFFFFFF;
            return 0xFF000000 | FlagPalettes.ramp(color)[tintIndex];
        }, ModItems.FLAG);

        ColorProviderRegistry.ITEM.register(StevepartyClient.getTradingStallItemColor, TRADING_STALL.asItem());
        ColorProviderRegistry.ITEM.register(StevepartyClient.getTokenIemColor, ModItems.TOKEN);
        // The Mula egg is drawn (textures/item/mula_spawn_egg.png): no spawn-egg tint over it
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> 0xFFFFFFFF, ModItems.MULA_SPAWN_EGG);
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> 0xFFFFFFFF, ModItems.BOXED_TRADER_SPAWN_EGG);
        TRIPLE_JUMP_SHOES.renderProviderHolder.setValue(new GeoRenderProvider() {
            private TripleJumpShoesRenderer renderer;

            @Override
            public <E extends LivingEntity>
            BipedEntityModel<?> getGeoArmorRenderer(@Nullable E entity,
                                                    ItemStack stack,
                                                    EquipmentSlot slot,
                                                    BipedEntityModel<E> original) {
                if (this.renderer == null) {
                    this.renderer = new TripleJumpShoesRenderer();
                }
                return this.renderer;
            }
        });

    }

    private static void initBlockEntitiesRenderers() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CHECK_POINT, RenderLayer.getTranslucent());
        // Cut out: the start tile's top is made of value layers (see TileColors#tint)
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.ADVANCED_TILE, ModBlocks.TILE);

        BlockEntityRendererFactories.register(ModBlockEntities.ADVANCED_TILE_ENTITY, TileBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.TILE_ENTITY, TileBlockEntityRenderer::new);
        fr.lordfinn.steveparty.client.gui.TileStampTooltipComponent.register();
        TileBlockEntityRenderer.registerReloadListener();
        BlockEntityRendererFactories.register(ModBlockEntities.STEP_CONTROLLER_ENTITY, StepControllerBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.EASEL_SIGN_ENTITY, StencilCanvasBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.STENCIL_CANVAS, StencilCanvasBlockEntityRenderer::new);
        // Stencil signs: turned models with see-through texels (stripped logs, pebbles...)
        for (Block sign : new Block[]{OAK_EASEL_SIGN, SPRUCE_EASEL_SIGN, BIRCH_EASEL_SIGN, JUNGLE_EASEL_SIGN, ACACIA_EASEL_SIGN,
                DARK_OAK_EASEL_SIGN, MANGROVE_EASEL_SIGN, CHERRY_EASEL_SIGN, CRIMSON_EASEL_SIGN, WARPED_EASEL_SIGN,
                ModBlocks.EASEL_SIGN, WOODEN_PANEL, WOODEN_CUTOUT_PANEL, ROCK_SIGN, PLASTIC_ROAD_SIGN}) {
            BlockRenderLayerMap.INSTANCE.putBlock(sign, RenderLayer.getCutout());
        }
        BlockEntityRendererFactories.register(ModBlockEntities.STENCIL_MAKER_ENTITY, StencilMakerBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LOOTING_BOX_ENTITY, LootingBoxBlockEntityRenderer::new);
        // The villager block is drawn alive (reactions, looking around) by its renderer, not as a baked block
        BlockEntityRendererFactories.register(ModBlockEntities.VILLAGER_BLOCK_ENTITY, fr.lordfinn.steveparty.client.blockentity.VillagerBlockEntityRenderer::new);
        // It cries while it is being broken: the breaking progress of any player, as the world renderer knows it
        fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEntity.miningStageProbe = pos -> {
            var renderer = net.minecraft.client.MinecraftClient.getInstance().worldRenderer;
            if (renderer == null) return -1;
            var infos = ((fr.lordfinn.steveparty.client.mixin.WorldRendererBreakingAccessor) renderer)
                    .steveparty$getBlockBreakingProgressions().get(pos.asLong());
            return infos == null || infos.isEmpty() ? -1 : infos.last().getStage();
        };
        BlockEntityRendererFactories.register(ModBlockEntities.DICE_FORGE_ENTITY, DiceForgeBlockEntityRenderer::new);
        // The figure of the player registered on a podium, and the pattern tagged on its banner
        BlockEntityRendererFactories.register(ModBlockEntities.PODIUM_ENTITY, fr.lordfinn.steveparty.client.blockentity.PodiumRenderer::new);
        fr.lordfinn.steveparty.client.blockentity.PodiumRenderer.registerReloadListener();

        for (net.minecraft.block.Block starFragments : STAR_FRAGMENTS_BLOCKS) {
            BlockRenderLayerMap.INSTANCE.putBlock(starFragments, RenderLayer.getTranslucent());
        }
        BlockRenderLayerMap.INSTANCE.putBlock(GRAVITY_CORE, RenderLayer.getTranslucent());

        BlockRenderLayerMap.INSTANCE.putBlock(TRADING_STALL, RenderLayer.getCutout());
        BlockEntityRendererFactories.register(ModBlockEntities.TRADING_STALL, TradingStallBlockEntityRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GOAL_POLE, RenderLayer.getCutout());
        // The flag is drawn by the block entity renderer (it ripples in the wind); the baked model is the pole only
        BlockEntityRendererFactories.register(ModBlockEntities.GOAL_POLE_ENTITY, GoalPoleFlagRenderer::new);
        // With a wrench in hand: what each port of the base does, next to it
        BlockEntityRendererFactories.register(ModBlockEntities.GOAL_POLE_BASE_ENTITY, GoalPoleBaseRenderer::new);

        ColorProviderRegistry.BLOCK.register(StevepartyClient.getTileColor, TILE, ADVANCED_TILE);
        ColorProviderRegistry.BLOCK.register(StevepartyClient.getTradingStallColor, TRADING_STALL);
    }

    private static void initEntitiesRenderers() {
        EntityRendererRegistry.register(ModEntities.DICE_ENTITY, DiceEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.DIRECTION_DISPLAY_ENTITY, DirectionDisplayRenderer::new);
        EntityRendererRegistry.register(ModEntities.BOXED_TRADER_ENTITY, BoxedTraderEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.MULA_ENTITY, MulaEntityRenderer::new);
        // The forge core is drawn by the forge: its entity is only a hitbox
        EntityRendererRegistry.register(ModEntities.FORGE_CORE, net.minecraft.client.render.entity.EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PIPE_CARRIER, net.minecraft.client.render.entity.EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.MULA_STAR, fr.lordfinn.steveparty.client.entity.MulaStarRenderer::new);
        fr.lordfinn.steveparty.client.entity.MulaFoodTooltip.register();
        BlockRenderLayerMap.INSTANCE.putBlock(TRADING_STALL, RenderLayer.getCutout());

    }

    private static void initParticles() {
        ParticleFactoryRegistry.getInstance().register(ModParticles.HERE_PARTICLE, HereParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ARROW_PARTICLE, ArrowParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ENCHANTED_CIRCULAR_PARTICLE, EnchantedCircularParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.FORGE_BEAM, ForgeBeamParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.MULA_SPARKLE, MulaSparkleParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.KAMEK_SHAPE, KamekShapeParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.STAR_FLARE, StarFlareParticle.Factory::new);
    }

    private static void initScreens() {
        //Initialize HUDs
        
        //Initialize Screens
        HandledScreens.register(TILE_SCREEN_HANDLER, BoardSpaceScreen::new);
        HandledScreens.register(ROUTER_SCREEN_HANDLER, RouterScreen::new);
        HandledScreens.register(HOP_SWITCH_SCREEN_HANDLER, HopSwitchScreen::new);
        HandledScreens.register(BOXED_TRADER_SCREEN_HANDLER, BoxedTraderScreen::new);
        HandledScreens.<net.minecraft.screen.MerchantScreenHandler, fr.lordfinn.steveparty.client.screens.ShopStopScreen>register(
                fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers.SHOP_STOP_SCREEN_HANDLER,
                (handler, inventory, title) -> new fr.lordfinn.steveparty.client.screens.ShopStopScreen(
                        (fr.lordfinn.steveparty.screen_handlers.custom.ShopStopScreenHandler) handler, inventory, title));
        HandledScreens.register(CARTRIDGE_SCREEN_HANDLER, fr.lordfinn.steveparty.client.screens.CartridgeScreen::new);
        HandledScreens.register(MINI_GAMES_CATALOGUE_SCREEN_HANDLER, MiniGamesCatalogueScreen::new);
        HandledScreens.register(STENCIL_MAKER_SCREEN_HANDLER, StencilMakerScreen::new);
        HandledScreens.register(STENCIL_GUN_SCREEN_HANDLER, StencilGunScreen::new);
        HandledScreens.register(TRADING_STALL_SCREEN_HANDLER, TradingStallScreen::new);
        HandledScreens.register(CASH_REGISTER_SCREEN_HANDLER, CashRegisterScreen::new);
        HandledScreens.register(GOAL_POLE_BASE_SCREEN_HANDLER, GoalPoleBaseScreen::new);
        HandledScreens.register(GOAL_POLE_SCREEN_HANDLER, GoalPoleScreen::new);
        HandledScreens.register(LOOTING_BOX_SCREEN_HANDLER, LootingBoxScreen::new);
        HandledScreens.register(DICE_FORGE_SCREEN_HANDLER, DiceForgeScreen::new);
        HandledScreens.register(PARTY_CONTROLLER_SCREEN_HANDLER, PartyControllerScreen::new);
        HandledScreens.register(MINI_GAME_CONTROLLER_SCREEN_HANDLER, fr.lordfinn.steveparty.client.screens.MiniGameControllerScreen::new);
    }

    private static void initKeybinds() {
        // Recipe export is a developer tool only
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        }
    }


    private static void initParticleRenderers() {
        FloatingTextRenderer.registerRenderCallback();
        fr.lordfinn.steveparty.client.renderer.StarSpaceRenderer.initialize();
    }

    /** Client caches are per server connection: drop them on disconnect. */
    private static void resetClientState() {
        PartyHud.clear();
        fr.lordfinn.steveparty.client.minigame.MiniGamePageClient.clear();
        FloatingTextRenderer.clear();
        fr.lordfinn.steveparty.client.renderer.StarSpaceRenderer.clear();
        GoalPoleFlipTracker.clear();
        fr.lordfinn.steveparty.client.flip.GoalPoleCameraRoll.clear();
        SquishAnimations.clear();
        fr.lordfinn.steveparty.client.renderer.DestinationsRenderer.clear();
        // The carriers of the last world, else kept (with their world) until a world renders again
        fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity.CLIENT_CARRIERS.clear();
    }

    private static boolean lastPressed = false;

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!client.isPaused()) SquishAnimations.tick(client.world);

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
