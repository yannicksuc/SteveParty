package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.client.screens.AntennaIcons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

/**
 * Over a Party Antenna, for a player with the Wrench in hand: what it listens to, readable from afar without opening
 * it. A dark plate edged with the colour of the moment's family, the moment's item on it; under it its filter
 * ({@code ≥5}) if it has one, and an hourglass if it waits.
 * <p>
 * Drawn by the antenna's block entity renderer, facing the camera. Everything is opaque and writes depth: the plate
 * first (its edge one step behind it), then what is on it one step toward the camera (minecraft-rendering: a detail
 * on a plate is opaque, never a translucent colour that writes depth).
 */
final class AntennaBubble {
    /** Block per bubble pixel. */
    private static final float SCALE = 1f / 48f;
    /** The plate's half size and the item on it (bubble pixels). */
    private static final float HALF = 10, ITEM = 14;
    private static final int PLATE = 0xFF26262B;
    /** Steps toward the camera (bubble pixels: +z goes away from it in this space). */
    private static final float EDGE_Z = 0.6f, PLATE_Z = 0.3f;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private AntennaBubble() {
    }

    static void draw(PartyBellBlockEntity antenna, MatrixStack matrices, VertexConsumerProvider consumers,
                     BlockEntityRenderDispatcher dispatcher) {
        PartyMoment moment = antenna.getMoment();
        int family = AntennaIcons.colour(moment.family());
        matrices.push();
        matrices.translate(0.5, 1.55, 0.5);
        matrices.multiply(dispatcher.camera.getRotation());
        matrices.scale(SCALE, -SCALE, SCALE);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        VertexConsumer plate = consumers.getBuffer(RenderLayer.getTextBackground());
        quad(plate, matrix, -HALF - 1, -HALF - 1, HALF + 1, HALF + 1, EDGE_Z, family);
        quad(plate, matrix, -HALF, -HALF, HALF, HALF, PLATE_Z, PLATE);

        // The item, flattened onto the plate as in an inventory
        matrices.push();
        matrices.scale(ITEM, -ITEM, 0.01f);
        MinecraftClient.getInstance().getItemRenderer().renderItem(AntennaIcons.item(moment), ModelTransformationMode.GUI,
                LIGHT, OverlayTexture.DEFAULT_UV, matrices, consumers, antenna.getWorld(), 0);
        matrices.pop();

        // Under the plate: its filter, its hourglass
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        String filter = switch (antenna.getFilter()) {
            case ANY -> "";
            case EQUAL -> "=" + antenna.getFilterValue();
            case AT_LEAST -> "≥" + antenna.getFilterValue();
            case AT_MOST -> "≤" + antenna.getFilterValue();
        };
        boolean waits = antenna.isWaiting();
        int width = font.getWidth(filter) + (waits ? 12 : 0);
        if (width > 0) {
            float x = -width / 2f, y = HALF + 3;
            if (!filter.isEmpty()) {
                font.draw(Text.literal(filter), x, y, 0xFFFFFFFF, false, matrix, consumers, TextRenderer.TextLayerType.NORMAL, 0xC0000000, LIGHT);
                x += font.getWidth(filter) + 2;
            }
            if (waits) icon(consumers, matrix, AntennaIcons.WAIT, x, y - 1, 10);
        }
        matrices.pop();
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int argb) {
        buffer.vertex(matrix, x0, y0, z).color(argb).light(LIGHT);
        buffer.vertex(matrix, x0, y1, z).color(argb).light(LIGHT);
        buffer.vertex(matrix, x1, y1, z).color(argb).light(LIGHT);
        buffer.vertex(matrix, x1, y0, z).color(argb).light(LIGHT);
    }

    /** A cell of the antenna's sheet, {@code size} bubble pixels wide. */
    private static void icon(VertexConsumerProvider consumers, Matrix4f matrix, int cell, float x, float y, float size) {
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getText(AntennaIcons.SHEET));
        float u0 = (cell % 16) * 16 / 256f, v0 = (cell / 16) * 16 / 64f, u1 = u0 + 16 / 256f, v1 = v0 + 16 / 64f;
        buffer.vertex(matrix, x, y, 0).color(0xFFFFFFFF).texture(u0, v0).light(LIGHT);
        buffer.vertex(matrix, x, y + size, 0).color(0xFFFFFFFF).texture(u0, v1).light(LIGHT);
        buffer.vertex(matrix, x + size, y + size, 0).color(0xFFFFFFFF).texture(u1, v1).light(LIGHT);
        buffer.vertex(matrix, x + size, y, 0).color(0xFFFFFFFF).texture(u1, v0).light(LIGHT);
    }
}
