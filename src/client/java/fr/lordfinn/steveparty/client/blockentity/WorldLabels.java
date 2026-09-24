package fr.lordfinn.steveparty.client.blockentity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import fr.lordfinn.steveparty.items.custom.WrenchItem;

/** Small camera-facing labels drawn in the world by block entity renderers (like name tags). */
public final class WorldLabels {
    /** Size of a label's text: 1/64 block per text pixel. */
    public static final float SCALE = 1f / 64f;

    private WorldLabels() {}

    /**
     * Draws a label centred on (x, y, z), in block-local coordinates of the current matrices, facing the camera.
     * @param line vertical offset in lines (0: centred on the point, 1: one line below)
     */
    public static void draw(MatrixStack matrices, VertexConsumerProvider consumers, BlockEntityRenderDispatcher dispatcher,
                            double x, double y, double z, Text text, int color, int background, float line) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(dispatcher.camera.getRotation());
        matrices.scale(SCALE, -SCALE, SCALE);
        float width = textRenderer.getWidth(text);
        textRenderer.draw(text, -width / 2f, -4.5f + line * 10f, color, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.NORMAL, background, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }

    /** @return whether the local player holds a wrench (the goal pole details are shown then). */
    public static boolean holdingWrench() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        for (ItemStack stack : player.getHandItems()) {
            if (stack.getItem() instanceof WrenchItem) return true;
        }
        return false;
    }
}
