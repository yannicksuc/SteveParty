package fr.lordfinn.steveparty.client;

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
import fr.lordfinn.steveparty.client.model.TradingStallModelPlugin;
import fr.lordfinn.steveparty.client.particle.ArrowParticle;
import fr.lordfinn.steveparty.client.particle.EnchantedCircularParticle;
import fr.lordfinn.steveparty.client.particle.HereParticle;
import fr.lordfinn.steveparty.client.payloads.PayloadReceivers;
import fr.lordfinn.steveparty.client.renderer.DestinationsRenderer;
import fr.lordfinn.steveparty.client.renderer.FloatingTextRenderer;
import fr.lordfinn.steveparty.client.renderer.items.TripleJumpShoesRenderer;
import fr.lordfinn.steveparty.client.screens.*;
import fr.lordfinn.steveparty.client.utils.ConfigurationManager;
import fr.lordfinn.steveparty.components.CarpetColorComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.particles.ModParticles;
import fr.lordfinn.steveparty.payloads.custom.TripleJumpPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
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

    private static final BlockColorProvider getTileColor = (state, world, pos, tintIndex) -> {
        if (world == null) return 0xFFFFFFFF;
        if (!(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity  tileEntity)) return 0xFFFFFFFF;
        ItemStack behaviorItemstack = tileEntity.getActiveCartridgeItemStack();
        if (behaviorItemstack == null || behaviorItemstack.isEmpty()) return 0xFFFFFFFF;
        tileEntity.getBoardSpaceBehavior(behaviorItemstack).updateBoardSpaceColor(tileEntity, behaviorItemstack);
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

        initScreens();
        initParticles();
        initEntitiesRenderers();
        initBlockEntitiesRenderers();
        initItemRenderers();
        DestinationsRenderer.initialize();
        initKeybinds();

        HudRenderCallback.EVENT.register(PARTY_STEPS_HUD);
        PartyStepsHud.registerKeyHandlers();
        Runtime.getRuntime().addShutdownHook(new Thread(PartyStepsHud::saveConfigOnExit));

        initParticleRenderers();

       }

    private void initItemRenderers() {
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.STENCIL, new StencilItemRenderer());

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
        BlockEntityRendererFactories.register(ModBlockEntities.BIG_BOOK_ENTITY, TeleportationPadBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.STEP_CONTROLLER_ENTITY, StepControllerBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.TRAFFIC_SIGN_ENTITY, TrafficSignBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.STENCIL_MAKER_ENTITY, StencilMakerBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LOOTING_BOX_ENTITY, LootingBoxBlockEntityRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(BLUE_STAR_FRAGMENTS_BLOCK, RenderLayer.getTranslucent());
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
        BlockRenderLayerMap.INSTANCE.putBlock(TRADING_STALL, RenderLayer.getCutout());

    }

    private static void initParticles() {
        ParticleFactoryRegistry.getInstance().register(ModParticles.HERE_PARTICLE, HereParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ARROW_PARTICLE, ArrowParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ENCHANTED_CIRCULAR_PARTICLE, EnchantedCircularParticle.Factory::new);
    }

    private static void initScreens() {
        //Initialize HUDs
        PARTY_STEPS_HUD.initialize();
        
        //Initialize Screens
        HandledScreens.register(TILE_SCREEN_HANDLER, TileScreen::new);
        HandledScreens.register(ROUTER_SCREEN_HANDLER, RouterScreen::new);
        HandledScreens.register(HOP_SWITCH_SCREEN_HANDLER, HopSwitchScreen::new);
        HandledScreens.register(HIDING_TRADER_SCREEN_HANDLER, HidingTraderScreen::new);
        HandledScreens.register(CARTRIDGE_SCREEN_HANDLER, CartridgeInventoryScreen::new);
        HandledScreens.register(MINI_GAME_PAGE_SCREEN_HANDLER, MiniGamePageScreen::new);
        HandledScreens.register(MINI_GAMES_CATALOGUE_SCREEN_HANDLER, MiniGamesCatalogueScreen::new);
        HandledScreens.register(HERE_WE_GO_BOOK_SCREEN_HANDLER, HereWeGoBookScreen::new);
        HandledScreens.register(HERE_WE_COME_BOOK_SCREEN_HANDLER, HereWeComeBookScreen::new);
        HandledScreens.register(STENCIL_MAKER_SCREEN_HANDLER, StencilMakerScreen::new);
        HandledScreens.register(TRADING_STALL_SCREEN_HANDLER, TradingStallScreen::new);
        HandledScreens.register(CASH_REGISTER_SCREEN_HANDLER, CashRegisterScreen::new);
        HandledScreens.register(GOAL_POLE_BASE_SCREEN_HANDLER, GoalPoleBaseScreen::new);
        HandledScreens.register(GOAL_POLE_SCREEN_HANDLER, GoalPoleScreen::new);
        HandledScreens.register(LOOTING_BOX_SCREEN_HANDLER, LootingBoxScreen::new);
    }

    public static KeyBinding exportRecipeKey;
    private static void initKeybinds() {

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick();
        });

    }


    private static void initParticleRenderers() {
        FloatingTextRenderer.registerRenderCallback();
    }

    private static boolean lastPressed = false;
    private static boolean lastJumpPressed = false;

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        long window = client.getWindow().getHandle();
        boolean isPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_0) == GLFW.GLFW_PRESS;

        if (isPressed && !lastPressed) {
            if (client.currentScreen != null) {
                RecipeExporter.exportRecipe(client);
            }
        }
        lastPressed = isPressed;

        boolean jumpPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        if (jumpPressed && !lastJumpPressed) {
            ClientPlayNetworking.send(new TripleJumpPayload());
        }
        lastJumpPressed = jumpPressed;
    }

}
