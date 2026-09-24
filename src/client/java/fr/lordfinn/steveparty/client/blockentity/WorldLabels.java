package fr.lordfinn.steveparty.client.blockentity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
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
        draw(matrices, consumers, dispatcher, x, y, z, text, color, background, line, SCALE);
    }

    /** Same, with a text size of {@code scale} block per text pixel. */
    public static void draw(MatrixStack matrices, VertexConsumerProvider consumers, BlockEntityRenderDispatcher dispatcher,
                            double x, double y, double z, Text text, int color, int background, float line, float scale) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(dispatcher.camera.getRotation());
        matrices.scale(scale, -scale, scale);
        float width = textRenderer.getWidth(text);
        textRenderer.draw(text, -width / 2f, -4.5f + line * 10f, color, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.NORMAL, background, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }

    /**
     * @return whether the local player's crosshair is on a block of the column x, z between {@code minY} and
     * {@code maxY} (a goal pole and its base): with several poles around, only the one looked at shows its details
     */
    public static boolean lookingAtColumn(int x, int z, int minY, int maxY) {
        BlockPos pos = lookedAtBlock();
        return pos != null && pos.getX() == x && pos.getZ() == z && pos.getY() >= minY && pos.getY() <= maxY;
    }

    /** Farther than the reach, so that a pole can be read from a few blocks away. */
    private static final double LOOK_DISTANCE = 16;
    private static Entity lookEntity;
    private static double lookX, lookY, lookZ;
    private static float lookYaw, lookPitch;
    private static BlockPos lookedAt;

    /** The block under the crosshair up to {@link #LOOK_DISTANCE} blocks away (cast again only when the view moves). */
    private static BlockPos lookedAtBlock() {
        MinecraftClient client = MinecraftClient.getInstance();
        Entity viewer = client.getCameraEntity();
        if (viewer == null) return null;
        if (viewer != lookEntity || viewer.getX() != lookX || viewer.getEyeY() != lookY || viewer.getZ() != lookZ
                || viewer.getYaw() != lookYaw || viewer.getPitch() != lookPitch) {
            lookEntity = viewer;
            lookX = viewer.getX();
            lookY = viewer.getEyeY();
            lookZ = viewer.getZ();
            lookYaw = viewer.getYaw();
            lookPitch = viewer.getPitch();
            HitResult hit = viewer.raycast(LOOK_DISTANCE, 1f, false);
            lookedAt = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
        }
        return lookedAt;
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
