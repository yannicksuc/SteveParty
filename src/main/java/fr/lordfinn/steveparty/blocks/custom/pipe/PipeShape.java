package fr.lordfinn.steveparty.blocks.custom.pipe;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ConnectingBlock;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a pipe block, from its connections to other pipes (a bit per face) and the solid block it is fixed to.
 * <ul>
 *     <li>a pipe joined to 2 pipes or more is a length of pipe, an elbow or a junction: no end;</li>
 *     <li>a pipe joined to 1 pipe ends a run on the other side: a <b>mouth</b> (wide rim), or a <b>capped end</b> when
 *     that side is its solid block (the pipe goes into the wall or the ground: a warp);</li>
 *     <li>a pipe alone is a length along its solid block: a mouth facing away from it and a capped end into it; alone
 *     and fixed to nothing, a vertical length open at both ends.</li>
 * </ul>
 * A solid block beside the run (not at an end) holds the pipe with a clamp. Shared by the server (shapes, ends) and
 * the client model.
 */
public final class PipeShape {
    /** Faces, as seen by the model. */
    public enum Face { CONNECTED, MOUTH, CAPPED, CLAMP, CLOSED }

    /** An end of a pipe: the side it opens on (or goes into the solid block, capped). */
    public record End(Direction dir, boolean capped) {}

    /** Number of shape keys: 64 connection masks x 7 solid values. */
    public static final int KEYS = 64 * 7;
    /** Length of a mouth's rim and of a capped end's flange, and depth of the hollow in a mouth (pixels). */
    public static final int RIM = 5, FLANGE = 2, HOLLOW = 4;

    private static final VoxelShape[] SHAPES = new VoxelShape[KEYS];

    private PipeShape() {}

    public static BooleanProperty connection(Direction dir) {
        return ConnectingBlock.FACING_PROPERTIES.get(dir);
    }

    public static int mask(BlockState state) {
        int mask = 0;
        for (Direction dir : Direction.values()) if (state.get(connection(dir))) mask |= 1 << dir.ordinal();
        return mask;
    }

    public static @Nullable Direction solid(BlockState state) {
        return state.get(PipeBlock.SOLID).direction();
    }

    public static int key(int mask, @Nullable Direction solid) {
        return mask * 7 + (solid == null ? 0 : solid.ordinal() + 1);
    }

    public static int key(BlockState state) {
        return key(mask(state), solid(state));
    }

    public static int maskOf(int key) {
        return key / 7;
    }

    public static @Nullable Direction solidOf(int key) {
        int s = key % 7;
        return s == 0 ? null : Direction.values()[s - 1];
    }

    /** The ends of a pipe block (0, 1 or 2). */
    public static List<End> ends(int mask, @Nullable Direction solid) {
        List<End> ends = new ArrayList<>(2);
        int count = Integer.bitCount(mask);
        if (count == 1) {
            Direction open = Direction.values()[Integer.numberOfTrailingZeros(mask)].getOpposite();
            ends.add(new End(open, open == solid));
        } else if (count == 0) {
            if (solid == null) {
                ends.add(new End(Direction.UP, false));
                ends.add(new End(Direction.DOWN, false));
            } else {
                ends.add(new End(solid.getOpposite(), false));
                ends.add(new End(solid, true));
            }
        }
        return ends;
    }

    /** The ends of a pipe block (none for anything else). */
    public static List<End> ends(BlockState state) {
        if (!(state.getBlock() instanceof PipeBlock)) return List.of();
        return ends(mask(state), solid(state));
    }

    /** @return the open end (mouth) of this pipe on {@code dir}, or null */
    public static @Nullable End mouth(BlockState state, Direction dir) {
        for (End end : ends(state)) if (end.dir() == dir && !end.capped()) return end;
        return null;
    }

    public static boolean hasMouth(BlockState state) {
        for (End end : ends(state)) if (!end.capped()) return true;
        return false;
    }

