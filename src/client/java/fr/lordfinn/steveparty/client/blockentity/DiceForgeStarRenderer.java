package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Cosmetic: a flat golden star frame turning slowly around the forge, like the launch stars of the Comet
 * Observatory. A hollow five-pointed star, its top bevelled into a lit ridge, in the gold of the forge screen.
 * Its hole is wide enough for the block's corners to pass through as it turns.
 */
public class DiceForgeStarRenderer {
    private static final Identifier TEXTURE = Steveparty.id("textures/entity/dice_forge_star.png");
    /** Outer points and valleys of the star (blocks from the centre). */
    private static final float OUTER = 1.3f, VALLEY = 0.95f;
    /** The ridge and the hole, as fractions of the outline: the hole's valleys stay outside the block (0.71). */
    private static final float RIDGE = 0.9f, HOLE = 0.8f;
    private static final float THICKNESS = 0.07f, RIDGE_RISE = 0.025f;
    /** Height of the frame (blocks above the block's bottom), and its slow bob. */
    private static final float HEIGHT = 0.5f, BOB = 0.03f;
    /** One turn every 36 s, one bob every 5 s (ticks). */
    private static final float TURN_TICKS = 36 * 20, BOB_TICKS = 5 * 20;

    private static final int RIDGE_COLOR = 0xFFFFF0A8, EDGE_COLOR = 0xFFF2C84B, INNER_COLOR = 0xFFD9A93A,
            SIDE_COLOR = 0xFFC99A2A, SHADOW_COLOR = 0xFF9A6C10;

    private final float[][] outline = points();

    public void render(DiceForgeBlockEntity forge, float partialTick, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light) {
        if (forge.getWorld() == null) return;
        float time = forge.getWorld().getTime() + partialTick;
        matrices.push();
        matrices.translate(0.5, HEIGHT + BOB * MathHelper.sin(time * MathHelper.TAU / BOB_TICKS), 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 360f / TURN_TICKS));
        VertexConsumer consumer = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        light = LightmapTextureManager.MAX_LIGHT_COORDINATE;   // full bright: it glows at night
        MatrixStack.Entry entry = matrices.peek();
        float top = THICKNESS / 2, bottom = -THICKNESS / 2, ridge = top + RIDGE_RISE;
        int n = outline.length;
        for (int i = 0; i < n; i++) {
            float[] a = outline[i], b = outline[(i + 1) % n];
            // top: the outer slope up to the ridge, then down to the hole
            quad(consumer, entry, light, 0, 1, 0,
                    a[0], top, a[1], EDGE_COLOR, b[0], top, b[1], EDGE_COLOR,
                    b[0] * RIDGE, ridge, b[1] * RIDGE, RIDGE_COLOR, a[0] * RIDGE, ridge, a[1] * RIDGE, RIDGE_COLOR);
            quad(consumer, entry, light, 0, 1, 0,
                    a[0] * RIDGE, ridge, a[1] * RIDGE, RIDGE_COLOR, b[0] * RIDGE, ridge, b[1] * RIDGE, RIDGE_COLOR,
                    b[0] * HOLE, top, b[1] * HOLE, INNER_COLOR, a[0] * HOLE, top, a[1] * HOLE, INNER_COLOR);
            // bottom
            quad(consumer, entry, light, 0, -1, 0,
                    a[0], bottom, a[1], SHADOW_COLOR, a[0] * HOLE, bottom, a[1] * HOLE, SHADOW_COLOR,
                    b[0] * HOLE, bottom, b[1] * HOLE, SHADOW_COLOR, b[0], bottom, b[1], SHADOW_COLOR);
            // outer and inner walls, lit by their own normal
            float nx = b[1] - a[1], nz = a[0] - b[0];
            float len = MathHelper.sqrt(nx * nx + nz * nz);
            nx /= len;
            nz /= len;
            quad(consumer, entry, light, nx, 0, nz,
                    a[0], bottom, a[1], SIDE_COLOR, b[0], bottom, b[1], SIDE_COLOR,
                    b[0], top, b[1], EDGE_COLOR, a[0], top, a[1], EDGE_COLOR);
            quad(consumer, entry, light, -nx, 0, -nz,
                    a[0] * HOLE, top, a[1] * HOLE, INNER_COLOR, b[0] * HOLE, top, b[1] * HOLE, INNER_COLOR,
                    b[0] * HOLE, bottom, b[1] * HOLE, SHADOW_COLOR, a[0] * HOLE, bottom, a[1] * HOLE, SHADOW_COLOR);
        }
        matrices.pop();
    }

    /** The star's outline, points and valleys alternating, the first point towards north. */
    private static float[][] points() {
        float[][] pts = new float[10][];
        for (int i = 0; i < 10; i++) {
            float r = i % 2 == 0 ? OUTER : VALLEY;
            double a = Math.toRadians(-90 + 36 * i);
            pts[i] = new float[]{(float) (r * Math.cos(a)), (float) (r * Math.sin(a))};
        }
        return pts;
    }

    private static void quad(VertexConsumer consumer, MatrixStack.Entry entry, int light, float nx, float ny, float nz,
                             float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2,
                             float x3, float y3, float z3, int c3, float x4, float y4, float z4, int c4) {
        vertex(consumer, entry, light, nx, ny, nz, x1, y1, z1, c1, 0, 0);
        vertex(consumer, entry, light, nx, ny, nz, x2, y2, z2, c2, 1, 0);
        vertex(consumer, entry, light, nx, ny, nz, x3, y3, z3, c3, 1, 1);
        vertex(consumer, entry, light, nx, ny, nz, x4, y4, z4, c4, 0, 1);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, int light, float nx, float ny, float nz,
                               float x, float y, float z, int color, float u, float v) {
        consumer.vertex(entry.getPositionMatrix(), x, y, z).color(color).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
    }
}
