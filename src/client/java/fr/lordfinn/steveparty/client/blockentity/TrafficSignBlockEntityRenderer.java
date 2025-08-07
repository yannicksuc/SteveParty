package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.TrafficSignBlockEntity;
import fr.lordfinn.steveparty.client.utils.StencilRenderUtils;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.PI;

public class TrafficSignBlockEntityRenderer implements BlockEntityRenderer<TrafficSignBlockEntity> {
    private final BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();

    public TrafficSignBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
    }

    @Override
    public void render(TrafficSignBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (entity == null || entity.getCachedState() == null) return;

        int rotationIndex = entity.getRotation();
        float rotationDegrees = rotationIndex * -22.5F + 180; // Each index step is 22.5 degrees
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationDegrees));
        matrices.translate(-0.5, 0, -0.5);

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getCutout());
        BlockRenderView world = entity.getWorld();
        BlockPos pos = entity.getPos();
        BakedModel model = blockRenderManager.getModel(entity.getCachedState());

        blockRenderManager.getModelRenderer().renderSmooth(world, model, entity.getCachedState(), pos, matrices, vertexConsumer, true, Random.create(), 42L, overlay);

        // Get the symbol texture and apply dye color and glow
        byte[] stencilData = entity.getShape();
        StencilResourceManager.StencilTextures textures = StencilResourceManager.getStencilShape(stencilData);
        int color = entity.getColor().getSignColor();

        if (textures != null && textures.plankShape() != null)
            StencilRenderUtils.renderSymbol(
                    matrices,
                    vertexConsumers,
                    light,
                    overlay,
                    textures.plankShape(),
                    entity.getColor().getSignColor(),
                    entity.isGlowing(),
                    (stack) -> {
                        MatrixStack.Entry entry = stack.peek();
                        Matrix4f matrix = entry.getPositionMatrix();
                        matrix.rotate((float) Math.toRadians(-67.5f), 1, 0, 0);
                        matrix.translate(0, -0.082f, 0.365f);
                    }
            );
//        renderSymbol(matrices, vertexConsumers, light, overlay, textures.plankShape(), color, entity.isGlowing());
        matrices.pop();
    }

    private void renderSymbol(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Identifier symbolTextureId, int color, boolean isGlowing) {
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(symbolTextureId);
        texture.bindTexture();

        RenderLayer renderLayer = RenderLayer.getEntityTranslucent(symbolTextureId);
        if (isGlowing) {
            light = 0x0F0F0F;
        }

        VertexConsumer symbolConsumer = vertexConsumers.getBuffer(renderLayer);

        matrices.translate(0.5, 0.5f, 0);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();
        matrix.rotate((float) ((-45f - 22.5f)  / 180f * PI), 1, 0, 0);
        matrix.translate(0, -0.738f, 0.093f);

        // Extract RGB components from the color
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        // Draw a quad on the front face with the applied color
        symbolConsumer.vertex(matrix, -0.5f,  0.5f, 0.5f).color(red, green, blue, 255).texture(1, 0).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        symbolConsumer.vertex(matrix,  0.5f,  0.5f, 0.5f).color(red, green, blue, 255).texture(0, 0).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        symbolConsumer.vertex(matrix,  0.5f, 0.5f, -0.5f).color(red, green, blue, 255).texture(0, 1).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        symbolConsumer.vertex(matrix, -0.5f, 0.5f, -0.5f).color(red, green, blue, 255).texture(1, 1).light(light).overlay(overlay).normal(entry, 0, 1, 0);
    }
}