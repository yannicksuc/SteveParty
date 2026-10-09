package fr.lordfinn.steveparty.entities.custom.fumarole;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Arrays;

/**
 * The Fumarole's pumping rules: it drinks real lava sources (the block disappears, its tank gains a bucket), but it
 * can never drain a lake. A source can be pumped only when:
 * <ul>
 *     <li>it is a still lava source block;</li>
 *     <li>at least {@link #MIN_NEIGHBOURS} of its 4 horizontal neighbours are sources too (never the lone source at
 *     the edge, never a puddle);</li>
 *     <li>at least {@link #MIN_REMAINING} other sources stay within {@link #AREA_RADIUS} blocks around it
 *     (horizontally, {@link #AREA_HEIGHT} up and down): a pool of fewer than {@code MIN_REMAINING + 1} sources is
 *     never touched, and a bigger one is never pumped below that;</li>
 *     <li>it has pumped fewer than {@link #MAX_PER_MINUTE} sources in the last minute, and none in the last
 *     {@link #MIN_GAP} ticks (one gulp every 3 s at best).</li>
 * </ul>
 * Since the tank holds 27 buckets and only empties as it shoots, one Fumarole drinks at most a few dozen sources of a
 * Nether lava sea in its life.
 */
public final class FumarolePumping {
    public static final int MIN_NEIGHBOURS = 2;
    public static final int MIN_REMAINING = 12;
    public static final int AREA_RADIUS = 5, AREA_HEIGHT = 2;
    public static final int MAX_PER_MINUTE = 6;
    public static final int MIN_GAP = 60;
    private static final int MINUTE = 1200;

    /** When it pumped its last sources (world time), oldest first; empty slots are {@link Long#MIN_VALUE}. */
    private final long[] gulps = new long[MAX_PER_MINUTE];

    public FumarolePumping() {
        Arrays.fill(gulps, Long.MIN_VALUE);
    }

    /** A still lava source block. */
    public static boolean isSource(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.LAVA) && state.getFluidState().isOf(Fluids.LAVA) && state.getFluidState().isStill();
    }

    /** Whether this source may be taken without draining its pool (the area rules above; not the rate). */
    public static boolean leavesEnough(World world, BlockPos pos) {
        if (!isSource(world, pos)) return false;
        int neighbours = 0;
        for (Direction direction : Direction.Type.HORIZONTAL) if (isSource(world, pos.offset(direction))) neighbours++;
        if (neighbours < MIN_NEIGHBOURS) return false;
        int remaining = 0;
        BlockPos.Mutable at = new BlockPos.Mutable();
        for (int dx = -AREA_RADIUS; dx <= AREA_RADIUS; dx++) {
            for (int dz = -AREA_RADIUS; dz <= AREA_RADIUS; dz++) {
                for (int dy = -AREA_HEIGHT; dy <= AREA_HEIGHT; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (isSource(world, at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz))
                            && ++remaining >= MIN_REMAINING) return true;
                }
            }
        }
        return false;
    }

    /** Whether the rate allows a gulp now. */
    public boolean rateAllows(long now) {
        long last = gulps[gulps.length - 1];
        if (last != Long.MIN_VALUE && now - last < MIN_GAP) return false;
        return gulps[0] == Long.MIN_VALUE || now - gulps[0] >= MINUTE;
    }

    /** Records a gulp at {@code now}. */
    public void record(long now) {
        System.arraycopy(gulps, 1, gulps, 0, gulps.length - 1);
        gulps[gulps.length - 1] = now;
    }

    public void write(NbtCompound nbt) {
        nbt.putLongArray("PumpGulps", gulps);
    }

    public void read(NbtCompound nbt) {
        long[] saved = nbt.getLongArray("PumpGulps");
        Arrays.fill(gulps, Long.MIN_VALUE);
        int n = Math.min(saved.length, gulps.length);
        System.arraycopy(saved, saved.length - n, gulps, gulps.length - n, n);
    }
}
