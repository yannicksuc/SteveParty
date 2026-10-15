package fr.lordfinn.steveparty.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * The comparison signs {@code < ≤ = ≥ >} and « any » ({@code ✱}), drawn as pixels in the style of the Minecraft font's
 * digits (the font has no ≤ ≥, and its fallback glyphs are smoothed and thick): 5 x 7 font pixels on the digits'
 * height and baseline, strokes one font pixel wide, ≤ and ≥ a chevron with its bar attached under it. One set for every
 * screen and panel that shows a comparison (the Mast Base's buttons, the Party Antenna's filter...).
 * <ul>
 *     <li>in a screen: {@link #draw} at the text's place ({@code y} the text's top, as {@link DrawContext#drawText}),
 *     {@code scale} screen pixels per font pixel, the font's shadow if asked; {@link #drawCentred} in a button;</li>
 *     <li>in the world (label space, y down): {@link #world}, opaque pixels in one batch (drawn at once when
 *     {@code consumers} is an {@code Immediate}).</li>
 * </ul>
 * Inline, a sign is followed by a space then its value (« ≥ 5 »): {@link #SPACE} font pixels after its right edge.
 */
public final class ComparisonGlyphs {
    /** A sign's size and its advance to the next glyph (font pixels: the font's spacing column after it). */
    public static final int WIDTH = 5, HEIGHT = 7, ADVANCE = WIDTH + 1;
    /** From a sign's right edge to its value inline (« ≥ 5 »): its spacing column and the font's space (font pixels). */
    public static final int SPACE = 1 + 4;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    public enum Sign {
        LESS(".....", "....#", "..##.", "##...", "..##.", "....#", "....."),
        LESS_OR_EQUAL(".....", "....#", "..##.", "##...", "..##.", "....#", "#####"),
        EQUAL(".....", ".....", "#####", ".....", "#####", ".....", "....."),
        GREATER_OR_EQUAL(".....", "#....", ".##..", "...##", ".##..", "#....", "#####"),
        GREATER(".....", "#....", ".##..", "...##", ".##..", "#....", "....."),
        ANY(".....", "#.#.#", ".###.", "#####", ".###.", "#.#.#", ".....");

        private final String[] rows;

        Sign(String... rows) {
            this.rows = rows;
        }

        private boolean on(int column, int row) {
            return rows[row].charAt(column) == '#';
        }
    }

    private ComparisonGlyphs() {
    }

    /**
     * {@code sign} with its top-left corner at (x, y), {@code scale} pixels per font pixel, in {@code argb}; with the
     * font's shadow (a quarter of its colour, one font pixel down and right) if {@code shadow}.
     */
    public static void draw(DrawContext context, Sign sign, int x, int y, int argb, boolean shadow, int scale) {
        if (shadow) pixels(context, sign, x + scale, y + scale, shadowColour(argb), scale);
        pixels(context, sign, x, y, argb, scale);
    }

    /**
     * {@code sign}'s drawn pixels centred on (centreX, centreY) (a button's middle), {@code scale} pixels per font pixel,
     * no shadow: each sign centred on its ink, so that a row of them (« < ≤ = ≥ > ») shares one middle.
     */
    public static void drawCentred(DrawContext context, Sign sign, int centreX, int centreY, int argb, int scale) {
        int minColumn = WIDTH, maxColumn = -1, minRow = HEIGHT, maxRow = -1;
        for (int row = 0; row < HEIGHT; row++) {
            for (int column = 0; column < WIDTH; column++) {
                if (!sign.on(column, row)) continue;
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
            }
        }
        int inkWidth = (maxColumn - minColumn + 1) * scale, inkHeight = (maxRow - minRow + 1) * scale;
        draw(context, sign, centreX - inkWidth / 2 - minColumn * scale, centreY - inkHeight / 2 - minRow * scale, argb, false, scale);
    }

    private static void pixels(DrawContext context, Sign sign, int x, int y, int argb, int scale) {
        for (int row = 0; row < HEIGHT; row++) {
            for (int column = 0; column < WIDTH; column++) {
                if (sign.on(column, row)) context.fill(x + column * scale, y + row * scale, x + (column + 1) * scale, y + (row + 1) * scale, argb);
            }
        }
    }

    /** The font's shadow colour of {@code argb} (TextRenderer: its colour a quarter as bright). */
    public static int shadowColour(int argb) {
        return (argb & 0xFF000000) | ((argb & 0xFCFCFC) >> 2);
    }

    /**
     * {@code sign} on a world plate, its top-left corner at (x, y), {@code pixel} label units per font pixel, at depth
     * {@code z}, opaque {@code argb}.
     */
    public static void world(MatrixStack matrices, VertexConsumerProvider consumers, Sign sign, float x, float y, float pixel, float z, int argb) {
        RenderLayer layer = RenderLayer.getTextBackground();
        VertexConsumer consumer = consumers.getBuffer(layer);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (int row = 0; row < HEIGHT; row++) {
            for (int column = 0; column < WIDTH; column++) {
                if (!sign.on(column, row)) continue;
                float x0 = x + column * pixel, y0 = y + row * pixel;
                consumer.vertex(matrix, x0, y0, z).color(argb).light(LIGHT);
                consumer.vertex(matrix, x0, y0 + pixel, z).color(argb).light(LIGHT);
                consumer.vertex(matrix, x0 + pixel, y0 + pixel, z).color(argb).light(LIGHT);
                consumer.vertex(matrix, x0 + pixel, y0, z).color(argb).light(LIGHT);
            }
        }
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
    }
}
