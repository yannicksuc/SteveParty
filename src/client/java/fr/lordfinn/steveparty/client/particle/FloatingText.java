package fr.lordfinn.steveparty.client.particle;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public class FloatingText {
    private final String text;
    public double x, y, z;
    public double prevX, prevY, prevZ;
    private final double velocityY = 0.02;
    private int age = 0;
    private final int maxAge = 40; // 2 seconds at 20 TPS

    public FloatingText(String text, double x, double y, double z) {
        this.text = text;
        this.x = prevX = x;
        this.y = prevY = y;
        this.z = prevZ = z;
    }

    public boolean tick() {
        prevX = x;
        prevY = y;
        prevZ = z;
        age++;
        y += velocityY;
        return age < maxAge;
    }

    public float alpha() {
        return 1.0f - ((float) age / maxAge);
    }

    public String getText() {
        return text;
    }

    public void render(MatrixStack matrices, double camX, double camY, double camZ, TextRenderer textRenderer, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        matrices.push();


        double renderX = prevX + (x - prevX) * tickDelta;
        double renderY = prevY + (y - prevY) * tickDelta;
        double renderZ = prevZ + (z - prevZ) * tickDelta;

        // Move to particle position relative to camera
        matrices.translate(renderX - camX, renderY - camY, renderZ - camZ);
       matrices.multiply(mc.gameRenderer.getCamera().getRotation());

        float scale = 0.05f;
        matrices.scale(scale, -scale, -scale);

        int alphaInt = Math.min(255, Math.max(0, (int)(alpha() * 255)));
        int color = (alphaInt << 24) | 0xFFFFFF;

        // Use correct Immediate VertexConsumerProvider for world rendering
        VertexConsumerProvider.Immediate vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();

        textRenderer.draw(
                text,
                -textRenderer.getWidth(text) / 2f,
                0,
                color,
                false,
                matrices.peek().getPositionMatrix(),
                vertexConsumers,
                net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                0,
                0xF000F0
        );

        vertexConsumers.draw(); // flush the buffer

        matrices.pop();
    }

}

