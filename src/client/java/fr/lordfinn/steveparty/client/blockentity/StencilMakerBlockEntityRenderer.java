package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.StencilMakerBlockEntity;
import fr.lordfinn.steveparty.client.StevepartyClient;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

public class StencilMakerBlockEntityRenderer implements BlockEntityRenderer<StencilMakerBlockEntity> {

    public StencilMakerBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
    }
    @Override
    public void render(StencilMakerBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ItemStack stencilData = entity.getStencil();
        StevepartyClient.LOGGER.info("Rendering stencil: {}", stencilData);
        if (stencilData != null && !stencilData.isEmpty()) {
            StevepartyClient.LOGGER.info("Rendering stencil: {}", stencilData);
            byte[] shape = StencilItem.getShape(stencilData);
            StencilResourceManager.StencilTextures textures = StencilResourceManager.getStencilShape(shape);
            if (textures != null) {
                renderSymbol(matrices, vertexConsumers, light, overlay, textures.metalStencil());
            }
        }
    }


    private void renderSymbol(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Identifier symbolTextureId) {
        matrices.push();
        StevepartyClient.LOGGER.info("Rendering symbol: {}", symbolTextureId);
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(symbolTextureId);
        texture.bindTexture();

        RenderLayer renderLayer = RenderLayer.getEntityTranslucent(symbolTextureId);

        VertexConsumer symbolConsumer = vertexConsumers.getBuffer(renderLayer);

        matrices.translate(0.5f, 0f, 0.5f);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();

        // Draw a quad on the front face with the applied color
        symbolConsumer.vertex(matrix, -0.5f,  15/16f, 0.5f).color(255, 255, 255, 255).texture(1, 0).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        symbolConsumer.vertex(matrix,  0.5f,  15/16f, 0.5f).color(255, 255, 255, 255).texture(0, 0).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        symbolConsumer.vertex(matrix,  0.5f, 15/16f, -0.5f).color(255, 255, 255, 255).texture(0, 1).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        symbolConsumer.vertex(matrix, -0.5f, 15/16f, -0.5f).color(255, 255, 255, 255).texture(1, 1).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        matrices.pop();
    }
}
