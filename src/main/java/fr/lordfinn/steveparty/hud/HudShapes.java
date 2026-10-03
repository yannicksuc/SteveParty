package fr.lordfinn.steveparty.hud;

import java.util.HashMap;
import java.util.Map;

/**
 * The shapes of the party HUDs (the art sources: m_pill, m_disc, m_cut, m_chevron), as masks,
 * and where they are solid: the layout spaces them by their real outlines (three empty pixels between two shapes on
 * every row they share), the client paints them from the same masks. A shape's picture has {@link #PAD} pixels of
 * margin round its mask (its outline, its drop shadow, the current step's halo).
 */
public final class HudShapes {
    /** Margin of a shape's picture round its mask. */
    public static final int PAD = 4;
    /** Empty pixels between two shapes. */
    public static final int GAP = 3;

    public enum Form {
        /** Round ends (a disc when as wide as high). */
        PILL,
        /** A rectangle with its corners cut by one pixel (plates, frames). */
        CUT1,
        /** The turn bar's banner: a point on the left, a notch on the right (each fits in the one before it). */
        CHEVRON
    }

    private static final Map<Long, boolean[][]> MASKS = new HashMap<>();
    private static final Map<Long, int[][]> PROFILES = new HashMap<>();

    private HudShapes() {
    }

    private static long key(Form form, int w, int h, int rings) {
        return ((long) form.ordinal() << 48) | ((long) (w & 0xFFFF) << 32) | ((long) (h & 0xFFFF) << 16) | (rings & 0xFFFF);
    }

    /** The mask of a shape, {@code [y][x]} (cached: never change it). */
    public static boolean[][] mask(Form form, int w, int h) {
        return MASKS.computeIfAbsent(key(form, w, h, 0), k -> switch (form) {
            case PILL -> rounded(w, h, h / 2.0);
            case CUT1 -> cut(w, h, 1);
            case CHEVRON -> chevron(w, h);
        });
    }

    private static boolean[][] rounded(int w, int h, double r) {
        boolean[][] m = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double cx = Math.min(Math.max(x + 0.5, r), w - r);
                double cy = Math.min(Math.max(y + 0.5, r), h - r);
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                m[y][x] = dx * dx + dy * dy <= r * r - r * 0.4 + 0.01;
            }
        }
        return m;
    }

    private static boolean[][] cut(int w, int h, int c) {
        boolean[][] m = new boolean[h][w];
        for (boolean[] row : m) java.util.Arrays.fill(row, true);
        for (int k = 0; k < c; k++) {
            int[][] corners = {{k, 0}, {0, k}, {w - 1 - k, 0}, {w - 1, k}, {k, h - 1}, {0, h - 1 - k}, {w - 1 - k, h - 1}, {w - 1, h - 1 - k}};
            for (int[] p : corners) if (p[1] >= 0 && p[1] < h && p[0] >= 0 && p[0] < w) m[p[1]][p[0]] = false;
        }
        return m;
    }

    private static boolean[][] chevron(int w, int h) {
        int d = h / 4;
        boolean[][] m = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            int k = Math.min(y, h - 1 - y);
            int off = d - (k + 1) / 2;
            int left = Math.max(0, off), right = w - 1 - d + off;
            for (int x = left; x <= right && x < w; x++) m[y][x] = true;
        }
        return m;
    }

    /** The mask in a picture with {@link #PAD} pixels of margin. */
    public static boolean[][] padded(boolean[][] mask) {
        int h = mask.length, w = h == 0 ? 0 : mask[0].length;
        boolean[][] out = new boolean[h + 2 * PAD][w + 2 * PAD];
        for (int y = 0; y < h; y++) System.arraycopy(mask[y], 0, out[y + PAD], PAD, w);
        return out;
    }

    /** Grows a mask by {@code n} pixels (diagonals included). */
    public static boolean[][] dilate(boolean[][] m, int n) {
        boolean[][] cur = m;
        for (int i = 0; i < n; i++) {
            int h = cur.length, w = cur[0].length;
            boolean[][] o = new boolean[h][w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (!cur[y][x]) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int yy = y + dy, xx = x + dx;
                            if (yy >= 0 && yy < h && xx >= 0 && xx < w) o[yy][xx] = true;
                        }
                    }
                }
            }
            cur = o;
        }
        return cur;
    }

    /**
     * Where a shape's picture is solid, row by row: {@code [row] = {first column, last column}}, {-1, -1} for an
     * empty row. Solid: the mask, its outline and, for the current step, its {@code halo} (2 px); not the drop shadows.
     */
    public static int[][] profile(Form form, int w, int h, boolean halo) {
        return PROFILES.computeIfAbsent(key(form, w, h, halo ? 1 : 0), k -> {
            boolean[][] solid = dilate(padded(mask(form, w, h)), halo ? 3 : 1);
            int[][] rows = new int[solid.length][];
            for (int y = 0; y < solid.length; y++) {
                int first = -1, last = -1;
                for (int x = 0; x < solid[y].length; x++) {
                    if (!solid[y][x]) continue;
                    if (first < 0) first = x;
                    last = x;
                }
                rows[y] = new int[]{first, last};
            }
            return rows;
        });
    }

    /** The mock-ups' {@code odd}: a width made odd (one more when even), so that a shape has a centre column. */
    public static int odd(int w) {
        return w % 2 == 1 ? w : w + 1;
    }
}
