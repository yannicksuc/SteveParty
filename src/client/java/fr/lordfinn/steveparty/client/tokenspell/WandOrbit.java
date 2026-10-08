package fr.lordfinn.steveparty.client.tokenspell;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.particles.SpellPalette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Little magic shapes orbiting the Tokenizer Wand's jewel whenever it is held: a square, a circle, a triangle and
 * a second circle, in the spell gradient (fuchsia, violet, blue, then the jewel's own lapis), full bright, slowly turning around the jewel with a little bob and a
 * gentle pulse. Drawn with the held item itself (first and third person) into the same buffers, as opaque cut-outs
 * like the wand: so they never lag behind, and shader packs draw them with the hand, in front of the sky and clouds
 * (a translucent layer was drawn behind the clouds with Iris). Never on item icons, dropped items or item frames.
 * No allocation per frame. Render thread only.
 */
public final class WandOrbit {
    private static final RenderLayer[] LAYERS = {
            RenderLayer.getEntityCutoutNoCull(texture("magic_square")),
            RenderLayer.getEntityCutoutNoCull(texture("magic_circle")),
            RenderLayer.getEntityCutoutNoCull(texture("magic_triangle")),
            RenderLayer.getEntityCutoutNoCull(texture("magic_circle"))};
    private static final int SHAPES = LAYERS.length;
    /** One colour per shape, stepping down the gradient to the jewel's lapis. */
    private static final int[] COLORS = {SpellPalette.FUCHSIA, SpellPalette.VIOLET, SpellPalette.BLUE, SpellPalette.LAPIS};
    /** Centre of the wand's jewel, in model space (model pixels / 16: x 8, y 29, z 8). */
    private static final float JEWEL_X = 0.5F, JEWEL_Y = 29F / 16F, JEWEL_Z = 0.5F;
    /** Orbit radius and shape half-size, in model space (the jewel is 6 pixels wide). */
    private static final float RADIUS = 7F / 16F, HALF_SIZE = 1.3F / 16F;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final Vector3f CENTER = new Vector3f(), POINT = new Vector3f(), RIGHT = new Vector3f(), UP = new Vector3f();
    private static final Quaternionf BILLBOARD = new Quaternionf();

    private WandOrbit() {
    }

    private static Identifier texture(String name) {
        return Steveparty.id("textures/particle/" + name + ".png");
    }

    public static boolean shows(ModelTransformationMode mode) {
        return mode.isFirstPerson() || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
    }

    /**
     * Draws the orbiting shapes, the matrices being those the item was rendered with (before its display transform).
     */
    public static void render(ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                              VertexConsumerProvider vertexConsumers, BakedModel model) {
        float time = (Util.getMeasuringTimeMs() % 3_600_000L) / 1000F;
        matrices.push();
        model.getTransformation().getTransformation(mode).apply(leftHanded, matrices);
        matrices.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        // Size of a model unit where the shapes are drawn
        matrix.transformPosition(JEWEL_X, JEWEL_Y, JEWEL_Z, CENTER);
        matrix.transformPosition(JEWEL_X + HALF_SIZE, JEWEL_Y, JEWEL_Z, POINT);
        float halfSize = POINT.distance(CENTER);

        // Face the camera: first person items are drawn in view space; in the world, the camera's rotation
        if (mode.isFirstPerson()) {
            BILLBOARD.identity();
        } else {
            BILLBOARD.set(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
        }
        BILLBOARD.transform(RIGHT.set(halfSize, 0, 0));
        BILLBOARD.transform(UP.set(0, halfSize, 0));

        for (int i = 0; i < SHAPES; i++) {
            float angle = time * 0.9F + i * MathHelper.TAU / SHAPES;
            float bob = MathHelper.sin(time * 1.7F + i * 1.3F) * (1.2F / 16F);
            matrix.transformPosition(JEWEL_X + MathHelper.cos(angle) * RADIUS, JEWEL_Y + bob,
                    JEWEL_Z + MathHelper.sin(angle) * RADIUS, POINT);
            int color = COLORS[i];
            // A gentle pulse of size (the shapes are drawn as opaque cut-outs, like the wand itself)
            float pulse = 0.85F + 0.15F * MathHelper.sin(time * 2.3F + i * 2.1F);
            quad(vertexConsumers.getBuffer(LAYERS[i]), POINT, color, pulse);
        }
        matrices.pop();
    }

    private static void quad(VertexConsumer buffer, Vector3f center, int rgb, float scale) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        corner(buffer, center, -scale, -scale, r, g, b, 0, 1);
        corner(buffer, center, scale, -scale, r, g, b, 1, 1);
        corner(buffer, center, scale, scale, r, g, b, 1, 0);
        corner(buffer, center, -scale, scale, r, g, b, 0, 0);
    }

    private static void corner(VertexConsumer buffer, Vector3f center, float sx, float sy, int r, int g, int b, float u, float v) {
        buffer.vertex(center.x() + RIGHT.x() * sx + UP.x() * sy, center.y() + RIGHT.y() * sx + UP.y() * sy,
                        center.z() + RIGHT.z() * sx + UP.z() * sy)
                .color(r, g, b, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(0, 1, 0);
    }
}
