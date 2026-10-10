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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Drawing helpers for the Wrench overlays, in world coordinates (the camera offset is applied here): board paths made
 * of the mod's arrow particle sprite, and labels on plates cut like the mod's screens (see the art sources).
 */
public final class WorldDraw {
    /** The chevron of the mod's arrow particle (textures/particle/arrow.png): the dots of the board paths. */
    static final Identifier CHEVRON = Steveparty.id("textures/particle/arrow.png");
    private static final int PLATE_SIZE = 16, PLATE_BORDER = 4;
    /** The plate lies a little behind its text (label space: -z goes away from the camera). */
    private static final float BEHIND = -0.5f;
    /** Text on the plates: the dark grey of the mod's screen titles. */
    public static final int PLATE_TEXT = 0xFF3F3F3F;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    /** Plate colours (the frame): teal like the Tile screen, gold like the Advanced Tile's... */
    public enum Plate {
        TEAL, GOLD, GREEN, RED, ORANGE, PURPLE;

        final Identifier texture = Steveparty.id("textures/gui/sprites/board/plate_" + name().toLowerCase() + ".png");
    }

    private WorldDraw() {
    }

    // ---------------------------------------------------------------- board paths

    /**
     * A board path from {@code a} to {@code b}: chevrons lying on the way, pointing and scrolling toward {@code b} (like
     * the paths of a party board game). {@code phase} (blocks) moves them; the ends ({@code margin}) stay clear for the
     * tiles, and the chevrons fade in and out there.
     *
     * @param shift sideways offset (blocks, to the right of the travel direction): two opposite paths don't overlap
     */
    static void path(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d a, Vec3d b, int argb,
                     double size, double spacing, double phase, double margin, double shift) {
        path(matrices, consumers, camera, a.x, a.y, a.z, b.x, b.y, b.z, argb, size, spacing, phase, margin, shift);
    }

