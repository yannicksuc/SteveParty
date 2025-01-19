package fr.lordfinn.steveparty.client;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceEntity;
import fr.lordfinn.steveparty.client.blockentity.BigBookRenderer;
import fr.lordfinn.steveparty.client.blockentity.StepControllerRenderer;
import fr.lordfinn.steveparty.client.blockentity.TileEntityRenderer;
import fr.lordfinn.steveparty.client.entity.HidingTraderEntityRenderer;
import fr.lordfinn.steveparty.client.entity.DiceRenderer;
import fr.lordfinn.steveparty.client.entity.DirectionDisplayRenderer;
import fr.lordfinn.steveparty.client.gui.PartyStepsHud;
import fr.lordfinn.steveparty.client.particle.ArrowParticle;
import fr.lordfinn.steveparty.client.particle.EnchantedCircularParticle;
import fr.lordfinn.steveparty.client.particle.HereParticle;
import fr.lordfinn.steveparty.client.payloads.PayloadReceivers;
import fr.lordfinn.steveparty.client.renderer.AbstractBoardSpaceSelectorItemDestinationsRenderer;
import fr.lordfinn.steveparty.client.screens.CartridgeInventoryScreen;
import fr.lordfinn.steveparty.client.screens.HidingTraderScreen;
import fr.lordfinn.steveparty.client.screens.TileScreen;
import fr.lordfinn.steveparty.client.utils.ConfigurationManager;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.particles.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static fr.lordfinn.steveparty.blocks.ModBlocks.TILE;
import static fr.lordfinn.steveparty.screens.ModScreens.*;
import static fr.lordfinn.steveparty.utils.MessageUtils.getColorFromText;

@SuppressWarnings("unused")
public class StevepartyClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("steveparty");
    public static final PartyStepsHud PARTY_STEPS_HUD = new PartyStepsHud();

    private static final BlockColorProvider getTileColor = (state, world, pos, tintIndex) -> {
        if (world == null) return 0xFFEB68;
        BoardSpaceEntity tileEntity = (BoardSpaceEntity) world.getBlockEntityRenderData(pos);
        if (tileEntity == null) return 0xFFEB68;
        ItemStack behaviorItemstack = tileEntity.getActiveTileBehaviorItemStack();
        Integer color = behaviorItemstack.get(ModComponents.TB_START_COLOR);
        if (color == null || color == 0)
            return 0xFFEB68;
        return color;
    };

    private static final ItemColorProvider getTokenIemColor = (stack, tintIndex) -> {
        if (stack == null) return 0xFFFFFFFF;
        Text customName = stack.get(DataComponentTypes.CUSTOM_NAME);
        int color = getColorFromText(customName);
        color = (color & 0x00FFFFFF) | 0xFF000000;
        return color;
    };

    @Override
    public void onInitializeClient() {
        ConfigurationManager.loadConfig();
        PARTY_STEPS_HUD.initialize();

        HandledScreens.register(TILE_SCREEN_HANDLER, TileScreen::new);
        HandledScreens.register(HIDING_TRADER_SCREEN_HANDLER, HidingTraderScreen::new);
        HandledScreens.register(CARTRIDGE_SCREEN_HANDLER, CartridgeInventoryScreen::new);

        ParticleFactoryRegistry.getInstance().register(ModParticles.HERE_PARTICLE, HereParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ARROW_PARTICLE, ArrowParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ENCHANTED_CIRCULAR_PARTICLE, EnchantedCircularParticle.Factory::new);
        PayloadReceivers.initialize();

        EntityRendererRegistry.register(ModEntities.DICE_ENTITY, DiceRenderer::new);
        EntityRendererRegistry.register(ModEntities.DIRECTION_DISPLAY_ENTITY, DirectionDisplayRenderer::new);
        EntityRendererRegistry.register(ModEntities.HIDING_TRADER_ENTITY, HidingTraderEntityRenderer::new);

        BlockEntityRendererFactories.register(ModBlockEntities.TILE_ENTITY, TileEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.BIG_BOOK_ENTITY, BigBookRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.STEP_CONTROLLER_ENTITY, StepControllerRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.TRIGGER_POINT, RenderLayer.getTranslucent());

        ColorProviderRegistry.BLOCK.register(StevepartyClient.getTileColor, TILE);
        ColorProviderRegistry.ITEM.register(StevepartyClient.getTokenIemColor, ModItems.TOKEN);

        HudRenderCallback.EVENT.register(PARTY_STEPS_HUD);
        PartyStepsHud.registerKeyHandlers();

        Runtime.getRuntime().addShutdownHook(new Thread(PartyStepsHud::saveConfigOnExit));

        AbstractBoardSpaceSelectorItemDestinationsRenderer.initialize();
    }
}
