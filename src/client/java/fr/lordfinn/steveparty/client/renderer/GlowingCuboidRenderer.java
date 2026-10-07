package fr.lordfinn.steveparty.client.renderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.List;

import static org.joml.Math.lerp;

public class GlowingCuboidRenderer {

    private static final List<Color> RAINBOW_COLORS = List.of(Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA);

    public enum GradientType {
        RAINBOW,
        SOLID_COLOR
    }

    public static void renderCuboids(MatrixStack matrices, VertexConsumerProvider vertexConsumerProvider, BlockPos pos, GradientType gradientType) {
        if (MinecraftClient.getInstance().world == null)
            return;

        float time = MinecraftClient.getInstance().world.getTime() / 20f; // Adjust speed of color change
        float r = 0, g = 0, b = 0;

        switch (gradientType) {
            case RAINBOW -> {
                int stepCount = RAINBOW_COLORS.size();
                int currentIndex = (int) (time % stepCount);
                int nextIndex = (currentIndex + 1) % stepCount;
                Color currentColor = RAINBOW_COLORS.get(currentIndex);
                Color nextColor = RAINBOW_COLORS.get(nextIndex);
                float lerpFactor = (time % 1);
                r = lerp(currentColor.getRed(), nextColor.getRed(), lerpFactor) / 255.0f;
                g = lerp(currentColor.getGreen(), nextColor.getGreen(), lerpFactor) / 255.0f;
                b = lerp(currentColor.getBlue(), nextColor.getBlue(), lerpFactor) / 255.0f;
            }
            case SOLID_COLOR -> {
                r = 0.2f;
                g = 0.2f;
                b = 1.0f; // Solid red color
            }
        }

        drawBlockBox(matrices, vertexConsumerProvider, pos, r, g, b, 0.5f);
    }

    public static void renderCuboids(MatrixStack matrices, VertexConsumerProvider vertexConsumerProvider, BlockPos pos) {
        renderCuboids(matrices, vertexConsumerProvider, pos, GradientType.RAINBOW);
    }

    public static void drawBlockBox(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockPos pos, float red, float green, float blue, float alpha) {
        // A tile is highlighted where it is seen: hugging its surface (lowered, sloped, all 4 blocks of a large tile)
        net.minecraft.client.world.ClientWorld world = MinecraftClient.getInstance().world;
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        if (world != null && camera.isReady()) {
            BlockPos tile = fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces.resolve(world, pos);
            net.minecraft.block.BlockState state = world.getBlockState(tile);
            if (state.getBlock() instanceof fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock) {
                var layout = state.get(fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SIZE);
                var support = state.get(fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SUPPORT);
                Vec3d camPos = camera.getPos();
                for (int[] cell : layout.cells()) {
                    for (Box box : support.outline(cell[0], cell[1]).getBoundingBoxes()) {
                        Box seen = box.offset(tile.getX() + cell[0], tile.getY(), tile.getZ() + cell[1]).offset(camPos.negate()).expand(0.01);
                        drawBox(matrices, vertexConsumers, seen, red, green, blue, alpha);
                        drawEdges(matrices, vertexConsumers, seen, red, green, blue);
                    }
                }
                return;
            }
        }
        drawBox(matrices, vertexConsumers, pos, pos.add(1, 1, 1), red, green, blue, alpha);
        if (camera.isReady()) {
            Vec3d cam = camera.getPos();
            drawEdges(matrices, vertexConsumers, new Box(pos).offset(cam.negate()).expand(0.01), red, green, blue);
        }
    }

