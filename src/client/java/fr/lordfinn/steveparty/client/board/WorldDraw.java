package fr.lordfinn.steveparty.client.board;

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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Drawing helpers for the Wrench overlays, in world coordinates (the camera offset is applied here). */
final class WorldDraw {
    private WorldDraw() {
    }

    /** A thin line segment (batched: many per frame). */
    static void line(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d a, Vec3d b, int argb) {
        Vec3d cam = camera.getPos();
        float ax = (float) (a.x - cam.x), ay = (float) (a.y - cam.y), az = (float) (a.z - cam.z);
        float bx = (float) (b.x - cam.x), by = (float) (b.y - cam.y), bz = (float) (b.z - cam.z);
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-4f) return;
        nx /= length;
        ny /= length;
        nz /= length;
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getLines());
        consumer.vertex(entry, ax, ay, az).color(argb).normal(entry, nx, ny, nz);
        consumer.vertex(entry, bx, by, bz).color(argb).normal(entry, nx, ny, nz);
    }

    /** An arrow: a line and a chevron at {@code head} fraction of the way (1 = at b), lying flat. */
    static void arrow(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d a, Vec3d b, int argb, double head) {
        line(matrices, consumers, camera, a, b, argb);
        Vec3d dir = b.subtract(a);
        double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (flat < 1.0E-3) return;
        Vec3d tip = a.add(dir.multiply(head));
        Vec3d back = dir.normalize().multiply(-0.35);
        Vec3d side = new Vec3d(-dir.z / flat, 0, dir.x / flat).multiply(0.22);
        line(matrices, consumers, camera, tip, tip.add(back).add(side), argb);
        line(matrices, consumers, camera, tip, tip.add(back).subtract(side), argb);
    }

    /** A thick line: its own strip, flushed right away (a few per frame at most). */
    static void thickLine(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Camera camera, Vec3d a, Vec3d b, int argb, double width) {
        Vec3d cam = camera.getPos();
        RenderLayer layer = RenderLayer.getDebugLineStrip(width);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer consumer = consumers.getBuffer(layer);
        consumer.vertex(matrix, (float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z)).color(argb);
        consumer.vertex(matrix, (float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z)).color(argb);
        consumers.draw(layer);
    }

    /** A see-through filled box between two corners (world coordinates). */
    static void box(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d min, Vec3d max, int rgb, float alpha) {
        Vec3d cam = camera.getPos();
        GlowingCuboidRenderer.drawBox(matrices, consumers, min.x - cam.x, min.y - cam.y, min.z - cam.z, max.x - cam.x, max.y - cam.y, max.z - cam.z,
                ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
    }

    /** A camera-facing label centred on {@code pos}, seen through blocks. */
    static void label(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d pos, Text text, int color, float scale) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        Vec3d cam = camera.getPos();
        matrices.push();
        matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        float width = textRenderer.getWidth(text);
        textRenderer.draw(text, -width / 2f, -4.5f, color, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x60000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }
}
