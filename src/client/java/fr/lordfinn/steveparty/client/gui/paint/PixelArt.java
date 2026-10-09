package fr.lordfinn.steveparty.client.gui.paint;

import fr.lordfinn.steveparty.hud.HudShapes;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.gui.DrawContext;

import java.util.Arrays;
import java.util.Map;

/**
 * The pixel-art kit's painting, shared by its themes ({@code ConsolePaint} for the controllers' screens,
 * {@code HudPaint} for the party HUDs): shapes as masks ({@code [y][x]}, true inside), painted the way the approved
 * mock-ups paint them (the art sources' {@code shape}) into pictures ({@code [y][x]} ARGB, 0: nothing), and small
 * icons from rows of characters. Pictures are turned into textures by {@link PaintedTextures}.
 */
public final class PixelArt {
    /** {@link #paint} flags: a soft drop shadow under the shape, its 1 px outline, its glossy band. */
    public static final int SHADOW = 1, OUTLINE = 2, BAND = 4;

    private PixelArt() {
    }

    // ------------------------------------------------------------------ masks

    /** A rectangle with its corners cut by {@code c} pixels (buttons: 1, panels: 2). */
    public static boolean[][] cut(int w, int h, int c) {
        boolean[][] m = new boolean[h][w];
        for (boolean[] row : m) Arrays.fill(row, true);
        for (int k = 0; k < c; k++) {
            int[][] corners = {{k, 0}, {0, k}, {w - 1 - k, 0}, {w - 1, k}, {k, h - 1}, {0, h - 1 - k}, {w - 1 - k, h - 1}, {w - 1, h - 1 - k}};
            for (int[] p : corners) if (p[1] >= 0 && p[1] < h && p[0] >= 0 && p[0] < w) m[p[1]][p[0]] = false;
        }
        return m;
    }

    /** {@code inner} with one pixel of margin all round, in a {@code w} x {@code h} mask: room for its outline. */
    public static boolean[][] margin(boolean[][] inner, int w, int h) {
        boolean[][] m = new boolean[h][w];
        for (int y = 0; y < inner.length; y++) System.arraycopy(inner[y], 0, m[y + 1], 1, inner[y].length);
        return m;
    }

    /** The mask holds ({@code x}, {@code y}) (false outside it). */
    public static boolean in(boolean[][] m, int x, int y) {
        return y >= 0 && y < m.length && x >= 0 && x < m[0].length && m[y][x];
    }

    /** One of the 8 pixels round ({@code x}, {@code y}), or itself, is in the mask. */
    public static boolean touches(boolean[][] m, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) if (in(m, x + dx, y + dy)) return true;
        return false;
    }

    // ------------------------------------------------------------------ painting

    /** The mock-ups' {@code shape}, as a new picture the size of the mask: see {@link #paint(int[][], boolean[][], Ramp, int, int)}. */
    public static int[][] paint(boolean[][] m, Ramp ramp, int bevel, int flags) {
        int[][] out = new int[m.length][m[0].length];
        paint(out, m, ramp, bevel, flags);
        return out;
    }

    /**
     * The mock-ups' {@code shape}, into {@code out} (the mask's size): the drop shadow ({@link #SHADOW}: two rows under
     * the shape, outline included), the outline ({@link #OUTLINE}: the 8 pixels round the mask, diagonals included), the
     * body, the highlight on the top and left {@code bevel} pixels then the shadow on the bottom and right ones, and the
     * glossy band ({@link #BAND}: the two top rows of each column, two rows lower, not on the side edges).
     */
    public static void paint(int[][] out, boolean[][] m, Ramp ramp, int bevel, int flags) {
        boolean outline = (flags & OUTLINE) != 0;
        int h = m.length, w = m[0].length;
        if ((flags & SHADOW) != 0) {
            boolean[][] base = outline ? HudShapes.dilate(m, 1) : m;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (base[y][x]) continue;
                    if (y >= 2 && base[y - 2][x] && out[y][x] == 0) out[y][x] = 0x32000000;
                    if (y >= 1 && base[y - 1][x]) out[y][x] = 0x69000000;
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (m[y][x]) out[y][x] = ramp.body();
                else if (outline && touches(m, x, y)) out[y][x] = ramp.outline();
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y][x]) continue;
                for (int k = 1; k <= bevel; k++) if (!in(m, x, y - k) || !in(m, x - k, y)) out[y][x] = ramp.hi();
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y][x]) continue;
                for (int k = 1; k <= bevel; k++) if (!in(m, x, y + k) || !in(m, x + k, y)) out[y][x] = ramp.shadow();
            }
        }
        if ((flags & BAND) != 0) {
            int light = Argb.opaque(Argb.lerp(ramp.body(), 0xFFFFFFFF, 0.45f));
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!m[y][x] || isTop(m, x, y) || !isTop(m, x, y - 2)) continue;
                    if (!in(m, x - 1, y) || !in(m, x + 1, y)) continue;
                    out[y][x] = light;
                }
            }
        }
    }

    /** One of the two top rows of its column: in the shape, the pixel two rows above it is not. */
    private static boolean isTop(boolean[][] m, int x, int y) {
        return in(m, x, y) && !in(m, x, y - 2);
    }

    /** A small icon from rows of characters, each one a colour (others: nothing), as wide as its longest row. */
    public static int[][] pattern(String[] rows, Map<Character, Integer> colours) {
        int w = 0;
        for (String row : rows) w = Math.max(w, row.length());
        int[][] out = new int[rows.length][w];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                Integer c = colours.get(rows[y].charAt(x));
                if (c != null) out[y][x] = c;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ drawn as is

    /**
     * A sunken box (a slot: 18 x 18 for {@code w} = {@code h} = 17): its body, a dark top and left edge (its corner cut)
     * and a light bottom and right one, one pixel past {@code w} and {@code h}.
     */
    public static void inset(DrawContext context, int x, int y, int w, int h, int body, int edge, int low) {
        context.fill(x + 1, y + 1, x + w, y + h, body);
        context.fill(x + 2, y, x + w, y + 1, edge);
        context.fill(x, y + 2, x + 1, y + h, edge);
        context.fill(x + 1, y + 1, x + 2, y + 2, edge);
        context.fill(x + 1, y + h, x + w + 1, y + h + 1, low);
        context.fill(x + w, y + 1, x + w + 1, y + h + 1, low);
    }
}
