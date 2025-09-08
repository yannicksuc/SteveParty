package fr.lordfinn.steveparty.client.particle;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public class FloatingText {
    private final String text;
    public double x, y, z;
    public double prevX, prevY, prevZ;
    private final double velX, velY, velZ;
    private final float scale;
    private final int color;        // 0xRRGGBB
    private final int maxAge;       // durée en ticks
    private final float fadeStart;  // 0 à 1
    private int age = 0;

    // Constructeur simple pour tests
    public FloatingText(String text, double x, double y, double z) {
        this(text, x, y, z, 0, 0.02, 0, 40, 0.05f, 0xFFFFFF, 0.5f);
    }

    // Constructeur complet avec payload
    public FloatingText(String text, double x, double y, double z,
                        double velX, double velY, double velZ,
                        float duration, float scale, int color, float fadeStart) {
        this.text = text;
        this.x = this.prevX = x;
        this.y = this.prevY = y;
        this.z = this.prevZ = z;
        this.velX = velX;
        this.velY = velY;
        this.velZ = velZ;
        this.maxAge = Math.max(1, (int) duration);
        this.scale = scale;
        this.color = color & 0xFFFFFF; // ignore alpha pour maintenant
        this.fadeStart = Math.max(0f, Math.min(1f, fadeStart));
    }

    public boolean tick() {
        prevX = x;
        prevY = y;
        prevZ = z;

        x += velX;
        y += velY;
        z += velZ;

        age++;
        return age < maxAge;
    }

    public float alpha() {
        float progress = (float) age / maxAge;
        if (progress < fadeStart) {
            return 1.0f;
        } else {
            return Math.max(0f, 1.0f - (progress - fadeStart) / (1.0f - fadeStart));
        }
    }

    public void render(MatrixStack matrices, double camX, double camY, double camZ,
                       TextRenderer textRenderer, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        matrices.push();

        double renderX = prevX + (x - prevX) * tickDelta;
        double renderY = prevY + (y - prevY) * tickDelta;
        double renderZ = prevZ + (z - prevZ) * tickDelta;

        // Move to particle position relative to camera
        matrices.translate(renderX - camX, renderY - camY, renderZ - camZ);
        matrices.multiply(mc.gameRenderer.getCamera().getRotation());

        matrices.scale(scale, -scale, -scale);

        int alphaInt = Math.min(255, Math.max(0, (int)(alpha() * 255)));
        int finalColor = (alphaInt << 24) | color;

        VertexConsumerProvider.Immediate vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();

        textRenderer.draw(
                text,
                -textRenderer.getWidth(text) / 2f,
                0,
                finalColor,
                false,
                matrices.peek().getPositionMatrix(),
                vertexConsumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                0xF000F0
        );

        vertexConsumers.draw(); // flush the buffer

        matrices.pop();
    }
}
