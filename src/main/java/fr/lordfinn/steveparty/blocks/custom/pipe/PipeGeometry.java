package fr.lordfinn.steveparty.blocks.custom.pipe;

import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The quads of a pipe block, built from its shape key ({@link PipeShape}) and kind: pixels 0 to 16, walls 1 pixel thick.
 * <ul>
 *     <li>the body is a square tube 1 to 15 (inside 2 to 14): each closed face is a wall (outside and inside), going
 *     on to the edge of the block on the sides where the pipe goes on, up to the rim at a mouth;</li>
 *     <li>between two joined faces (an elbow, a junction), the short walls closing the corner;</li>
 *     <li>a mouth: a rim 0 to 16 round the hole, {@link PipeShape#RIM} pixels long; a capped end: a flange
 *     {@link PipeShape#FLANGE} pixels long; a clamp: a small block toward the solid block beside.</li>
 * </ul>
 * <b>Texturing.</b> Every face is painted in its texture space (s, t: the block grid as vanilla maps it, so that
 * neighbouring blocks line up) from its <i>region</i>: the pixels of that face's surface, going on past the block where
 * the pipe goes on. A pixel's look only depends on its distance to the region's edge ({@link #tone}; for rims and
 * clamps, also on whether that edge is lit, top or left, or shaded): the lines of the tube follow its outline round
 * bends and junctions, never stop at a joint and never double up, and match from block to block and between kinds.
 * <ul>
 *     <li>plastic walls use a sheet of 4 x 4 tiles ({@code <color>_outer} and {@code <color>_inner}), one per set of
 *     open sides ({@link #LEFT} | {@link #RIGHT} | {@link #TOP} | {@link #BOTTOM}), painted from the same regions by
 *     {@code the art sources};</li>
 *     <li>glass walls, and every rim, flange and clamp, are cut into rectangles sampling the right texels of the
 *     block's texture (the vanilla glass one keeps its look: its border along the real edges only).</li>
 * </ul>
 */
public final class PipeGeometry {
    /** Sprites: the outside of the walls, their inside, rims and clamps. */
    public static final int OUTER = 0, INNER = 1, RIM = 2;
    /** Open sides of a face region, in texture space (a tile of the wall sheets is {@code open & 3, open >> 2}). */
    public static final int LEFT = 1, RIGHT = 2, TOP = 4, BOTTOM = 8;
    /** Tiles per row and column of a wall sheet. */
    public static final int TILES = 4;
    /** Distances to the edge told apart by {@link #tone} (farther: {@code FAR}). */
    public static final int FAR = 5;

    /** Receives each quad: its four corners (pixels, in the vanilla order of the face) and texture coordinates (0-16). */
    public interface Out {
        void quad(Direction facing, float[][] corners, float[][] uvs, int sprite, @Nullable Direction cull);
    }

    /** The pixels of a face's surface, in its texture space (they may lie past the block). */
    public interface Region {
        boolean in(int s, int t);
    }

    /** Texture coordinates of a pixel, from its tone: see {@link #desc}. */
    private interface Painter {
        int at(int s, int t, int tone);
    }

    private PipeGeometry() {}

    public static void build(int key, PipeKind kind, Out out) {
        boolean glass = !kind.isPlastic();
        int mask = PipeShape.maskOf(key);
        PipeShape.Face[] faces = PipeShape.faces(mask, PipeShape.solidOf(key));
        for (Direction dir : Direction.values()) {
            switch (faces[dir.ordinal()]) {
                case CLOSED -> wall(dir, faces, glass, out);
                case CLAMP -> {
                    wall(dir, faces, glass, out);
                    clamp(dir, glass, out);
                }
                case MOUTH -> rim(dir, PipeShape.RIM, glass, out);
                case CAPPED -> rim(dir, PipeShape.FLANGE, glass, out);
                case CONNECTED -> {
                    for (Direction side : Direction.values()) {
                        if (side.getAxis() != dir.getAxis() && faces[side.ordinal()] == PipeShape.Face.CONNECTED) armSide(dir, side, glass, out);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- regions and tones

    /**
     * The region of a tube's wall: its square {@code [e0, e1)} (1-15 outside, 2-14 inside) and, on each open side, the
     * tube going on (straight, as far as needed).
     */
    public static Region tube(int open, int e0, int e1) {
        return (s, t) -> {
            boolean inS = s >= e0 && s < e1, inT = t >= e0 && t < e1;
            if (inS && inT) return true;
            if (inT) return s < e0 ? (open & LEFT) != 0 : s >= e1 && (open & RIGHT) != 0;
            if (inS) return t < e0 ? (open & TOP) != 0 : t >= e1 && (open & BOTTOM) != 0;
            return false;
        };
    }

    /** A rectangle's region: {@code [s0, s1) x [t0, t1)}. */
    private static Region box(int s0, int s1, int t0, int t1) {
        return (s, t) -> s >= s0 && s < s1 && t >= t0 && t < t1;
    }

    /**
     * The tone of the pixel {@code (s, t)} of a region: {@code d * 4 + lit + 2 * horizontal}, {@code d} its distance to
     * the nearest pixel out of the region (0 on the edge, {@link #FAR} or more inside), {@code lit} (1) when that edge is
     * a top or left one (any of the nearest: straight above or to the left, or up and left), {@code horizontal} (2) when
     * that edge runs along s. Square distances, so
     * that lines turn round inner corners at right angles.
     */
    public static int tone(Region region, int s, int t) {
        for (int k = 1; k <= FAR; k++) {
            int found = -1;
            for (int dt = -k; dt <= k; dt++) {
                for (int ds = -k; ds <= k; ds++) {
                    if (Math.max(Math.abs(ds), Math.abs(dt)) != k || region.in(s + ds, t + dt)) continue;
                    boolean lit = Math.abs(ds) == Math.abs(dt) ? ds < 0 && dt < 0 : Math.abs(ds) > Math.abs(dt) ? ds < 0 : dt < 0;
                    int code = (lit ? 1 : 0) | (Math.abs(dt) >= Math.abs(ds) ? 2 : 0);
                    if (found < 0 || lit && (found & 1) == 0) found = code;
                }
            }
            if (found >= 0) return (k - 1) * 4 + found;
        }
        return FAR * 4;
    }

    /** Texture coordinates: u (and v) either the pixel's own plus {@code shift}, or that of one texel (stretched). */
    private static int desc(boolean uTexel, int u, boolean vTexel, int v) {
        return (uTexel ? 1 : 0) | (u + 128) << 1 | (vTexel ? 1 : 0) << 9 | (v + 128) << 10;
    }

    private static float uvOf(int desc, boolean v, float coord) {
        int shift = v ? 10 : 1, mode = v ? 1 << 9 : 1;
        int value = (desc >> shift & 0xFF) - 128;
        return (desc & mode) != 0 ? value + 0.5f : coord + value;
    }

    /** Keeps a pixel off the border rows and columns of the texture (the glass's own frame). */
    private static int inward(int x) {
        return x == 0 ? 1 : x == 15 ? -1 : 0;
    }

    /** Glass: its border texels on the region's edges only, its middle elsewhere. */
    private static final Painter GLASS = (s, t, tone) -> {
        boolean lit = (tone & 1) != 0, horizontal = (tone & 2) != 0;
        if (tone >> 2 == 0) {
            return horizontal ? desc(false, inward(s), true, lit ? 0 : 15) : desc(true, lit ? 0 : 15, false, inward(t));
        }
        return desc(false, inward(s), false, inward(t));
    };

    /**
     * The inside of a glass wall: its middle only. Its edges are seen through the outside, right beside the outside's
     * own: drawn, they would double every line of the tube.
     */
    private static final Painter GLASS_INSIDE = (s, t, tone) -> desc(false, inward(s), false, inward(t));

    /** A plastic block's look (rims, clamps): light top and left edges, dark bottom and right ones (2 pixels). */
    private static final Painter PLASTIC = (s, t, tone) -> {
        int d = tone >> 2;
        boolean lit = (tone & 1) != 0;
        int row = d == 0 ? (lit ? 0 : 15) : d == 1 && !lit ? 14 : 8;
        return desc(true, 8, true, row);
    };

    // ---------------------------------------------------------------- texture space

    private static boolean positive(Direction dir) {
        return dir.getDirection() == Direction.AxisDirection.POSITIVE;
    }

    private static int axis(Direction dir) {
        return dir.getAxis().ordinal();
    }

    private static Direction dir(int axis, boolean positive) {
        return Direction.from(Direction.Axis.values()[axis], positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    /** The world axis along a face's s (u), and whether s runs against it (vanilla's block grid, see {@link #box}). */
    private static int sAxis(Direction facing) {
        return facing.getAxis() == Direction.Axis.X ? 2 : 0;
    }

    private static boolean sFlip(Direction facing) {
        return facing == Direction.NORTH || facing == Direction.EAST;
    }

    private static int tAxis(Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? 2 : 1;
    }

    private static boolean tFlip(Direction facing) {
        return facing != Direction.UP;
    }

    /** Which side of a face's texture the world direction {@code toward} (across the face) goes to. */
    public static int side(Direction facing, Direction toward) {
        int x = axis(toward);
        boolean pos = positive(toward);
        if (x == sAxis(facing)) return pos != sFlip(facing) ? RIGHT : LEFT;
        return pos != tFlip(facing) ? BOTTOM : TOP;
    }

    /** A rectangle on the plane {@code plane} of a face, in its texture space. */
    private static void face(Direction facing, float plane, float s0, float s1, float t0, float t1, int sprite, int desc, Out out) {
        float[] from = new float[3], to = new float[3];
        int a = axis(facing), sa = sAxis(facing), ta = tAxis(facing);
        from[a] = to[a] = plane;
        from[sa] = sFlip(facing) ? 16 - s1 : s0;
        to[sa] = sFlip(facing) ? 16 - s0 : s1;
        from[ta] = tFlip(facing) ? 16 - t1 : t0;
        to[ta] = tFlip(facing) ? 16 - t0 : t1;
        box(facing, from, to, sprite, (s, t) -> new float[]{uvOf(desc, false, s), uvOf(desc, true, t)}, out);
    }

    /** A world rectangle (flat box) in the face's texture space: {s0, s1, t0, t1}. */
    private static int[] texRect(Direction facing, float[] from, float[] to) {
        int sa = sAxis(facing), ta = tAxis(facing);
        int s0 = Math.round(sFlip(facing) ? 16 - to[sa] : from[sa]), s1 = Math.round(sFlip(facing) ? 16 - from[sa] : to[sa]);
        int t0 = Math.round(tFlip(facing) ? 16 - to[ta] : from[ta]), t1 = Math.round(tFlip(facing) ? 16 - from[ta] : to[ta]);
        return new int[]{s0, s1, t0, t1};
    }

    /**
     * The world rectangles {@code rects} (from, to) of a face, painted from its region: each pixel's texel from its tone,
     * pixels alike merged into as few quads as possible.
     */
    private static void paint(Direction facing, List<float[][]> rects, Region region, int sprite, Painter painter, Out out) {
        int[][] grid = new int[16][16];
        boolean[][] set = new boolean[16][16];
        float plane = rects.getFirst()[0][axis(facing)];
        for (float[][] rect : rects) {
            int[] r = texRect(facing, rect[0], rect[1]);
            for (int t = r[2]; t < r[3]; t++) {
                for (int s = r[0]; s < r[1]; s++) {
                    grid[t][s] = painter.at(s, t, tone(region, s, t));
                    set[t][s] = true;
                }
            }
        }
        for (int t = 0; t < 16; t++) {
            for (int s = 0; s < 16; s++) {
                if (!set[t][s]) continue;
                int d = grid[t][s], s1 = s + 1, t1 = t + 1;
                while (s1 < 16 && set[t][s1] && grid[t][s1] == d) s1++;
                grow:
                while (t1 < 16) {
                    for (int x = s; x < s1; x++) if (!set[t1][x] || grid[t1][x] != d) break grow;
                    t1++;
                }
                for (int y = t; y < t1; y++) for (int x = s; x < s1; x++) set[y][x] = false;
                face(facing, plane, s, s1, t, t1, sprite, d, out);
            }
        }
    }

    /** A plastic wall's rectangle, textured by the tile of its region's open sides (block grid inside the tile). */
    private static void tiled(Direction facing, float[][] rect, int sprite, int open, Out out) {
        int col = open % TILES, row = open / TILES;
        box(facing, rect[0], rect[1], sprite, (s, t) -> new float[]{(col * 16 + s) / TILES, (row * 16 + t) / TILES}, out);
    }

    // ---------------------------------------------------------------- parts

    private static boolean open(PipeShape.Face face) {
        return face == PipeShape.Face.CONNECTED || face == PipeShape.Face.MOUTH || face == PipeShape.Face.CAPPED;
    }

    /** How far a wall goes toward a face: to the block's edge, the rim, the flange, or the corner of the body. */
    private static float extent(PipeShape.Face toward, boolean positive, boolean outer) {
        float reach = switch (toward) {
            case CONNECTED -> 8;
            case MOUTH -> 8 - PipeShape.RIM;
            case CAPPED -> 8 - PipeShape.FLANGE;
            default -> outer ? 7 : 6;
        };
        return positive ? 8 + reach : 8 - reach;
    }

    private static float[][] rect(int a, float plane, int x, float x0, float x1, int y, float y0, float y1) {
        float[] from = new float[3], to = new float[3];
        from[a] = to[a] = plane;
        from[x] = x0;
        to[x] = x1;
        from[y] = y0;
        to[y] = y1;
        return new float[][]{from, to};
    }

    /**
     * A closed face: the wall, outside and inside. It follows the tube exactly (never over its outline): the square of
     * the body, and a strip toward each side where the pipe goes on (no corner square at a bend). Its region's open
     * sides are those where the pipe goes on (joined, or to a rim): no line there, the tube's texture goes on.
     */
    private static void wall(Direction dir, PipeShape.Face[] faces, boolean glass, Out out) {
        int a = axis(dir), b = (a + 1) % 3, c = (a + 2) % 3;
        for (boolean outer : new boolean[]{true, false}) {
            Direction facing = outer ? dir : dir.getOpposite();
            float plane = outer ? (positive(dir) ? 15 : 1) : (positive(dir) ? 14 : 2);
            int e0 = outer ? 1 : 2;
            float[] lo = new float[3], hi = new float[3], cLo = new float[3], cHi = new float[3];
            int open = 0;
            for (int x : new int[]{b, c}) {
                lo[x] = extent(faces[dir(x, false).ordinal()], false, outer);
                hi[x] = extent(faces[dir(x, true).ordinal()], true, outer);
                cLo[x] = Math.max(lo[x], e0);
                cHi[x] = Math.min(hi[x], 16 - e0);
                for (boolean pos : new boolean[]{false, true}) {
                    if (open(faces[dir(x, pos).ordinal()])) open |= side(facing, dir(x, pos));
                }
            }
            List<float[][]> rects = new ArrayList<>(5);
            rects.add(rect(a, plane, b, cLo[b], cHi[b], c, cLo[c], cHi[c]));
            if (lo[b] < cLo[b]) rects.add(rect(a, plane, b, lo[b], cLo[b], c, cLo[c], cHi[c]));
            if (hi[b] > cHi[b]) rects.add(rect(a, plane, b, cHi[b], hi[b], c, cLo[c], cHi[c]));
            if (lo[c] < cLo[c]) rects.add(rect(a, plane, c, lo[c], cLo[c], b, cLo[b], cHi[b]));
            if (hi[c] > cHi[c]) rects.add(rect(a, plane, c, cHi[c], hi[c], b, cLo[b], cHi[b]));
            int sprite = outer ? OUTER : INNER;
            if (glass) {
                paint(facing, rects, tube(open, e0, 16 - e0), sprite, outer ? GLASS : GLASS_INSIDE, out);
            } else {
                for (float[][] rect : rects) tiled(facing, rect, sprite, open, out);
            }
        }
    }

    /**
     * Where two joined faces meet: the wall of the arm on {@code arm} on the side of the arm on {@code side}, outside
     * and inside. It is the arm's wall going on: its region is a tube along the arm.
     */
    private static void armSide(Direction arm, Direction side, boolean glass, Out out) {
        int a = axis(arm), s = axis(side), t = 3 - a - s;
        boolean armPos = positive(arm), sidePos = positive(side);
        for (boolean outer : new boolean[]{true, false}) {
            Direction facing = outer ? side : side.getOpposite();
            int e0 = outer ? 1 : 2;
            float[] from = new float[3], to = new float[3];
            from[a] = armPos ? 16 - e0 : 0;
            to[a] = armPos ? 16 : e0;
            from[s] = to[s] = sidePos ? 16 - e0 : e0;
            from[t] = e0;
            to[t] = 16 - e0;
            int open = side(facing, arm) | side(facing, arm.getOpposite());
            int sprite = outer ? OUTER : INNER;
            if (glass) {
                paint(facing, List.<float[][]>of(new float[][]{from, to}), tube(open, e0, 16 - e0), sprite, outer ? GLASS : GLASS_INSIDE, out);
            } else {
                tiled(facing, new float[][]{from, to}, sprite, open, out);
            }
        }
    }

    /** A part of a rim or clamp: a flat rectangle painted as a small block face of its own (edges all round). */
    private static void part(Direction facing, float[] from, float[] to, boolean glass, Out out) {
        int[] r = texRect(facing, from, to);
        paint(facing, List.<float[][]>of(new float[][]{from, to}), box(r[0], r[1], r[2], r[3]), RIM, glass ? GLASS : PLASTIC, out);
    }

    /** A rim (mouth) or flange (capped end) of {@code length} pixels on {@code dir}. */
    private static void rim(Direction dir, int length, boolean glass, Out out) {
        int a = axis(dir), b = (a + 1) % 3, c = (a + 2) % 3;
        boolean pos = positive(dir);
        float lo = pos ? 16 - length : 0, hi = pos ? 16 : length;
        for (int side : new int[]{b, c}) {
            int third = side == b ? c : b;
            for (boolean sidePos : new boolean[]{false, true}) {
                float[] from = new float[3], to = new float[3];
                from[a] = lo;
                to[a] = hi;
                // Outside
                from[side] = to[side] = sidePos ? 16 : 0;
                from[third] = 0;
                to[third] = 16;
                part(dir(side, sidePos), from.clone(), to.clone(), glass, out);
                // Inside the hole
                from[side] = to[side] = sidePos ? 14 : 2;
                from[third] = 2;
                to[third] = 14;
                part(dir(side, !sidePos), from, to, glass, out);
            }
        }
        annulus(dir, pos ? 16 : 0, 2, glass, out);
        annulus(dir.getOpposite(), pos ? 16 - length : length, 1, glass, out);
    }

    /** A square ring 0 to 16 round a hole {@code width} pixels in from the edge, on the plane {@code plane}. */
    private static void annulus(Direction facing, float plane, int width, boolean glass, Out out) {
        int a = axis(facing), b = (a + 1) % 3, c = (a + 2) % 3;
        float[][] parts = {{0, 16, 0, width}, {0, 16, 16 - width, 16}, {0, width, width, 16 - width}, {16 - width, 16, width, 16 - width}};
        List<float[][]> rects = new ArrayList<>(4);
        for (float[] p : parts) rects.add(rect(a, plane, b, p[0], p[1], c, p[2], p[3]));
        Region ring = (s, t) -> s >= 0 && s < 16 && t >= 0 && t < 16 && (s < width || s >= 16 - width || t < width || t >= 16 - width);
        paint(facing, rects, ring, RIM, glass ? GLASS : PLASTIC, out);
    }

    /** The clamp holding the pipe to the solid block on {@code dir}. */
    private static void clamp(Direction dir, boolean glass, Out out) {
        int a = axis(dir), b = (a + 1) % 3, c = (a + 2) % 3;
        boolean pos = positive(dir);
        float lo = pos ? 15 : 0, hi = pos ? 16 : 1;
        for (int side : new int[]{b, c}) {
            int third = side == b ? c : b;
            for (boolean sidePos : new boolean[]{false, true}) {
                float[] from = new float[3], to = new float[3];
                from[a] = lo;
                to[a] = hi;
                from[side] = to[side] = sidePos ? 11 : 5;
                from[third] = 5;
                to[third] = 11;
                part(dir(side, sidePos), from, to, glass, out);
            }
        }
        float[] from = new float[3], to = new float[3];
        from[a] = to[a] = pos ? 16 : 0;
        from[b] = from[c] = 5;
        to[b] = to[c] = 11;
        part(dir, from, to, glass, out);
    }

    private interface Uv {
        float[] at(float s, float t);
    }

    /**
     * One quad on a flat box ({@code from} and {@code to} equal on the facing's axis), facing {@code facing}. Its
     * texture coordinates from its corners' place on the block grid (s, t), like vanilla's.
     */
    private static void box(Direction facing, float[] from, float[] to, int sprite, Uv uv, Out out) {
        float x0 = from[0], y0 = from[1], z0 = from[2], x1 = to[0], y1 = to[1], z1 = to[2];
        float[][] c = switch (facing) {
            case DOWN -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case UP -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case NORTH -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
            case EAST -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
        };
        float[][] uvs = new float[4][];
        for (int i = 0; i < 4; i++) {
            float x = c[i][0], y = c[i][1], z = c[i][2];
            float s, t;
            switch (facing) {
                case DOWN -> { s = x; t = 16 - z; }
                case UP -> { s = x; t = z; }
                case NORTH -> { s = 16 - x; t = 16 - y; }
                case SOUTH -> { s = x; t = 16 - y; }
                case WEST -> { s = z; t = 16 - y; }
                default -> { s = 16 - z; t = 16 - y; }
            }
            uvs[i] = uv.at(s, t);
        }
        float plane = from[axis(facing)];
        Direction cull = (positive(facing) ? plane == 16 : plane == 0) ? facing : null;
        out.quad(facing, c, uvs, sprite, cull);
    }
}
