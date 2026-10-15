package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.client.screens.AntennaIcons;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
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
    /** The bubble's centre above the block: clear of the dish, whichever way it turns. */
    private static final double BUBBLE_Y = 1.8;
    /** Depths of the edge and the plate behind what is on them (bubble pixels: +z comes toward the camera in this space). */
    private static final float EDGE_Z = -0.6f, PLATE_Z = -0.3f;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private AntennaBubble() {
    }

    static void draw(PartyBellBlockEntity antenna, MatrixStack matrices, VertexConsumerProvider consumers,
                     BlockEntityRenderDispatcher dispatcher) {
        PartyMoment moment = antenna.getMoment();
        int family = AntennaIcons.colour(moment.family());
        matrices.push();
        matrices.translate(0.5, BUBBLE_Y, 0.5);
        matrices.multiply(dispatcher.camera.getRotation());
        matrices.scale(SCALE, -SCALE, SCALE);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        VertexConsumer plate = consumers.getBuffer(RenderLayer.getTextBackground());
        quad(plate, matrix, -HALF - 1, -HALF - 1, HALF + 1, HALF + 1, EDGE_Z, family);
        quad(plate, matrix, -HALF, -HALF, HALF, HALF, PLATE_Z, PLATE);

        // The item, flattened onto the plate as in an inventory, solid (every layer cutout) and lit from above whichever
        // way the bubble turns: as bright as in the screen
        matrices.push();
        float size = ITEM * AntennaIcons.scale(moment);
        matrices.scale(size, -size, 0.01f);
        MinecraftClient.getInstance().getItemRenderer().renderItem(AntennaIcons.item(moment), ModelTransformationMode.GUI,
                LIGHT, OverlayTexture.DEFAULT_UV, matrices, layer -> new LitFromAbove(consumers.getBuffer(TexturedRenderLayers.getEntityCutout())), antenna.getWorld(), 0);
        matrices.pop();
        int badge = AntennaIcons.badge(moment);
        if (badge >= 0) icon(consumers, matrix, badge, -HALF + 2, -HALF + 2, 16, 0.2f);

        // Under the plate, on a dark strip: its filter (its sign and value, in pixels) and its hourglass
        boolean filtered = antenna.getFilter() != PartyBellBlockEntity.Filter.ANY, waits = antenna.isWaiting();
        if (filtered || waits) {
            String value = Integer.toString(antenna.getFilterValue());
            float width = (filtered ? 5 + value.length() * 4 - 1 : 0) + (filtered && waits ? 2 : 0) + (waits ? 10 : 0);
            float x = Math.round(-width / 2f), y = HALF + 3;
            // Edged as the plate above it, joined to it
            VertexConsumer strip = consumers.getBuffer(RenderLayer.getTextBackground());
            quad(strip, matrix, x - 3, y - 3, x + width + 3, y + 13, EDGE_Z, family);
            quad(strip, matrix, x - 2, y - 2, x + width + 2, y + 12, PLATE_Z, PLATE);
            if (filtered) {
                int[] sign = switch (antenna.getFilter()) {
                    case EQUAL -> EQUAL;
                    case AT_LEAST -> AT_LEAST;
                    default -> AT_MOST;
                };
                pixels(consumers, matrix, sign, 3, x, y + 2);
                x += 5;
                for (char digit : value.toCharArray()) {
                    pixels(consumers, matrix, DIGITS[digit - '0'], 3, x, y + 2);
                    x += 4;
                }
                x += 1;
            }
            if (waits) icon(consumers, matrix, AntennaIcons.WAIT, x, y, 10, 0);
        }
        matrices.pop();
    }

    /** The digits 0-9 and the filter's signs in 3 x 5 pixels (rows from the top, bits from the left). */
    private static final int[][] DIGITS = {
            {7, 5, 5, 5, 7}, {2, 6, 2, 2, 7}, {7, 1, 7, 4, 7}, {7, 1, 3, 1, 7}, {5, 5, 7, 1, 1},
            {7, 4, 7, 1, 7}, {7, 4, 7, 5, 7}, {7, 1, 1, 2, 2}, {7, 5, 7, 5, 7}, {7, 5, 7, 1, 7}};
    private static final int[] EQUAL = {0, 7, 0, 7, 0}, AT_LEAST = {4, 2, 4, 0, 7}, AT_MOST = {1, 2, 1, 0, 7};

    /** A 3 x 5 bitmap in white pixels, 1.2 bubble pixels each. */
    private static void pixels(VertexConsumerProvider consumers, Matrix4f matrix, int[] rows, int width, float x, float y) {
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getTextBackground());
        float px = 1.2f;
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < width; col++) {
                if ((rows[row] >> (width - 1 - col) & 1) == 0) continue;
                quad(buffer, matrix, x + col * px, y + row * px, x + (col + 1) * px, y + (row + 1) * px, 0, 0xFFFFFFFF);
            }
        }
    }

    /** A buffer whose normals all point up: the item gets the world's full light, never its shaded side. */
    private record LitFromAbove(VertexConsumer delegate) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(0, 1, 0);
            return this;
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int argb) {
        buffer.vertex(matrix, x0, y0, z).color(argb).light(LIGHT);
        buffer.vertex(matrix, x0, y1, z).color(argb).light(LIGHT);
        buffer.vertex(matrix, x1, y1, z).color(argb).light(LIGHT);
        buffer.vertex(matrix, x1, y0, z).color(argb).light(LIGHT);
    }

    /** A cell of the antenna's sheet, {@code size} bubble pixels wide. */
    private static void icon(VertexConsumerProvider consumers, Matrix4f matrix, int cell, float x, float y, float size, float z) {
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getText(AntennaIcons.SHEET));
        float u0 = (cell % 16) * 16 / 256f, v0 = (cell / 16) * 16 / 64f, u1 = u0 + 16 / 256f, v1 = v0 + 16 / 64f;
        buffer.vertex(matrix, x, y, z).color(0xFFFFFFFF).texture(u0, v0).light(LIGHT);
        buffer.vertex(matrix, x, y + size, z).color(0xFFFFFFFF).texture(u0, v1).light(LIGHT);
        buffer.vertex(matrix, x + size, y + size, z).color(0xFFFFFFFF).texture(u1, v1).light(LIGHT);
        buffer.vertex(matrix, x + size, y, z).color(0xFFFFFFFF).texture(u1, v0).light(LIGHT);
    }
}
