package fr.lordfinn.steveparty.client.board;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A ring of items slowly circling a point of the world, each turning on itself, with an optional small label under
 * each one (a count): what a board space gives, takes or holds (see {@link TileInfoClient}), the prizes of a
 * Trichaudron space... Reusable: give it a centre, the items and their labels. Allocates nothing per frame.
 */
public final class ItemRing {
    /** Degrees per tick: the ring turns once in 30 seconds, each item on itself in 9. */
    private static final float ORBIT_SPEED = 0.6f, SPIN_SPEED = 2f;
    private static final float ITEM_SCALE = 0.42f, LABEL_SCALE = 1f / 70f;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private ItemRing() {
    }

    /**
     * Draws {@code stacks} evenly round (x, y, z) (world coordinates; the camera offset is applied here).
     *
     * @param labels one per stack, drawn under it (null: none; an entry may be null)
     * @param time   world time + tick delta
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, @Nullable World world,
                              double x, double y, double z, double radius, List<ItemStack> stacks,
                              @Nullable List<OrderedText> labels, float time) {
        int count = stacks.size();
        if (count == 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;
        double cx = x - camera.getPos().x, cy = y - camera.getPos().y, cz = z - camera.getPos().z;
        float orbit = time * ORBIT_SPEED;
        for (int i = 0; i < count; i++) {
            ItemStack stack = stacks.get(i);
            float angle = (orbit + 360f * i / count) * MathHelper.RADIANS_PER_DEGREE;
            double ix = cx + Math.cos(angle) * radius, iz = cz + Math.sin(angle) * radius;
            double iy = cy + 0.06 * MathHelper.sin(time * 0.08f + i * 1.7f);
            matrices.push();
            matrices.translate(ix, iy, iz);
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * SPIN_SPEED + i * 40f));
            matrices.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            client.getItemRenderer().renderItem(stack, ModelTransformationMode.FIXED, LIGHT, OverlayTexture.DEFAULT_UV,
                    matrices, consumers, world, i);
            matrices.pop();
            OrderedText label = labels == null || i >= labels.size() ? null : labels.get(i);
            if (label != null) {
                matrices.translate(0, -0.3, 0);
                matrices.multiply(camera.getRotation());
                matrices.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
                float width = font.getWidth(label);
                font.draw(label, -width / 2f, -4f, 0xFFFFFFFF, true, matrices.peek().getPositionMatrix(), consumers,
                        TextRenderer.TextLayerType.NORMAL, 0x60000000, LIGHT);
            }
            matrices.pop();
        }
    }
}
