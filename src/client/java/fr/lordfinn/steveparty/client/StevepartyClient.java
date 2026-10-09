package fr.lordfinn.steveparty.client;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEntity;
import fr.lordfinn.steveparty.client.blockentity.FrousseuxCandleHolderRenderer;
import fr.lordfinn.steveparty.client.blockentity.PodiumRenderer;
import fr.lordfinn.steveparty.client.blockentity.StencilCanvasBlockEntityRenderer;
import fr.lordfinn.steveparty.client.blockentity.VillagerBlockEntityRenderer;
import fr.lordfinn.steveparty.client.board.BrushClient;
import fr.lordfinn.steveparty.client.board.ChevronSprites;
import fr.lordfinn.steveparty.client.board.TileOutline;
import fr.lordfinn.steveparty.client.config.ClientOptions;
import fr.lordfinn.steveparty.client.entity.BoomcartRenderer;
import fr.lordfinn.steveparty.client.entity.DeferredGlows;
import fr.lordfinn.steveparty.client.entity.FrousseuxRenderer;
import fr.lordfinn.steveparty.client.entity.TrichaudronRenderer;
import fr.lordfinn.steveparty.client.entity.TrichaudronRiderClient;
import fr.lordfinn.steveparty.client.entity.GlandouilleCarryClient;
import fr.lordfinn.steveparty.client.entity.GlandouilleRenderer;
import fr.lordfinn.steveparty.client.entity.MagpieRenderer;
import fr.lordfinn.steveparty.client.entity.WildMagpieRenderer;
import fr.lordfinn.steveparty.client.entity.MistigriDieRenderer;
import fr.lordfinn.steveparty.client.entity.MistigriRenderer;
import fr.lordfinn.steveparty.client.entity.MulaFoodTooltip;
import fr.lordfinn.steveparty.client.entity.MulaStarRenderer;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeClient;
import fr.lordfinn.steveparty.client.flip.GoalPoleCameraRoll;
import fr.lordfinn.steveparty.client.gui.MiniGamePageTooltipComponent;
import fr.lordfinn.steveparty.client.gui.StencilGunHud;
import fr.lordfinn.steveparty.client.gui.TileStampTooltipComponent;
import fr.lordfinn.steveparty.client.gui.TitleScreenBackground;
import fr.lordfinn.steveparty.client.gui.party.DiceRevealHud;
import fr.lordfinn.steveparty.client.gui.party.MiniGameCardHud;
import fr.lordfinn.steveparty.client.gui.party.MiniGamePracticeHud;
import fr.lordfinn.steveparty.client.gui.party.MiniGameResultsHud;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import fr.lordfinn.steveparty.client.hammer.StencilHammerStrikes;
import fr.lordfinn.steveparty.client.items.BandanaArmorRenderer;
import fr.lordfinn.steveparty.client.items.CartridgeClickGuard;
import fr.lordfinn.steveparty.client.items.ExplorerHelmetRenderer;
import fr.lordfinn.steveparty.client.items.InventoryCartridgeClient;
import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.client.minigame.PageZoneClient;
import fr.lordfinn.steveparty.client.mixin.WorldRendererBreakingAccessor;
import fr.lordfinn.steveparty.client.model.PolishedTilesItemModel;
import fr.lordfinn.steveparty.client.model.sign.MaterialSprites;
import fr.lordfinn.steveparty.client.model.sign.StencilSignModelPlugin;
import fr.lordfinn.steveparty.client.particle.ThermalSteamParticle;
import fr.lordfinn.steveparty.client.pawn.PawnPossessionClient;
import fr.lordfinn.steveparty.client.pawn.PlayerPawnRenderer;
import fr.lordfinn.steveparty.client.pipe.MiniGamePipeNotchRenderer;
import fr.lordfinn.steveparty.client.pipe.PipeBulgeRenderer;
import fr.lordfinn.steveparty.client.pipe.PipeModelPlugin;
import fr.lordfinn.steveparty.client.renderer.StarSpaceRenderer;
import fr.lordfinn.steveparty.client.board.TileInfoClient;
import fr.lordfinn.steveparty.client.screens.CartridgeScreen;
import fr.lordfinn.steveparty.client.screens.TrichaudronScreen;
import fr.lordfinn.steveparty.client.screens.MiniGameControllerScreen;
import fr.lordfinn.steveparty.client.screens.ShopStopScreen;
import fr.lordfinn.steveparty.client.screens.StencilGunScreen;
import fr.lordfinn.steveparty.client.telescope.TelescopeClient;
import fr.lordfinn.steveparty.client.token.PawnPoseFeedback;
import fr.lordfinn.steveparty.client.token.TokenBaseRenderer;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.flag.FlagPalettes;
import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxCompanion;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.items.custom.BandanaItem;
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
import fr.lordfinn.steveparty.client.items.DiceItemRenderer;
import fr.lordfinn.steveparty.client.items.StencilItemRenderer;
import fr.lordfinn.steveparty.client.model.BrickShadeModelPlugin;
import fr.lordfinn.steveparty.client.model.ConnectedPlasticModelPlugin;
import fr.lordfinn.steveparty.client.model.TradingStallModelPlugin;
import fr.lordfinn.steveparty.client.particle.ArrowParticle;
import fr.lordfinn.steveparty.client.particle.EnchantedCircularParticle;
import fr.lordfinn.steveparty.client.particle.ForgeBeamParticle;
import fr.lordfinn.steveparty.client.particle.HereParticle;
import fr.lordfinn.steveparty.client.particle.MagicShapeParticle;
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
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.client.flip.GoalPoleFlipTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;

