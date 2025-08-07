package fr.lordfinn.steveparty.client.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public class StencilRenderUtils {

    public static void renderSymbol(MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers,
                                    int light, int overlay,
                                    Identifier textureId,
                                    int color,
                                    boolean isGlowing,
                                    Consumer<MatrixStack> transform) {
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
        texture.bindTexture();

        if (isGlowing) {
            light = 0xF000F0; // glowing
        }

        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(textureId));

        matrices.push();
        matrices.translate(0.5, 0, 0.5);

        // Apply the block-specific transformation
        transform.accept(matrices);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // Draw quad
        consumer.vertex(matrix, -0.5f, 0.5f, 0.5f).color(r, g, b, 255).texture(1, 0).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        consumer.vertex(matrix,  0.5f, 0.5f, 0.5f).color(r, g, b, 255).texture(0, 0).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        consumer.vertex(matrix,  0.5f, 0.5f, -0.5f).color(r, g, b, 255).texture(0, 1).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        consumer.vertex(matrix, -0.5f, 0.5f, -0.5f).color(r, g, b, 255).texture(1, 1).light(light).overlay(overlay).normal(entry, 0, 1, 0);

        matrices.pop();
    }


    public static final int WHITE = 0xFFFFFF;
}
