package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ThresholdCartridgeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

/**
 * What the board rule cartridges show over their space, drawn by the tile's renderer: the condition of a Threshold
 * obstacle (« 7 or more », « DOUBLE »), what a Common pot holds; always facing the camera, readable from all around.
 */
public final class BoardRuleOverlays {
    /** Height of the label's middle above the tile's block. */
    private static final double LABEL_Y = 1.7;
    private static final float LABEL_SCALE = 0.03f;

    private BoardRuleOverlays() {
    }

    public static void render(BoardSpaceBlockEntity entity, BoardSpaceType tileType, ItemStack stack, double centreX, double centreZ,
                              float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (stack.isEmpty()) return;
        if (tileType == BoardSpaceType.TILE_THRESHOLD && stack.getItem() instanceof ThresholdCartridgeItem) {
            label(ThresholdCartridgeItem.label(stack), centreX, centreZ, 0xFFFFFF, matrices, consumers);
        } else if (tileType == BoardSpaceType.TILE_POT && stack.getItem() instanceof PotCartridgeItem) {
            // What the pot holds, in coin gold
            label(Text.translatable("gui.steveparty.pot.label", PotCartridgeItem.coins(stack)), centreX, centreZ, 0xFFD54A, matrices, consumers);
        }
    }

    /** The renderer of the check points: what their role shows over them (they have no face to draw). */
    public static void renderCheckPoint(BoardSpaceBlockEntity entity, float tickDelta, MatrixStack matrices,
                                        VertexConsumerProvider consumers, int light, int overlay) {
        net.minecraft.block.BlockState state = entity.getCachedState();
        if (!state.contains(fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE)) return;
        BoardSpaceType type = state.get(fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE);
        if (type == BoardSpaceType.DEFAULT || type == BoardSpaceType.TILE_START) return;
        render(entity, type, fr.lordfinn.steveparty.client.utils.BoardSpaceClientUtils.getDisplayedCartridge(entity),
                0.5, 0.5, tickDelta, matrices, consumers, light);
    }

    /** A line of text over the space, facing the camera, lit whatever the light (a sign of the board). */
    static void label(Text text, double centreX, double centreZ, int color, MatrixStack matrices, VertexConsumerProvider consumers) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;
        matrices.push();
        matrices.translate(centreX, LABEL_Y, centreZ);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float x = -font.getWidth(text) / 2f;
        // Shadowed, no backdrop (a backdrop fights with the glyphs for the same depth)
        font.draw(text, x, -4, color, true, matrix, consumers, TextRenderer.TextLayerType.NORMAL, 0,
                LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }
}
