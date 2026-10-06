package fr.lordfinn.steveparty.client.utils;

import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
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


    /**
     * The metal stencil as a plate with a thickness, like vanilla's generated items: the front and back faces, and a
     * side face on every edge of an opaque pixel (the plate's outline and the outline of the cut-out shape).
     * Same placement as {@link #renderSymbol}: a 1 x 1 plate centred on (0.5, 0.5, 0.5) before {@code transform}.
     *
     * @param shape  the 16x16 shape cut out of the plate
     * @param margin the plate's frame around the shape ({@link StencilResourceManager.Kind#margin()})
     */
    public static void renderPlate(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay,
                                   Identifier textureId, byte[] shape, int margin, Consumer<MatrixStack> transform) {
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(textureId));
        int n = StencilShape.SIDE + 2 * margin;
        float px = 1f / n, top = 0.5f + THICKNESS / 2, bottom = 0.5f - THICKNESS / 2;

        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        transform.accept(matrices);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();

        // Front and back (u grows towards -x, v towards -z, as in renderSymbol)
        quad(consumer, entry, m, light, overlay, 0, 1, 0,
                -0.5f, top, 0.5f, 1, 0, 0.5f, top, 0.5f, 0, 0, 0.5f, top, -0.5f, 0, 1, -0.5f, top, -0.5f, 1, 1);
        quad(consumer, entry, m, light, overlay, 0, -1, 0,
                -0.5f, bottom, -0.5f, 1, 1, 0.5f, bottom, -0.5f, 0, 1, 0.5f, bottom, 0.5f, 0, 0, -0.5f, bottom, 0.5f, 1, 0);

        // Sides: each opaque pixel edge facing a hole or the outside, textured with that pixel
        for (int tx = 0; tx < n; tx++) {
            for (int ty = 0; ty < n; ty++) {
                if (!opaque(shape, margin, n, tx, ty)) continue;
                float x0 = 0.5f - (tx + 1) * px, x1 = 0.5f - tx * px;   // u = tx / n  ->  x = 0.5 - u
                float z0 = 0.5f - (ty + 1) * px, z1 = 0.5f - ty * px;
                float u0 = (tx + 0.25f) * px, u1 = (tx + 0.75f) * px, v0 = (ty + 0.25f) * px, v1 = (ty + 0.75f) * px;
                if (!opaque(shape, margin, n, tx - 1, ty)) // +x side (smaller u)
                    quad(consumer, entry, m, light, overlay, 1, 0, 0,
                            x1, top, z1, u0, v0, x1, top, z0, u1, v1, x1, bottom, z0, u1, v1, x1, bottom, z1, u0, v0);
                if (!opaque(shape, margin, n, tx + 1, ty)) // -x side
                    quad(consumer, entry, m, light, overlay, -1, 0, 0,
                            x0, top, z0, u0, v0, x0, top, z1, u1, v1, x0, bottom, z1, u1, v1, x0, bottom, z0, u0, v0);
                if (!opaque(shape, margin, n, tx, ty - 1)) // +z side (smaller v)
                    quad(consumer, entry, m, light, overlay, 0, 0, 1,
                            x0, top, z1, u0, v0, x1, top, z1, u1, v1, x1, bottom, z1, u1, v1, x0, bottom, z1, u0, v0);
                if (!opaque(shape, margin, n, tx, ty + 1)) // -z side
                    quad(consumer, entry, m, light, overlay, 0, 0, -1,
                            x1, top, z0, u0, v0, x0, top, z0, u1, v1, x0, bottom, z0, u1, v1, x1, bottom, z0, u0, v0);
            }
        }
        matrices.pop();
    }

    /** Thickness of the plate: one pixel of a 16x16 item, as vanilla's generated items. */
    private static final float THICKNESS = 1f / 16;

    /** @return whether the plate's pixel (x, y) is metal: inside the texture and not cut out by the shape. */
    private static boolean opaque(byte[] shape, int margin, int n, int x, int y) {
        if (x < 0 || y < 0 || x >= n || y >= n) return false;
        int sx = x - margin, sy = y - margin;
        boolean inShape = sx >= 0 && sy >= 0 && sx < StencilShape.SIDE && sy < StencilShape.SIDE;
        return !inShape || shape[StencilShape.index(sx, sy)] == 0;
    }

    private static void quad(VertexConsumer c, MatrixStack.Entry entry, Matrix4f m, int light, int overlay, float nx, float ny, float nz,
                             float x1, float y1, float z1, float u1, float v1, float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3, float x4, float y4, float z4, float u4, float v4) {
        c.vertex(m, x1, y1, z1).color(255, 255, 255, 255).texture(u1, v1).overlay(overlay).light(light).normal(entry, nx, ny, nz);
        c.vertex(m, x2, y2, z2).color(255, 255, 255, 255).texture(u2, v2).overlay(overlay).light(light).normal(entry, nx, ny, nz);
        c.vertex(m, x3, y3, z3).color(255, 255, 255, 255).texture(u3, v3).overlay(overlay).light(light).normal(entry, nx, ny, nz);
        c.vertex(m, x4, y4, z4).color(255, 255, 255, 255).texture(u4, v4).overlay(overlay).light(light).normal(entry, nx, ny, nz);
    }

    public static final int WHITE = 0xFFFFFF;
}
