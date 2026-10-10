package fr.lordfinn.steveparty.client.renderer;

import fr.lordfinn.steveparty.payloads.custom.GoalPolePopupsPayload;
import fr.lordfinn.steveparty.utils.ScorePopupStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * The « +N » / « −N » popups over goal pole bases: one per holder at a time, counting up while he keeps scoring
 * ({@link ScorePopupStack}), at a spot of his own around the top of the base.
 */
@Environment(EnvType.CLIENT)
public final class GoalPolePopupRenderer {
    private static final int GAIN_COLOR = 0xC90E0E;
    private static final int LOSS_COLOR = 0x6E7F92;
    private static final float TEXT_SCALE = 0.03f;
    private static final Map<BlockPos, ScorePopupStack> STACKS = new HashMap<>();

    private GoalPolePopupRenderer() {}

    public static void receive(GoalPolePopupsPayload payload) {
        ScorePopupStack stack = STACKS.computeIfAbsent(payload.base().toImmutable(), pos -> new ScorePopupStack());
        for (GoalPolePopupsPayload.Gain gain : payload.gains()) stack.add(gain.holder(), gain.delta());
    }

    public static void clear() {
        STACKS.clear();
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.isPaused() || STACKS.isEmpty()) return;
            STACKS.values().removeIf(stack -> {
                stack.tick();
                return stack.isEmpty();
            });
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (STACKS.isEmpty()) return;
            MatrixStack matrices = context.matrixStack();
            if (matrices == null) return;
            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer textRenderer = client.textRenderer;
            Vec3d cam = context.camera().getPos();
            Quaternionf rotation = context.camera().getRotation();
            float tickDelta = context.tickCounter().getTickDelta(true);
            VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
            STACKS.forEach((base, stack) -> {
                for (ScorePopupStack.Popup popup : stack.popups()) {
                    draw(popup, base, cam, rotation, matrices, textRenderer, consumers, tickDelta);
                }
            });
            consumers.draw();
        });
    }

    private static void draw(ScorePopupStack.Popup popup, BlockPos base, Vec3d cam, Quaternionf rotation, MatrixStack matrices,
                             TextRenderer textRenderer, VertexConsumerProvider consumers, float tickDelta) {
        int alpha = MathHelper.clamp((int) (popup.alpha(tickDelta) * 255), 0, 255);
        // TextRenderer treats an alpha below 4 as fully opaque: skip the fully faded frames instead
        if (alpha < 4) return;
        // A spot of the holder's own over the base, the same each time he scores
        int hash = popup.holder.hashCode() * 0x9E3779B1;
        double dx = ((hash & 0xFF) / 255.0) - 0.5;
        double dz = (((hash >>> 8) & 0xFF) / 255.0) - 0.5;
        double dy = 1.0 + ((hash >>> 16) & 0xFF) / 255.0 * 0.5 + popup.rise(tickDelta);
        matrices.push();
        matrices.translate(base.getX() + 0.5 + dx - cam.x, base.getY() + 0.5 + dy - cam.y, base.getZ() + 0.5 + dz - cam.z);
        matrices.multiply(rotation);
        float scale = TEXT_SCALE * popup.scale(tickDelta);
        matrices.scale(scale, -scale, -scale);
        String text = popup.text();
        int color = (alpha << 24) | (popup.value() < 0 ? LOSS_COLOR : GAIN_COLOR);
        textRenderer.draw(text, -textRenderer.getWidth(text) / 2f, -textRenderer.fontHeight / 2f, color, false,
                matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0,
                LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }
}