    /** {@link #path(MatrixStack, VertexConsumerProvider, Camera, Vec3d, Vec3d, int, double, double, double, double, double)}, allocation free. */
    static void path(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, double ax, double ay, double az,
                     double bx, double by, double bz, int argb, double size, double spacing, double phase, double margin, double shift) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 2 * margin + 0.1) return;
        double fx = dx / length, fy = dy / length, fz = dz / length;
        double sx = -fz, sz = fx;
        double sideLength = Math.sqrt(sx * sx + sz * sz);
        if (sideLength * sideLength < 1.0E-4) {
            sx = 1;
            sz = 0;
        } else {
            sx /= sideLength;
            sz /= sideLength;
        }
        Vec3d cam = camera.getPos();
        // Relative to the camera, shifted sideways
        double ox = ax + sx * shift - cam.x, oy = ay - cam.y, oz = az + sz * shift - cam.z;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getText(ChevronSprites.of(argb)));
        double hfx = fx * size / 2, hfy = fy * size / 2, hfz = fz * size / 2, hsx = sx * size / 2, hsz = sz * size / 2;
        double start = margin + Math.floorMod((long) Math.floor(phase * 1000), (long) Math.floor(spacing * 1000)) / 1000.0;
        int alpha = (argb >>> 24);
        for (double t = start; t <= length - margin; t += spacing) {
            double fade = Math.min(1, Math.min((t - margin) / 0.35, (length - margin - t) / 0.35));
            // The colour is in the texture (a ramp of it): the vertices only carry the fade
            int color = ((int) (alpha * Math.max(0, fade)) << 24) | 0xFFFFFF;
            double cx = ox + fx * t, cy = oy + fy * t, cz = oz + fz * t;
            float tipLx = (float) (cx + hfx - hsx), tipLy = (float) (cy + hfy), tipLz = (float) (cz + hfz - hsz);
            float tipRx = (float) (cx + hfx + hsx), tipRy = tipLy, tipRz = (float) (cz + hfz + hsz);
            float tailLx = (float) (cx - hfx - hsx), tailLy = (float) (cy - hfy), tailLz = (float) (cz - hfz - hsz);
            float tailRx = (float) (cx - hfx + hsx), tailRy = tailLy, tailRz = (float) (cz - hfz + hsz);
            // The chevron points to the top of its texture (v = 0); both sides drawn
            vertex(consumer, matrix, tailLx, tailLy, tailLz, color, 0, 1);
            vertex(consumer, matrix, tailRx, tailRy, tailRz, color, 1, 1);
            vertex(consumer, matrix, tipRx, tipRy, tipRz, color, 1, 0);
            vertex(consumer, matrix, tipLx, tipLy, tipLz, color, 0, 0);
            vertex(consumer, matrix, tipLx, tipLy, tipLz, color, 0, 0);
            vertex(consumer, matrix, tipRx, tipRy, tipRz, color, 1, 0);
            vertex(consumer, matrix, tailRx, tailRy, tailRz, color, 1, 1);
            vertex(consumer, matrix, tailLx, tailLy, tailLz, color, 0, 1);
        }
    }

    // ---------------------------------------------------------------- teleport arcs

    /**
     * A teleport link, sampled once (when the board view is built): a parabola {@code height} blocks high in its middle
     * from {@code a} to {@code b}, its points and how far along the arc each one is. Drawn by {@link #arc} without any
     * allocation.
     */
    static final class Arc {
        final double[] x, y, z, along;
        final double total;
        final Box bounds;

        Arc(Vec3d a, Vec3d b, double height) {
            int segments = Math.clamp((int) (a.distanceTo(b) * 8), 16, 240);
            x = new double[segments + 1];
            y = new double[segments + 1];
            z = new double[segments + 1];
            along = new double[segments + 1];
            double sum = 0;
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments;
                x[i] = a.x + (b.x - a.x) * t;
                y[i] = a.y + (b.y - a.y) * t + 4 * height * t * (1 - t);
                z[i] = a.z + (b.z - a.z) * t;
                if (i > 0) {
                    double dx = x[i] - x[i - 1], dy = y[i] - y[i - 1], dz = z[i] - z[i - 1];
                    sum += Math.sqrt(dx * dx + dy * dy + dz * dz);
                }
                along[i] = sum;
            }
            total = sum;
            bounds = new Box(a.x, Math.min(a.y, b.y), a.z, b.x, Math.max(a.y, b.y) + height, b.z).expand(0.5);
        }
    }

    /** Camera axes of the frame (render thread only), for the sparkles. */
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();

    /** A teleport link drawn once (the Wrench's ghost of a click): {@link #arc} on a new {@link Arc}. */
    static void arc(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d a, Vec3d b, double height,
                    int argb, int sparkle, double phase, double width, double margin) {
        arc(matrices, consumers, camera, new Arc(a, b, height), argb, sparkle, phase, width, margin);
    }

    /**
     * A teleport link: not a path (nobody walks it), so no chevrons but a dashed glowing arc, its dashes flowing toward
     * its end ({@code phase}, blocks), with a few twinkling sparkles riding it. The ends ({@code margin}) stay clear for
     * the tiles.
     */
    static void arc(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Arc arc, int argb, int sparkle,
                    double phase, double width, double margin) {
        if (arc.total < 2 * margin + 0.1) return;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getDebugQuads());
        Vec3d cam = camera.getPos();
        double dash = 0.28, half = width / 2;
        for (int i = 1; i < arc.x.length; i++) {
            double middle = (arc.along[i - 1] + arc.along[i]) / 2;
            if (Math.floorMod((long) Math.floor((middle - phase) / dash), 2L) != 0) continue;
            if (middle <= margin || middle >= arc.total - margin) continue;
            double fade = Math.min(1, Math.min((middle - margin) / 0.35, (arc.total - margin - middle) / 0.35));
            int color = ((int) ((argb >>> 24) * Math.max(0, fade)) << 24) | (argb & 0xFFFFFF);
            double x0 = arc.x[i - 1] - cam.x, y0 = arc.y[i - 1] - cam.y, z0 = arc.z[i - 1] - cam.z;
            double x1 = arc.x[i] - cam.x, y1 = arc.y[i] - cam.y, z1 = arc.z[i] - cam.z;
            // A band turned toward the camera: sideways = direction x view
            double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
            double vx = (x0 + x1) / 2, vy = (y0 + y1) / 2, vz = (z0 + z1) / 2;
            double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
            double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (length < 1.0E-4) continue;
            sx *= half / length;
            sy *= half / length;
            sz *= half / length;
            quad(consumer, matrix, x0 - sx, y0 - sy, z0 - sz, x0 + sx, y0 + sy, z0 + sz,
                    x1 + sx, y1 + sy, z1 + sz, x1 - sx, y1 - sy, z1 - sz, color);
        }
        // Sparkles riding the arc toward its end, twinkling (a four-pointed star facing the camera)
        RIGHT.set(1, 0, 0).rotate(camera.getRotation());
        UP.set(0, 1, 0).rotate(camera.getRotation());
        int count = Math.max(1, (int) (arc.total / 2.5));
        int last = arc.x.length - 1;
        for (int k = 0; k < count; k++) {
            double t = ((phase / Math.max(arc.total, 0.01)) * 0.6 + k / (double) count) % 1.0;
            double at = t * arc.total;
            if (at < margin || at > arc.total - margin) continue;
            int i = (int) Math.round(t * last);
            double size = 0.1 + 0.05 * Math.sin(phase * 7 + k * 2.1), thin = size * 0.22;
            double cx = arc.x[i] - cam.x, cy = arc.y[i] - cam.y, cz = arc.z[i] - cam.z;
            // A long thin diamond each way
            quad(consumer, matrix,
                    cx + UP.x() * size, cy + UP.y() * size, cz + UP.z() * size,
                    cx + RIGHT.x() * thin, cy + RIGHT.y() * thin, cz + RIGHT.z() * thin,
                    cx - UP.x() * size, cy - UP.y() * size, cz - UP.z() * size,
                    cx - RIGHT.x() * thin, cy - RIGHT.y() * thin, cz - RIGHT.z() * thin, sparkle);
            quad(consumer, matrix,
                    cx + RIGHT.x() * size, cy + RIGHT.y() * size, cz + RIGHT.z() * size,
                    cx + UP.x() * thin, cy + UP.y() * thin, cz + UP.z() * thin,
                    cx - RIGHT.x() * size, cy - RIGHT.y() * size, cz - RIGHT.z() * size,
                    cx - UP.x() * thin, cy - UP.y() * thin, cz - UP.z() * thin, sparkle);
        }
    }

    /** A quad (camera space), both sides drawn. */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, double ax, double ay, double az, double bx, double by,
                             double bz, double cx, double cy, double cz, double dx, double dy, double dz, int color) {
        consumer.vertex(matrix, (float) ax, (float) ay, (float) az).color(color);
        consumer.vertex(matrix, (float) bx, (float) by, (float) bz).color(color);
        consumer.vertex(matrix, (float) cx, (float) cy, (float) cz).color(color);
        consumer.vertex(matrix, (float) dx, (float) dy, (float) dz).color(color);
        consumer.vertex(matrix, (float) dx, (float) dy, (float) dz).color(color);
        consumer.vertex(matrix, (float) cx, (float) cy, (float) cz).color(color);
        consumer.vertex(matrix, (float) bx, (float) by, (float) bz).color(color);
        consumer.vertex(matrix, (float) ax, (float) ay, (float) az).color(color);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, int color, float u, float v) {
        consumer.vertex(matrix, x, y, z).color(color).texture(u, v).light(LIGHT);
    }

    // ---------------------------------------------------------------- plates

    /**
     * A camera-facing label on a plate, centred on {@code pos} (depth tested: the text lies just in front of its plate).
     *
     * @param scale block per text pixel
     */
    public static void plateLabel(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d pos, Text text,
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
        nineSlice(consumers.getBuffer(plateLayer), matrices.peek().getPositionMatrix(), -w / 2, -h / 2, w / 2, h / 2, BEHIND);
        // Drawn right away, then its text: the buffers of several layers are otherwise drawn together at the end, in
        // the order the layers first came, and a plate could cover the text of a label drawn before it
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(plateLayer);
        textRenderer.draw(text, -width / 2f + 0.5f, -3.5f, textColor, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw();
        matrices.pop();
    }

    /**
     * {@code plate} stretched over (x0, y0)-(x1, y1) in label space (the current matrices: text pixels, y down, facing
     * the camera), drawn right away so that what is drawn on it next is never covered.
     */
    public static void plate(MatrixStack matrices, VertexConsumerProvider consumers, Plate plate, float x0, float y0, float x1, float y1) {
        RenderLayer layer = RenderLayer.getText(plate.texture);
        nineSlice(consumers.getBuffer(layer), matrices.peek().getPositionMatrix(), x0, y0, x1, y1, BEHIND);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
    }

    /**
     * {@code plate} stretched over (x0, y0)-(x1, y1) in label space, {@code z} deep (label space: more negative is
     * farther from the camera): for a stack of plates (a panel and its rows), each in front of the one under it.
     * Drawn right away.
     */
    public static void plate(MatrixStack matrices, VertexConsumerProvider consumers, Plate plate, float x0, float y0, float x1, float y1, float z) {
        RenderLayer layer = RenderLayer.getText(plate.texture);
        nineSlice(consumers.getBuffer(layer), matrices.peek().getPositionMatrix(), x0, y0, x1, y1, z);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
    }

    /** A plain rectangle (x0, y0)-(x1, y1) in label space, {@code z} deep (as {@link #plate}), drawn right away. */
    public static void fill(MatrixStack matrices, VertexConsumerProvider consumers, float x0, float y0, float x1, float y1, float z, int argb) {
        RenderLayer layer = RenderLayer.getTextBackground();
        VertexConsumer consumer = consumers.getBuffer(layer);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        consumer.vertex(matrix, x0, y0, z).color(argb).light(LIGHT);
        consumer.vertex(matrix, x0, y1, z).color(argb).light(LIGHT);
        consumer.vertex(matrix, x1, y1, z).color(argb).light(LIGHT);
        consumer.vertex(matrix, x1, y0, z).color(argb).light(LIGHT);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
    }

    /** A horizontal rule one text pixel thick from x0 to x1 at y, in label space, on a plate (drawn right away). */
    public static void rule(MatrixStack matrices, VertexConsumerProvider consumers, float x0, float x1, float y, int argb) {
        RenderLayer layer = RenderLayer.getTextBackground();
        VertexConsumer consumer = consumers.getBuffer(layer);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float z = BEHIND / 2;
        consumer.vertex(matrix, x0, y, z).color(argb).light(LIGHT);
        consumer.vertex(matrix, x0, y + 1, z).color(argb).light(LIGHT);
        consumer.vertex(matrix, x1, y + 1, z).color(argb).light(LIGHT);
        consumer.vertex(matrix, x1, y, z).color(argb).light(LIGHT);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
    }

    /** The plate stretched over (x0, y0)-(x1, y1), its 4 pixel border kept. */
    private static void nineSlice(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z) {
        float b = PLATE_BORDER;
        float[] xs = {x0, x0 + b, x1 - b, x1}, ys = {y0, y0 + b, y1 - b, y1};
        float[] us = {0, b / PLATE_SIZE, 1 - b / PLATE_SIZE, 1};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                // Same winding as the font's glyphs: seen from the camera
                consumer.vertex(matrix, xs[i], ys[j], z).color(0xFFFFFFFF).texture(us[i], us[j]).light(LIGHT);
                consumer.vertex(matrix, xs[i], ys[j + 1], z).color(0xFFFFFFFF).texture(us[i], us[j + 1]).light(LIGHT);
                consumer.vertex(matrix, xs[i + 1], ys[j + 1], z).color(0xFFFFFFFF).texture(us[i + 1], us[j + 1]).light(LIGHT);
                consumer.vertex(matrix, xs[i + 1], ys[j], z).color(0xFFFFFFFF).texture(us[i + 1], us[j]).light(LIGHT);
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