    /** What each face (by {@link Direction#ordinal()}) shows. */
    public static Face[] faces(int mask, @Nullable Direction solid) {
        Face[] faces = new Face[6];
        for (Direction dir : Direction.values()) {
            faces[dir.ordinal()] = (mask & 1 << dir.ordinal()) != 0 ? Face.CONNECTED : dir == solid ? Face.CLAMP : Face.CLOSED;
        }
        for (End end : ends(mask, solid)) faces[end.dir().ordinal()] = end.capped() ? Face.CAPPED : Face.MOUTH;
        return faces;
    }

    // ---------------------------------------------------------------- collision and outline

    public static VoxelShape shape(BlockState state) {
        int key = key(state);
        VoxelShape shape = SHAPES[key];
        if (shape == null) SHAPES[key] = shape = buildShape(maskOf(key), solidOf(key));
        return shape;
    }

    /**
     * The body (1 to 15), up to each face it is joined on; a mouth is a rim round a hollow {@link #HOLLOW} pixels
     * deep, where what goes in stands (and is taken in); a capped end a flat flange; a clamp a small block.
     */
    private static VoxelShape buildShape(int mask, @Nullable Direction solid) {
        Face[] faces = faces(mask, solid);
        double[] min = {1, 1, 1}, max = {15, 15, 15};
        VoxelShape shape = VoxelShapes.empty();
        for (Direction dir : Direction.values()) {
            Face face = faces[dir.ordinal()];
            int axis = dir.getAxis().ordinal();
            boolean positive = dir.getDirection() == Direction.AxisDirection.POSITIVE;
            if (face == Face.MOUTH) {
                if (positive) max[axis] = 16 - HOLLOW; else min[axis] = HOLLOW;
                shape = VoxelShapes.union(shape, ring(dir, RIM));
            } else if (face == Face.CAPPED) {
                shape = VoxelShapes.union(shape, slab(dir, 0, 16, FLANGE));
            } else if (face == Face.CONNECTED) {
                shape = VoxelShapes.union(shape, slab(dir, 1, 15, 1));
            } else if (face == Face.CLAMP) {
                shape = VoxelShapes.union(shape, slab(dir, 5, 11, 1));
            }
        }
        return VoxelShapes.union(shape, Block.createCuboidShape(min[0], min[1], min[2], max[0], max[1], max[2]));
    }

    /** A square [from, to] across {@code dir}, {@code length} pixels long from its face inwards. */
    private static VoxelShape slab(Direction dir, double from, double to, double length) {
        double[] a = {from, from, from}, b = {to, to, to};
        int axis = dir.getAxis().ordinal();
        boolean positive = dir.getDirection() == Direction.AxisDirection.POSITIVE;
        a[axis] = positive ? 16 - length : 0;
        b[axis] = positive ? 16 : length;
        return Block.createCuboidShape(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    /** The rim of a mouth: 0 to 16 across round a 2 to 14 hole, {@code length} pixels long. */
    private static VoxelShape ring(Direction dir, double length) {
        int axis = dir.getAxis().ordinal();
        boolean positive = dir.getDirection() == Direction.AxisDirection.POSITIVE;
        double lo = positive ? 16 - length : 0, hi = positive ? 16 : length;
        int b = (axis + 1) % 3, c = (axis + 2) % 3;
        VoxelShape shape = VoxelShapes.empty();
        double[][] parts = {{0, 16, 0, 2}, {0, 16, 14, 16}, {0, 2, 2, 14}, {14, 16, 2, 14}};
        for (double[] part : parts) {
            double[] from = new double[3], to = new double[3];
            from[axis] = lo;
            to[axis] = hi;
            from[b] = part[0];
            to[b] = part[1];
            from[c] = part[2];
            to[c] = part[3];
            shape = VoxelShapes.union(shape, Block.createCuboidShape(from[0], from[1], from[2], to[0], to[1], to[2]));
        }
        return shape;
    }
}