    /**
     * The glowing outline of a highlighted box (camera space): bars along its 12 edges, of the colour brightened,
     * pulsing gently; bright where seen, and dimmed through what hides it (found behind the terrain).
     */
    public static void drawEdges(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Box box, float red, float green, float blue) {
        net.minecraft.client.world.ClientWorld world = MinecraftClient.getInstance().world;
        float time = world == null ? 0 : (world.getTime() % 400 + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true)) / 20f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * Math.PI * 1.2);
        double thick = Math.min(0.045 + 0.015 * pulse, Math.min(box.getLengthX(), Math.min(box.getLengthY(), box.getLengthZ())) / 3);
        float r = red + (1 - red) * 0.35f, g = green + (1 - green) * 0.35f, b = blue + (1 - blue) * 0.35f;
        org.joml.Matrix4f matrix = matrices.peek().getPositionMatrix();
        edges(vertexConsumers.getBuffer(HighlightLayers.SEEN), matrix, box, thick, r, g, b, 0.85f + 0.15f * pulse);
        edges(vertexConsumers.getBuffer(HighlightLayers.HIDDEN), matrix, box, thick * 0.7, r, g, b, 0.3f);
    }

    private static void edges(VertexConsumer consumer, org.joml.Matrix4f matrix, Box box, double t, float r, float g, float b, float a) {
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        double h = t / 2;
        for (double y : ys) for (double z : zs) bar(consumer, matrix, box.minX - h, y - h, z - h, box.maxX + h, y + h, z + h, r, g, b, a);
        for (double x : xs) for (double z : zs) bar(consumer, matrix, x - h, box.minY + h, z - h, x + h, box.maxY - h, z + h, r, g, b, a);
        for (double x : xs) for (double y : ys) bar(consumer, matrix, x - h, y - h, box.minZ + h, x + h, y + h, box.maxZ - h, r, g, b, a);
    }

    /** A thin box, its 6 faces. */
    private static void bar(VertexConsumer c, org.joml.Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1,
                            float r, float g, float b, float a) {
        float ax = (float) x0, ay = (float) y0, az = (float) z0, bx = (float) x1, by = (float) y1, bz = (float) z1;
        quad(c, m, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz, r, g, b, a);
        quad(c, m, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, r, g, b, a);
        quad(c, m, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az, r, g, b, a);
        quad(c, m, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz, r, g, b, a);
        quad(c, m, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az, r, g, b, a);
        quad(c, m, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz, r, g, b, a);
    }

    private static void quad(VertexConsumer c, org.joml.Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float r, float g, float b, float a) {
        c.vertex(m, x0, y0, z0).color(r, g, b, a);
        c.vertex(m, x1, y1, z1).color(r, g, b, a);
        c.vertex(m, x2, y2, z2).color(r, g, b, a);
        c.vertex(m, x3, y3, z3).color(r, g, b, a);
    }

    /** The outline's layers: seen (depth tested), and through the terrain (no depth test, dimmed); no depth written. */
    private static final class HighlightLayers extends RenderLayer {
        static final RenderLayer SEEN = RenderLayer.of("steveparty_highlight_seen", VertexFormats.POSITION_COLOR,
                VertexFormat.DrawMode.QUADS, 4096, false, true, MultiPhaseParameters.builder()
                        .program(COLOR_PROGRAM)
                        .transparency(TRANSLUCENT_TRANSPARENCY)
                        .cull(DISABLE_CULLING)
                        .layering(VIEW_OFFSET_Z_LAYERING)
                        .writeMaskState(COLOR_MASK)
                        .build(false));
        static final RenderLayer HIDDEN = RenderLayer.of("steveparty_highlight_hidden", VertexFormats.POSITION_COLOR,
                VertexFormat.DrawMode.QUADS, 4096, false, true, MultiPhaseParameters.builder()
                        .program(COLOR_PROGRAM)
                        .transparency(TRANSLUCENT_TRANSPARENCY)
                        .cull(DISABLE_CULLING)
                        .depthTest(ALWAYS_DEPTH_TEST)
                        .writeMaskState(COLOR_MASK)
                        .build(false));

        private HighlightLayers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling,
                                boolean translucent, Runnable begin, Runnable end) {
            super(name, format, mode, size, crumbling, translucent, begin, end);
        }
    }

    public static void drawBox(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockPos pos1, BlockPos pos2, float red, float green, float blue, float alpha) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        if (camera.isReady()) {
            Vec3d vec3d = camera.getPos().negate();
            Box box = Box.enclosing(pos1, pos2).offset(vec3d);
            drawBox(matrices, vertexConsumers, box.contract(0.5f).offset(-0.5, -0.5, -0.5), red, green, blue, alpha);
        }
    }

    public static void drawBox(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Box box, float red, float green, float blue, float alpha) {
        drawBox(matrices, vertexConsumers, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha);
    }

    public static void drawBox(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float red, float green, float blue, float alpha) {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getDebugFilledBox());
        WorldRenderer.renderFilledBox(matrices, vertexConsumer, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
    }
}
