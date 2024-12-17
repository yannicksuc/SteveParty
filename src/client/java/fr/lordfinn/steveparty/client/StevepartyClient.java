package fr.lordfinn.steveparty.client;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.tiles.TileEntity;
import fr.lordfinn.steveparty.client.blockentity.BigBookRenderer;
import fr.lordfinn.steveparty.client.blockentity.TileEntityRenderer;
import fr.lordfinn.steveparty.client.entity.DiceRenderer;
import fr.lordfinn.steveparty.client.entity.DirectionDisplayRenderer;
import fr.lordfinn.steveparty.client.particle.ArrowParticle;
import fr.lordfinn.steveparty.client.particle.EnchantedCircularParticle;
import fr.lordfinn.steveparty.client.particle.HereParticle;
import fr.lordfinn.steveparty.client.payloads.PayloadReceivers;
import fr.lordfinn.steveparty.client.screens.TileScreen;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.particles.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColorProvider;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.DisplayEntityRenderer;
import net.minecraft.client.render.entity.DisplayEntityRenderer.BlockDisplayEntityRenderer;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import static fr.lordfinn.steveparty.blocks.ModBlocks.TILE;
import static fr.lordfinn.steveparty.screens.ModScreens.TILE_SCREEN_HANDLER;

@SuppressWarnings("unused")
public class StevepartyClient implements ClientModInitializer {

    private static final BlockColorProvider getColor = (state, world, pos, tintIndex) -> {
        if (world == null) return 0xFFEB68;
        TileEntity tileEntity = (TileEntity) world.getBlockEntityRenderData(pos);
        if (tileEntity == null) return 0xFFEB68;
        ItemStack behaviorItemstack = tileEntity.getActiveTileBehaviorItemStack();
        Integer color = behaviorItemstack.get(ModComponents.TB_START_COLOR);
        if (color == null || color == 0)
            return 0xFFEB68;
        return color;
    };

    @Override
    public void onInitializeClient() {
        HandledScreens.register(TILE_SCREEN_HANDLER, TileScreen::new);

        ParticleFactoryRegistry.getInstance().register(ModParticles.HERE_PARTICLE, HereParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ARROW_PARTICLE, ArrowParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.ENCHANTED_CIRCULAR_PARTICLE, EnchantedCircularParticle.Factory::new);
        PayloadReceivers.initialize();

        EntityRendererRegistry.register(ModEntities.DICE_ENTITY, DiceRenderer::new);
        EntityRendererRegistry.register(ModEntities.DIRECTION_DISPLAY_ENTITY, DirectionDisplayRenderer::new);

        BlockEntityRendererFactories.register(ModBlockEntities.TILE_ENTITY, TileEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.BIG_BOOK_ENTITY, BigBookRenderer::new);

        ColorProviderRegistry.BLOCK.register(StevepartyClient.getColor, TILE);

    }

}