import java.util.List;

import static fr.lordfinn.steveparty.blocks.ModBlocks.*;
import fr.lordfinn.steveparty.client.utils.TileColors;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.screen_handlers.custom.ShopStopScreenHandler;
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

    private static final ItemColorProvider getTokenItemColor = (stack, tintIndex) -> {
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
        ClientOptions.initialize();
        TelescopeClient.initialize();
        ModelLoadingPlugin.register(new TradingStallModelPlugin());
        ModelLoadingPlugin.register(new ConnectedPlasticModelPlugin());
        ModelLoadingPlugin.register(new BrickShadeModelPlugin());
        ModelLoadingPlugin.register(new PolishedTilesItemModel.Plugin());
        ModelLoadingPlugin.register(new StencilSignModelPlugin());
        ModelLoadingPlugin.register(new PipeModelPlugin());
        PipeBulgeRenderer.register();
        BlockEntityRendererFactories.register(ModBlockEntities.MINIGAME_PIPE_ENTITY, MiniGamePipeNotchRenderer::new);
        MiniGamePipeNotchRenderer.registerHint();
        // What travels inside shows through glass pipes (like glass and stained glass) and windowed ones
        for (PipeKind kind : PipeKind.values()) {
            RenderLayer layer = kind == PipeKind.STAINED_GLASS ? RenderLayer.getTranslucent()
                    : kind == PipeKind.OPAQUE || kind.isMiniGame() ? null
                    : RenderLayer.getCutout();
            if (layer != null) for (Block pipe : ModBlocks.PIPES[kind.ordinal()]) BlockRenderLayerMap.INSTANCE.putBlock(pipe, layer);
        }
        StencilResourceManager.registerReloadListener();
        MaterialSprites.registerReloadListener();
        MobTextureColors.registerReloadListener();
        FlagPalettes.registerReloadListener();
        ChevronSprites.registerReloadListener();
        StencilGunHud.initialize();
        ToolWheel.initialize();
        GlandouilleCarryClient.initialize();
        TrichaudronRiderClient.initialize();
        BrushClient.initialize();
        TileInfoClient.initialize();
        TileOutline.initialize();
        DevClientCommands.initialize();
        StencilHammerStrikes.initialize();
        SwitchableClient.initialize();
        TokenBaseRenderer.initialize();
        BoxCostumeClient.initialize();

        initScreens();
        initParticles();
        initEntitiesRenderers();
        initBlockEntitiesRenderers();
        initItemRenderers();
        DestinationsRenderer.initialize();
        initKeybinds();

        PartyHud.initialize();
        DiceRevealHud.initialize();
        MiniGamePageClient.initialize();
        MiniGameCardHud.initialize();
        MiniGameResultsHud.initialize();
        MiniGamePracticeHud.initialize();
        PageZoneClient.initialize();
        InventoryCartridgeClient.initialize();
        CartridgeClickGuard.initialize();
        BandanaArmorRenderer.register();
        ExplorerHelmetRenderer.register();
        MiniGamePageTooltipComponent.register();
        Tooltips.setShiftProbe(net.minecraft.client.gui.screen.Screen::hasShiftDown);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) ->
                fr.lordfinn.steveparty.items.tooltip.ItemTips.append(stack, lines));
        TitleScreenBackground.initialize();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(StevepartyClient::resetClientState));

        initParticleRenderers();
        DeferredGlows.initialize();

       }

    private void initItemRenderers() {
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.STENCIL, new StencilItemRenderer());
        // Dice items: dice rolling over through the faces of the die (one, two or three as the models lay them out)
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.DEFAULT_DICE, DiceItemRenderer.single());
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.DOUBLE_DICE, DiceItemRenderer.pair());
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.TRIPLE_DICE, DiceItemRenderer.trio());
        // Paint can of the stencil gun: colour of the selected dye
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex != 1) return 0xFFFFFFFF;
            StencilGunItem.Load load = StencilGunItem.selectedLoad(stack);
            return load.color() == null ? 0xFF6B6B6B : 0xFF000000 | load.color().getEntityColor();
        }, ModItems.STENCIL_GUN);

        // Tiles: their size shows in hand and in the GUI (0 standard, 0.5 small: a 1x1 tile, 1 large: 4 faces in a 2x2)
        for (Item tile : List.of(ModBlocks.TILE.asItem(), ModBlocks.ADVANCED_TILE.asItem())) {
            ModelPredicateProviderRegistry.register(tile, Steveparty.id("tile_size"),
                    (stack, world, entity, seed) -> switch (TileSize.of(stack)) {
                        case STANDARD -> 0f;
                        case SMALL -> 0.5f;
                        case LARGE -> 1f;
                    });
        }
        // Creative pick block with Ctrl (vanilla) or Shift on a tile or a large tile's part: a copy with its cartridges
        TileContents.pickWithContents = () -> {
            MinecraftClient client = MinecraftClient.getInstance();
            return client.player != null && client.player.isCreative()
                    && (Screen.hasControlDown() || Screen.hasShiftDown());
        };
        // Bandana: one icon per colour (0, 0.25, ... 1: teal, blue, pink, orange, yellow)
        ModelPredicateProviderRegistry.register(ModItems.BANDANA, Steveparty.id("bandana_color"),
                (stack, world, entity, seed) -> BandanaItem.getColor(stack) / 4f);
        // Teleport Cartridge: its label in the colour of its network (0, 0.25, 0.5, 0.75: violet, green, orange, blue)
        ModelPredicateProviderRegistry.register(ModItems.TELEPORT_CARTRIDGE, Steveparty.id("teleport_network"),
                (stack, world, entity, seed) -> TeleportCartridgeItem.settings(stack).network().ordinal() / 4f);
        // Dyed flag: its own model, one white layer per shading level (and the stick, untinted), each level tinted
        // with the colour of the dye's wool (FlagPalettes)
        ModelPredicateProviderRegistry.register(ModItems.FLAG, Steveparty.id("dyed"),
                (stack, world, entity, seed) -> FlagItem.getColor(stack) == FlagItem.NO_COLOR ? 0f : 1f);
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            int color = FlagItem.getColor(stack);
            if (color == FlagItem.NO_COLOR || tintIndex < 0 || tintIndex >= FlagPalettes.LEVELS) return 0xFFFFFFFF;
            return 0xFF000000 | FlagPalettes.ramp(color)[tintIndex];
        }, ModItems.FLAG);

        // The Stencil Hammer's paint gauge: the colour of the dye it sprays, glass (empty) without one
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex != 0) return 0xFFFFFFFF;
            DyeColor dye = StencilGunItem.selectedLoad(stack).color();
            return dye == null ? 0xFFC8F2FF : 0xFF000000 | dye.getEntityColor();
        }, ModItems.STENCIL_GUN);
        ColorProviderRegistry.ITEM.register(StevepartyClient.getTradingStallItemColor, TRADING_STALL.asItem());
        ColorProviderRegistry.ITEM.register(StevepartyClient.getTokenItemColor, ModItems.TOKEN);
        // Every Steve Party egg is drawn per mob (textures/item/*_spawn_egg.png, 1.21.5 style): item/generated gives
        // layer0 tint index 0, so the vanilla spawn-egg tint is replaced by a white one (ModItems.spawnEgg lists them)
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> 0xFFFFFFFF, ModItems.SPAWN_EGGS.toArray(Item[]::new));
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
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ACORN_CROP, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.MAGPIE_NEST, RenderLayer.getCutout());

        BlockEntityRendererFactories.register(ModBlockEntities.ADVANCED_TILE_ENTITY, TileBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.TILE_ENTITY, TileBlockEntityRenderer::new);
        // A check point has no face: only what a board rule cartridge shows over it (a Threshold obstacle's condition...)
        BlockEntityRendererFactories.register(ModBlockEntities.CHECK_POINT_ENTITY, context -> BoardRuleOverlays::renderCheckPoint);
        TileStampTooltipComponent.register();
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
        BlockEntityRendererFactories.register(ModBlockEntities.VILLAGER_BLOCK_ENTITY, VillagerBlockEntityRenderer::new);
        // A Frousseux asleep as a candle holder: drawn whole by its block entity renderer (16 ways, its flame); its
        // item, the same
        BlockEntityRendererFactories.register(ModBlockEntities.FROUSSEUX_CANDLE_HOLDER, FrousseuxCandleHolderRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.FROUSSEUX_CANDLE_HOLDER,
                FrousseuxCandleHolderRenderer.ITEM);
        // It cries while it is being broken: the breaking progress of any player, as the world renderer knows it
        VillagerBlockEntity.miningStageProbe = pos -> {
            var renderer = MinecraftClient.getInstance().worldRenderer;
            if (renderer == null) return -1;
            var infos = ((WorldRendererBreakingAccessor) renderer)
                    .steveparty$getBlockBreakingProgressions().get(pos.asLong());
            return infos == null || infos.isEmpty() ? -1 : infos.last().getStage();
        };
        BlockEntityRendererFactories.register(ModBlockEntities.DICE_FORGE_ENTITY, DiceForgeBlockEntityRenderer::new);
        // The figure of the player registered on a podium, and the pattern tagged on its banner
        BlockEntityRendererFactories.register(ModBlockEntities.PODIUM_ENTITY, PodiumRenderer::new);
        PodiumRenderer.registerReloadListener();

        for (Block starFragments : STAR_FRAGMENTS_BLOCKS) {
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
        EntityRendererRegistry.register(ModEntities.GLANDOUILLE, GlandouilleRenderer::new);
        EntityRendererRegistry.register(ModEntities.FROUSSEUX, FrousseuxRenderer::new);
        EntityRendererRegistry.register(ModEntities.TRICHAUDRON, TrichaudronRenderer::new);
        EntityRendererRegistry.register(ModEntities.BOOMCART, BoomcartRenderer::new);
        EntityRendererRegistry.register(ModEntities.MISTIGRI, MistigriRenderer::new);
        EntityRendererRegistry.register(ModEntities.MISTIGRI_DIE, MistigriDieRenderer::new);
        EntityRendererRegistry.register(ModEntities.MAGPIE, MagpieRenderer::new);
        EntityRendererRegistry.register(ModEntities.WILD_MAGPIE, WildMagpieRenderer::new);
        EntityRendererRegistry.register(ModEntities.PLAYER_PAWN, PlayerPawnRenderer::new);
        PawnPossessionClient.initialize();
        PawnPoseFeedback.initialize();
        // its owner's crosshair goes through a following Frousseux, unless they reach for it
        FrousseuxEntity.CLIENT_PASS_THROUGH = frousseux -> {
            PlayerEntity player = MinecraftClient.getInstance().player;
            return player != null && frousseux.isOwner(player)
                    && !FrousseuxCompanion.reachesFor(player);
        };
        // ... and fades out of their way when close to their camera (the owner's view only)
        FrousseuxEntity.CLIENT_FADE = frousseux -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.gameRenderer == null) return 0;
            return FrousseuxCompanion.fadeFor(frousseux, client.player,
                    client.gameRenderer.getCamera().getPos());
        };
        // The forge core is drawn by the forge: its entity is only a hitbox
        EntityRendererRegistry.register(ModEntities.FORGE_CORE, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.PIPE_CARRIER, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.MULA_STAR, MulaStarRenderer::new);
        MulaFoodTooltip.register();
        BlockRenderLayerMap.INSTANCE.putBlock(TRADING_STALL, RenderLayer.getCutout());

    }

    private static void initParticles() {
        ParticleFactoryRegistry.getInstance().register(ModParticles.HERE_PARTICLE, HereParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ARROW_PARTICLE, ArrowParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ENCHANTED_CIRCULAR_PARTICLE, EnchantedCircularParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.FORGE_BEAM, ForgeBeamParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.MULA_SPARKLE, MulaSparkleParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.MAGIC_SHAPE, MagicShapeParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.STAR_FLARE, StarFlareParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.THERMAL_PLUME, ThermalSteamParticle::plume);
        ParticleFactoryRegistry.getInstance().register(ModParticles.THERMAL_BASE, ThermalSteamParticle::base);
        ParticleFactoryRegistry.getInstance().register(ModParticles.THERMAL_POOF, ThermalSteamParticle::poof);
    }

    private static void initScreens() {
        //Initialize HUDs
        
        //Initialize Screens
        HandledScreens.register(TILE_SCREEN_HANDLER, BoardSpaceScreen::new);
        HandledScreens.register(ModScreensHandlers.TRICHAUDRON_SCREEN_HANDLER,
                TrichaudronScreen::new);
        HandledScreens.register(ROUTER_SCREEN_HANDLER, RouterScreen::new);
        HandledScreens.register(HOP_SWITCH_SCREEN_HANDLER, HopSwitchScreen::new);
        HandledScreens.register(BOXED_TRADER_SCREEN_HANDLER, BoxedTraderScreen::new);
        HandledScreens.<MerchantScreenHandler, ShopStopScreen>register(
                ModScreensHandlers.SHOP_STOP_SCREEN_HANDLER,
                (handler, inventory, title) -> new ShopStopScreen(
                        (ShopStopScreenHandler) handler, inventory, title));
        HandledScreens.register(CARTRIDGE_SCREEN_HANDLER, CartridgeScreen::new);
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
        HandledScreens.register(MINI_GAME_CONTROLLER_SCREEN_HANDLER, MiniGameControllerScreen::new);
    }

    private static void initKeybinds() {
        // Recipe export is a developer tool only
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        }
    }


    private static void initParticleRenderers() {
        FloatingTextRenderer.registerRenderCallback();
        StarSpaceRenderer.initialize();
    }

    /** Client caches are per server connection: drop them on disconnect. */
    private static void resetClientState() {
        PartyHud.clear();
        DiceRevealHud.clear();
        MiniGamePageClient.clear();
        FloatingTextRenderer.clear();
        StarSpaceRenderer.clear();
        TileInfoClient.clear();
        GoalPoleFlipTracker.clear();
        GoalPoleCameraRoll.clear();
        SquishAnimations.clear();
        DestinationsRenderer.clear();
        // The carriers of the last world, else kept (with their world) until a world renders again
        PipeCarrierEntity.CLIENT_CARRIERS.clear();
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
