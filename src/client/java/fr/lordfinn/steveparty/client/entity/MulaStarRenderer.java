package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.MulaStarEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The shooting star: a white-hot heart in a big soft glow of the Mula's colour, facing the camera, twinkling; bright at
 * any light level. Its sparkling trail is made of particles (MulaStarEntity#trail).
 */
public class MulaStarRenderer extends EntityRenderer<MulaStarEntity> {
    private static final Identifier GLOW = Steveparty.id("textures/entity/mula_hallo.png");
    private static final Identifier HEART = Steveparty.id("textures/entity/mula_wisp.png");

    /** Tail points relative to the star (newest first). */
    private final float[] tail = new float[MulaStarEntity.TAIL * 3];
    private final double[] point = new double[3];

    public MulaStarRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(MulaStarEntity star) {
        return GLOW;
    }

    @Override
    public void render(MulaStarEntity star, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        int color = star.getVariant().getGlowColor();
        float progress = star.progress(tickDelta);
        // born from the pop (grows in quickly), fading out at the very end of its flight
        float in = MathHelper.clamp(progress * 12f, 0f, 1f);
        float out = MathHelper.clamp((1f - progress) * 10f, 0f, 1f);
        float twinkle = 0.85f + 0.15f * MathHelper.sin((star.age + tickDelta) * 1.7f);
        float size = in * out * twinkle;
        if (size <= 0.01f) return;
        double x = MathHelper.lerp(tickDelta, star.lastRenderX, star.getX());
        double y = MathHelper.lerp(tickDelta, star.lastRenderY, star.getY());
        double z = MathHelper.lerp(tickDelta, star.lastRenderZ, star.getZ());
        int tailCount = star.tailCount();
        for (int i = 0; i < tailCount; i++) {
            star.tailPoint(i, point);
            tail[i * 3] = (float) (point[0] - x);
            tail[i * 3 + 1] = (float) (point[1] - y);
            tail[i * 3 + 2] = (float) (point[2] - z);
        }
        // it stays a bright point however far it is: beyond 16 blocks its size grows with the distance
        float far = Math.max(1f, (float) Math.sqrt(this.dispatcher.getSquaredDistanceToCamera(star)) / 16f);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        VertexConsumer glow = buffers.getBuffer(RenderLayer.getEntityTranslucentEmissive(GLOW));
        VertexConsumer heart = buffers.getBuffer(RenderLayer.getEntityTranslucentEmissive(HEART));
        // the comet tail: fading, shrinking glows where it was over the last ticks, white-hot along its middle
        for (int i = tailCount - 1; i >= 1; i--) {
            float k = 1f - i / (float) MulaStarEntity.TAIL;
            matrices.push();
            matrices.translate(tail[i * 3], tail[i * 3 + 1], tail[i * 3 + 2]);
            matrices.multiply(this.dispatcher.getRotation());
            float half = size * far * (0.3f + 0.7f * k);
            quad(matrices, glow, 1.3f * half, r, g, b, (int) (255 * k));
            quad(matrices, heart, 0.45f * half, 255, 255, 255, (int) (230 * k * k));
            matrices.pop();
        }
        matrices.push();
        matrices.multiply(this.dispatcher.getRotation());
        quad(matrices, glow, 2.2f * size * far, r, g, b, 255);
        quad(matrices, glow, 1.2f * size * far, 255, 255, 255, 200);
        quad(matrices, heart, 0.8f * size * far, 255, 255, 255, 255);
        matrices.pop();
        super.render(star, yaw, tickDelta, matrices, buffers, light);
    }

    private static void quad(MatrixStack matrices, VertexConsumer vertices, float half, int r, int g, int b, int alpha) {
        MatrixStack.Entry entry = matrices.peek();
        vertex(vertices, entry, -half, -half, 0f, 1f, r, g, b, alpha);
        vertex(vertices, entry, half, -half, 1f, 1f, r, g, b, alpha);
        vertex(vertices, entry, half, half, 1f, 0f, r, g, b, alpha);
        vertex(vertices, entry, -half, half, 0f, 0f, r, g, b, alpha);
    }

    private static void vertex(VertexConsumer vertices, MatrixStack.Entry entry, float x, float y, float u, float v,
                               int r, int g, int b, int alpha) {
        vertices.vertex(entry.getPositionMatrix(), x, y, 0f).color(r, g, b, alpha).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(entry, 0f, 1f, 0f);
    }
}
