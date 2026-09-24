package fr.lordfinn.steveparty.client.model;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;

/**
 * Where a block sits inside its connected-texture plastic piece (at most 4 blocks wide on X and Z, 2 high), for the
 * plastic block and fence models: its phase on each axis, from 0 to the span - 1.
 * <p>
 * A piece starts from the edge of the build: a block counts the blocks joined to it back to the edge on each axis
 * (up to {@link #MAX_SCAN}: longer runs fall back to the world grid). That needs to see up to 13 blocks away, which
 * vanilla's chunk-building region does (it copies the 3x3 chunks around the section) and so does the world itself.
 * Sodium's region only copies the section plus 2 blocks, and reads air beyond: counted there, runs would stop at the
 * section borders, so a piece crossing one would get a wrong size and a border on one side only. In such a view, the
 * pieces follow the world grid instead: a block's phase then only depends on its own coordinates, the same whichever
 * section draws it.
 * <p>
 * One layout is used per block drawn, from one thread: it caches the phases of the 27 blocks around the drawn one
 * (all the models look at) and is reused on that thread, so drawing allocates nothing.
 */
final class PieceLayout {
    static final int SPAN_HORIZONTAL = 4;
    static final int SPAN_VERTICAL = 2;
    /** Longest run measured from the edge of the build; longer runs fall back to the world grid. */
    private static final int MAX_SCAN = 12;
    private static final Direction.Axis[] AXES = Direction.Axis.values();

    /** How a model sees the block at a position (the fence model sees a sign on a fence as that fence's post). */
    @FunctionalInterface
    interface Lookup {
        BlockState at(BlockRenderView world, BlockPos pos);
    }

    /** Whether {@code there}, next to {@code here} on side {@code dir}, belongs to the same piece. */
    @FunctionalInterface
    interface Joiner {
        boolean joined(BlockState here, BlockState there, Direction dir);
    }

    private static final ThreadLocal<PieceLayout> LAYOUTS = ThreadLocal.withInitial(PieceLayout::new);

    private final int[] phases = new int[27 * 3];
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();
    private int known;
    private BlockRenderView world;
    private BlockPos origin;
    private boolean scan;
    private Lookup lookup;
    private Joiner joiner;

    private PieceLayout() {
    }

    /** @return this thread's layout, ready for the block at {@code origin}; call {@link #end} when done. */
    static PieceLayout begin(BlockRenderView world, BlockPos origin, Lookup lookup, Joiner joiner) {
        PieceLayout layout = LAYOUTS.get();
        layout.world = world;
        layout.origin = origin;
        layout.lookup = lookup;
        layout.joiner = joiner;
        layout.scan = world instanceof ChunkRendererRegion || world instanceof World;
        layout.known = 0;
        return layout;
    }

    /** Lets go of the world (the layout outlives the block it was drawing). */
    void end() {
        world = null;
        origin = null;
        lookup = null;
        joiner = null;
    }

    static int span(int axis) {
        return axis == Direction.Axis.Y.ordinal() ? SPAN_VERTICAL : SPAN_HORIZONTAL;
    }

    /** @return the phase of the block at {@code pos} on {@code axis}. */
    int phase(BlockPos pos, int axis) {
        if (!scan) return grid(pos, axis);
        int dx = pos.getX() - origin.getX(), dy = pos.getY() - origin.getY(), dz = pos.getZ() - origin.getZ();
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1 || Math.abs(dz) > 1) return count(pos, axis);
        int slot = (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
        if ((known & (1 << slot)) == 0) {
            for (int a = 0; a < 3; a++) phases[slot * 3 + a] = count(pos, a);
            known |= 1 << slot;
        }
        return phases[slot * 3 + axis];
    }

    private static int grid(BlockPos pos, int axis) {
        return Math.floorMod(AXES[axis].choose(pos.getX(), pos.getY(), pos.getZ()), span(axis));
    }

    /** Counts the blocks joined to {@code pos} back to the edge of the build on {@code axis}. */
    private int count(BlockPos pos, int axis) {
        Direction back = Direction.from(AXES[axis], Direction.AxisDirection.NEGATIVE);
        cursor.set(pos);
        BlockState current = lookup.at(world, cursor);
        int run = 0;
        while (run < MAX_SCAN) {
            BlockState previous = lookup.at(world, cursor.move(back));
            if (!joiner.joined(current, previous, back)) break;
            current = previous;
            run++;
        }
        return run < MAX_SCAN ? run % span(axis) : grid(pos, axis);
    }

    /**
     * @return whether the blocks at {@code pos} and {@code pos + dir}, already known to be joined, are in the same
     * piece: no piece boundary between them, and aligned on the two other axes (so a piece never outgrows its box)
     */
    boolean samePiece(BlockPos pos, Direction dir) {
        int axis = dir.getAxis().ordinal();
        int phase = phase(pos, axis);
        if (phase == (dir.getDirection() == Direction.AxisDirection.POSITIVE ? span(axis) - 1 : 0)) return false;
        BlockPos other = pos.offset(dir);
        for (int a = 0; a < 3; a++) {
            if (a != axis && phase(other, a) != phase(pos, a)) return false;
        }
        return true;
    }
}
