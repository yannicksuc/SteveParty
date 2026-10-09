package fr.lordfinn.steveparty.entities.custom.trichaudron;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Arrays;

/**
 * The Trichaudron's pumping pace. It drinks from lava sources without taking them (the lava stays: no hole in a lake, no
 * river cut off): a bucket a gulp, at most one every {@link #MIN_GAP} ticks and {@link #MAX_PER_MINUTE} a minute.
 */
public final class TrichaudronPumping {
    public static final int MAX_PER_MINUTE = 6;
    public static final int MIN_GAP = 60;
    private static final int MINUTE = 1200;

    /** When it pumped its last gulps (world time), oldest first; empty slots are {@link Long#MIN_VALUE}. */
    private final long[] gulps = new long[MAX_PER_MINUTE];

    public TrichaudronPumping() {
        Arrays.fill(gulps, Long.MIN_VALUE);
    }

    /** A still lava source block. */
    public static boolean isSource(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.LAVA) && state.getFluidState().isOf(Fluids.LAVA) && state.getFluidState().isStill();
    }

    /** Whether the pace allows a gulp now. */
    public boolean rateAllows(long now) {
        long last = gulps[gulps.length - 1];
        if (last != Long.MIN_VALUE && now - last < MIN_GAP) return false;
        return gulps[0] == Long.MIN_VALUE || now - gulps[0] >= MINUTE;
    }

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
