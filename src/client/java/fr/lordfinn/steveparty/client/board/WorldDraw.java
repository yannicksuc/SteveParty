package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.renderer.GlowingCuboidRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Drawing helpers for the Wrench overlays, in world coordinates (the camera offset is applied here): board paths made
 * of the mod's arrow particle sprite, and labels on plates cut like the mod's screens (see the art sources).
 */
final class WorldDraw {
    /** The chevron of the mod's arrow particle (textures/particle/arrow.png): the dots of the board paths. */
    static final Identifier CHEVRON = Steveparty.id("textures/particle/arrow.png");
    private static final int PLATE_SIZE = 16, PLATE_BORDER = 4;
    /** The plate lies a little behind its text (label space: -z goes away from the camera). */
    private static final float BEHIND = -0.5f;
    /** Text on the plates: the dark grey of the mod's screen titles. */
    static final int PLATE_TEXT = 0xFF3F3F3F;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    /** Plate colours (the frame): teal like the Tile screen, gold like the Advanced Tile's... */
    enum Plate {
        TEAL, GOLD, GREEN, RED, ORANGE;

        final Identifier texture = Steveparty.id("textures/gui/sprites/board/plate_" + name().toLowerCase() + ".png");
    }

    private WorldDraw() {
    }

    // ---------------------------------------------------------------- board paths

    /**
     * A board path from {@code a} to {@code b}: chevrons lying on the way, pointing and scrolling toward {@code b} (like
     * the paths of a Mario Party board). {@code phase} (blocks) moves them; the ends ({@code margin}) stay clear for the
     * tiles, and the chevrons fade in and out there.
     *
     * @param shift sideways offset (blocks, to the right of the travel direction): two opposite paths don't overlap
     */
    static void path(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d a, Vec3d b, int argb,
                     double size, double spacing, double phase, double margin, double shift) {
        Vec3d d = b.subtract(a);
        double length = d.length();
        if (length < 2 * margin + 0.1) return;
        Vec3d forward = d.multiply(1 / length);
        Vec3d side = new Vec3d(-forward.z, 0, forward.x);
        if (side.lengthSquared() < 1.0E-4) side = new Vec3d(1, 0, 0);
        side = side.normalize();
        a = a.add(side.multiply(shift));
        Vec3d cam = camera.getPos();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getText(CHEVRON));
        Vec3d halfForward = forward.multiply(size / 2), halfSide = side.multiply(size / 2);
        double start = margin + Math.floorMod((long) Math.floor(phase * 1000), (long) Math.floor(spacing * 1000)) / 1000.0;
        int alpha = (argb >>> 24);
        for (double t = start; t <= length - margin; t += spacing) {
            double fade = Math.min(1, Math.min((t - margin) / 0.35, (length - margin - t) / 0.35));
            int color = ((int) (alpha * Math.max(0, fade)) << 24) | (argb & 0xFFFFFF);
            Vec3d centre = a.add(forward.multiply(t)).subtract(cam);
            Vec3d tipL = centre.add(halfForward).subtract(halfSide), tipR = centre.add(halfForward).add(halfSide);
            Vec3d tailL = centre.subtract(halfForward).subtract(halfSide), tailR = centre.subtract(halfForward).add(halfSide);
            // The chevron points to the top of its texture (v = 0); both sides drawn
            vertex(consumer, matrix, tailL, color, 0, 1);
            vertex(consumer, matrix, tailR, color, 1, 1);
            vertex(consumer, matrix, tipR, color, 1, 0);
            vertex(consumer, matrix, tipL, color, 0, 0);
            vertex(consumer, matrix, tipL, color, 0, 0);
            vertex(consumer, matrix, tipR, color, 1, 0);
            vertex(consumer, matrix, tailR, color, 1, 1);
            vertex(consumer, matrix, tailL, color, 0, 1);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3d p, int color, float u, float v) {
        consumer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(color).texture(u, v).light(LIGHT);
    }

    // ---------------------------------------------------------------- plates

    /**
     * A camera-facing label on a plate, centred on {@code pos} (depth tested: the text lies just in front of its plate).
     *
     * @param scale block per text pixel
     */
    static void plateLabel(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d pos, Text text,
                           Plate plate, int textColor, float scale) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        Vec3d cam = camera.getPos();
        matrices.push();
        matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        int width = textRenderer.getWidth(text);
        float w = Math.max(PLATE_SIZE, width + 9), h = PLATE_SIZE;
        RenderLayer plateLayer = RenderLayer.getText(plate.texture);
        nineSlice(consumers.getBuffer(plateLayer), matrices.peek().getPositionMatrix(), -w / 2, -h / 2, w / 2, h / 2);
        // Drawn right away, then its text: the buffers of several layers are otherwise drawn together at the end, in
        // the order the layers first came, and a plate could cover the text of a label drawn before it
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(plateLayer);
        textRenderer.draw(text, -width / 2f + 0.5f, -3.5f, textColor, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw();
        matrices.pop();
    }

    /** The plate stretched over (x0, y0)-(x1, y1), its 4 pixel border kept. */
    private static void nineSlice(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1) {
        float b = PLATE_BORDER;
        float[] xs = {x0, x0 + b, x1 - b, x1}, ys = {y0, y0 + b, y1 - b, y1};
        float[] us = {0, b / PLATE_SIZE, 1 - b / PLATE_SIZE, 1};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                // Same winding as the font's glyphs: seen from the camera
                consumer.vertex(matrix, xs[i], ys[j], BEHIND).color(0xFFFFFFFF).texture(us[i], us[j]).light(LIGHT);
                consumer.vertex(matrix, xs[i], ys[j + 1], BEHIND).color(0xFFFFFFFF).texture(us[i], us[j + 1]).light(LIGHT);
                consumer.vertex(matrix, xs[i + 1], ys[j + 1], BEHIND).color(0xFFFFFFFF).texture(us[i + 1], us[j + 1]).light(LIGHT);
                consumer.vertex(matrix, xs[i + 1], ys[j], BEHIND).color(0xFFFFFFFF).texture(us[i + 1], us[j]).light(LIGHT);
            }
        }
    }

    // ---------------------------------------------------------------- boxes

    /** A see-through filled box between two corners (world coordinates). */
    static void box(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d min, Vec3d max, int rgb, float alpha) {
        Vec3d cam = camera.getPos();
        GlowingCuboidRenderer.drawBox(matrices, consumers, min.x - cam.x, min.y - cam.y, min.z - cam.z, max.x - cam.x, max.y - cam.y, max.z - cam.z,
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
    }
}
