package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.MulaStarEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The shooting star: a white-hot heart in a big soft glow of the Mula's colour, facing the camera, twinkling; bright at
 * any light level. Its sparkling trail is made of particles (MulaStarEntity#trail).
 */
public class MulaStarRenderer extends EntityRenderer<MulaStarEntity, MulaStarRenderer.State> {
    private static final Identifier GLOW = Steveparty.id("textures/entity/mula_hallo.png");
    private static final Identifier HEART = Steveparty.id("textures/entity/mula_wisp.png");

    public MulaStarRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    public static class State extends EntityRenderState {
        int color;
        float progress;
        /** Tail points relative to the star (newest first) and how many. */
        final float[] tail = new float[MulaStarEntity.TAIL * 3];
        int tailCount;
        private final double[] point = new double[3];
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(MulaStarEntity star, State state, float tickDelta) {
        super.updateRenderState(star, state, tickDelta);
        state.color = star.getVariant().getGlowColor();
        state.progress = star.progress(tickDelta);
        state.tailCount = star.tailCount();
        for (int i = 0; i < state.tailCount; i++) {
            star.tailPoint(i, state.point);
            state.tail[i * 3] = (float) (state.point[0] - state.x);
            state.tail[i * 3 + 1] = (float) (state.point[1] - state.y);
            state.tail[i * 3 + 2] = (float) (state.point[2] - state.z);
        }
    }

    @Override
    public void render(State state, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        // born from the pop (grows in quickly), fading out at the very end of its flight
        float in = MathHelper.clamp(state.progress * 12f, 0f, 1f);
        float out = MathHelper.clamp((1f - state.progress) * 10f, 0f, 1f);
        float twinkle = 0.85f + 0.15f * MathHelper.sin(state.age * 1.7f);
        float size = in * out * twinkle;
        if (size <= 0.01f) return;
        // it stays a bright point however far it is: beyond 16 blocks its size grows with the distance
        float far = Math.max(1f, (float) Math.sqrt(state.squaredDistanceToCamera) / 16f);
        int r = (state.color >> 16) & 0xFF, g = (state.color >> 8) & 0xFF, b = state.color & 0xFF;
        VertexConsumer glow = buffers.getBuffer(RenderLayer.getEntityTranslucentEmissive(GLOW));
        VertexConsumer heart = buffers.getBuffer(RenderLayer.getEntityTranslucentEmissive(HEART));
        // the comet tail: fading, shrinking glows where it was over the last ticks, white-hot along its middle
        for (int i = state.tailCount - 1; i >= 1; i--) {
            float k = 1f - i / (float) MulaStarEntity.TAIL;
            matrices.push();
            matrices.translate(state.tail[i * 3], state.tail[i * 3 + 1], state.tail[i * 3 + 2]);
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
        super.render(state, matrices, buffers, light);
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
