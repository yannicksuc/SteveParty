package fr.lordfinn.steveparty.blocks.custom.pipe;

import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The quads of a pipe block, built from its shape key ({@link PipeShape}): pixels 0 to 16, walls 1 pixel thick.
 * <ul>
 *     <li>the body is a square tube 1 to 15 (inside 2 to 14): each closed face is a wall (outside and inside), going
 *     on to the edge of the block on the sides where the pipe goes on, up to the rim at a mouth;</li>
 *     <li>between two joined faces (an elbow, a junction), the short walls closing the corner;</li>
 *     <li>a mouth: a rim 0 to 16 round the hole, {@link PipeShape#RIM} pixels long; a capped end: a flange
 *     {@link PipeShape#FLANGE} pixels long; a clamp: a small block toward the solid block beside.</li>
 * </ul>
 * Walls running along the pipe take the body texture along it (across = u, along = v); other walls the cap texture;
 * rims and clamps the plastic block texture, mapped from the block's grid.
 */
public final class PipeGeometry {
    public static final int BODY = 0, CAP = 1, INNER = 2, RIM = 3, PLAIN = 4;

    /** Receives each quad: its four corners (pixels, in the vanilla order of the face) and texture coordinates. */
    public interface Out {
        void quad(Direction facing, float[][] corners, float[][] uvs, int sprite, @Nullable Direction cull);
    }

    private PipeGeometry() {}

    public static void build(int key, Out out) {
        int mask = PipeShape.maskOf(key);
        PipeShape.Face[] faces = PipeShape.faces(mask, PipeShape.solidOf(key));
        for (Direction dir : Direction.values()) {
            switch (faces[dir.ordinal()]) {
                case CLOSED -> wall(dir, faces, out);
                case CLAMP -> {
                    wall(dir, faces, out);
                    clamp(dir, out);
                }
                case MOUTH -> rim(dir, PipeShape.RIM, out);
                case CAPPED -> rim(dir, PipeShape.FLANGE, out);
                case CONNECTED -> {
                    for (Direction side : Direction.values()) {
                        if (side.getAxis() != dir.getAxis() && faces[side.ordinal()] == PipeShape.Face.CONNECTED) armSide(dir, side, out);
                    }
                }
            }
        }
    }

    private static boolean positive(Direction dir) {
        return dir.getDirection() == Direction.AxisDirection.POSITIVE;
    }

    private static int axis(Direction dir) {
        return dir.getAxis().ordinal();
    }

    private static Direction dir(int axis, boolean positive) {
        return Direction.from(Direction.Axis.values()[axis], positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

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

    /**
     * A closed face: the wall, outside and inside. It follows the tube exactly (never over its outline): the square of
     * the body, and a strip toward each side where the pipe goes on (a cross, no corner square at a bend). Its texture
     * is the tube's going on: along the pipe on a straight length; at a bend or a junction, each quarter of the square
     * takes the edge lines of its own real edges only (no doubled border where two branches meet).
     */
    private static void wall(Direction dir, PipeShape.Face[] faces, Out out) {
        int a = axis(dir), b = (a + 1) % 3, c = (a + 2) % 3;
        int openAxes = 0, along = -1;
        for (int x : new int[]{b, c}) {
            if (open(faces[dir(x, false).ordinal()]) || open(faces[dir(x, true).ordinal()])) {
                openAxes++;
                along = x;
            }
        }
        for (boolean outer : new boolean[]{true, false}) {
            Direction facing = outer ? dir : dir.getOpposite();
            float plane = outer ? (positive(dir) ? 15 : 1) : (positive(dir) ? 14 : 2);
            float core0 = outer ? 1 : 2, core1 = outer ? 15 : 14;
            float[] lo = new float[3], hi = new float[3], cLo = new float[3], cHi = new float[3];
            for (int x : new int[]{b, c}) {
                lo[x] = extent(faces[dir(x, false).ordinal()], false, outer);
                hi[x] = extent(faces[dir(x, true).ordinal()], true, outer);
                cLo[x] = Math.max(lo[x], core0);
                cHi[x] = Math.min(hi[x], core1);
            }
            int body = outer ? BODY : INNER;
            // The square of the body
            if (openAxes == 2) {
                for (boolean highB : new boolean[]{false, true}) {
                    for (boolean highC : new boolean[]{false, true}) {
                        float b0 = highB ? 8 : cLo[b], b1 = highB ? cHi[b] : 8, c0 = highC ? 8 : cLo[c], c1 = highC ? cHi[c] : 8;
                        if (b1 <= b0 || c1 <= c0) continue;
                        boolean edgeB = !open(faces[dir(b, highB).ordinal()]), edgeC = !open(faces[dir(c, highC).ordinal()]);
                        int sprite = edgeB && edgeC ? (outer ? CAP : INNER) : edgeB || edgeC ? body : (outer ? PLAIN : INNER);
                        int alongAxis = edgeB && !edgeC ? c : edgeC && !edgeB ? b : -1;
                        rect(facing, a, plane, b, b0, b1, c, c0, c1, sprite, alongAxis, out);
                    }
                }
            } else {
                rect(facing, a, plane, b, cLo[b], cHi[b], c, cLo[c], cHi[c], openAxes == 1 ? body : (outer ? CAP : INNER), openAxes == 1 ? along : -1, out);
            }
            // Where the pipe goes on: strips to the block's edge, along it
            if (lo[b] < cLo[b]) rect(facing, a, plane, b, lo[b], cLo[b], c, cLo[c], cHi[c], body, b, out);
            if (hi[b] > cHi[b]) rect(facing, a, plane, b, cHi[b], hi[b], c, cLo[c], cHi[c], body, b, out);
            if (lo[c] < cLo[c]) rect(facing, a, plane, c, lo[c], cLo[c], b, cLo[b], cHi[b], body, c, out);
            if (hi[c] > cHi[c]) rect(facing, a, plane, c, cHi[c], hi[c], b, cLo[b], cHi[b], body, c, out);
        }
    }

    /** A flat rectangle on the plane {@code plane} of axis {@code a}: {@code [x0, x1]} on axis x, {@code [y0, y1]} on axis y. */
    private static void rect(Direction facing, int a, float plane, int x, float x0, float x1, int y, float y0, float y1, int sprite, int along, Out out) {
        float[] from = new float[3], to = new float[3];
        from[a] = to[a] = plane;
        from[x] = x0;
        to[x] = x1;
        from[y] = y0;
        to[y] = y1;
        box(facing, from, to, sprite, along, out);
    }

    /** A rim (mouth) or flange (capped end) of {@code length} pixels on {@code dir}. */
    private static void rim(Direction dir, int length, Out out) {
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
                box(dir(side, sidePos), from, to, RIM, -1, out);
                // Inside the hole
                from[side] = to[side] = sidePos ? 14 : 2;
                from[third] = 2;
                to[third] = 14;
                box(dir(side, !sidePos), from, to, RIM, -1, out);
            }
        }
        annulus(dir, pos ? 16 : 0, 2, out);
        annulus(dir.getOpposite(), pos ? 16 - length : length, 1, out);
    }

    /** A square ring 0 to 16 round a hole {@code width} pixels in from the edge, on the plane {@code plane}. */
    private static void annulus(Direction facing, float plane, float width, Out out) {
        int a = axis(facing), b = (a + 1) % 3, c = (a + 2) % 3;
        float[][] parts = {{0, 16, 0, width}, {0, 16, 16 - width, 16}, {0, width, width, 16 - width}, {16 - width, 16, width, 16 - width}};
        for (float[] part : parts) {
            float[] from = new float[3], to = new float[3];
            from[a] = to[a] = plane;
            from[b] = part[0];
            to[b] = part[1];
            from[c] = part[2];
            to[c] = part[3];
            box(facing, from, to, RIM, -1, out);
        }
    }

    /** The clamp holding the pipe to the solid block on {@code dir}. */
    private static void clamp(Direction dir, Out out) {
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
                box(dir(side, sidePos), from, to, RIM, -1, out);
            }
        }
        float[] from = new float[3], to = new float[3];
        from[a] = to[a] = hi == 16 ? 16 : 0;
        from[b] = from[c] = 5;
        to[b] = to[c] = 11;
        box(dir, from, to, RIM, -1, out);
    }

    /** Where two joined faces meet: the wall of the arm on {@code arm} on the side of the arm on {@code side}. */
    private static void armSide(Direction arm, Direction side, Out out) {
        int a = axis(arm), s = axis(side), t = 3 - a - s;
        boolean armPos = positive(arm), sidePos = positive(side);
        float[] from = new float[3], to = new float[3];
        from[a] = armPos ? 15 : 0;
        to[a] = armPos ? 16 : 1;
        from[s] = to[s] = sidePos ? 15 : 1;
        from[t] = 1;
        to[t] = 15;
        box(side, from, to, BODY, a, out);
        from[a] = armPos ? 14 : 0;
        to[a] = armPos ? 16 : 2;
        from[s] = to[s] = sidePos ? 14 : 2;
        from[t] = 2;
        to[t] = 14;
        box(side.getOpposite(), from, to, INNER, a, out);
    }

    /**
     * One quad on a flat box ({@code from} and {@code to} equal on the facing's axis), facing {@code facing}.
     * Texture coordinates follow the block grid like vanilla's; with {@code along} (an axis), turned so that the
     * texture's v runs along it.
     */
    private static void box(Direction facing, float[] from, float[] to, int sprite, int along, Out out) {
        float x0 = from[0], y0 = from[1], z0 = from[2], x1 = to[0], y1 = to[1], z1 = to[2];
        float[][] c = switch (facing) {
            case DOWN -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case UP -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case NORTH -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
            case SOUTH -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
            case WEST -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
            case EAST -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
        };
        int tAxis = facing.getAxis() == Direction.Axis.Y ? 2 : 1;
        float[][] uv = new float[4][];
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
            if (along >= 0 && along != tAxis) {
                float swap = s;
                s = t;
                t = swap;
            }
            uv[i] = new float[]{s, t};
        }
        float plane = from[axis(facing)];
        Direction cull = (positive(facing) ? plane == 16 : plane == 0) ? facing : null;
        out.quad(facing, c, uv, sprite, cull);
    }
}
