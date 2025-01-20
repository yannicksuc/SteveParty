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
        drawBox(matrices, vertexConsumers, pos, pos.add(1, 1, 1), red, green, blue, alpha);
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
        VertexRendering.drawFilledBox(matrices, vertexConsumer, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
    }
}
