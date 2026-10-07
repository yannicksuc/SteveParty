package fr.lordfinn.steveparty.blocks.custom;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * Where the flag of a pole is drawn. It hangs at the top of the pole (the only place a flag can be: see
 * {@link GoalPoleBlock#settleFlag}) until the goal is met, then slides down to the bottom of the pole (just above the
 * base, or the lowest segment without a base). With the "one notch per point" setting
 * ({@link GoalPoleBlockEntity#isFlagSteps()}), it goes that way step by step: the share of the way it has gone is the
 * share of the goal reached. Only the render moves: the flag stays on the top segment on the server.
 */
public final class GoalPoleFlags {
    /** Flag bottom above its segment's floor, and its height, in pixels. */
    public static final float FLAG_BOTTOM = 2.5f, FLAG_HEIGHT = 11f;
    /** Longest column looked at (a pole taller than that rests its flag from there). */
    private static final int MAX_COLUMN = 64;

    private GoalPoleFlags() {}

    /**
     * @param scratch reused position (no allocation per call)
     * @return how far down (in pixels, 0 or negative) the flag of the pole at {@code flagPos} is drawn now: 0 until its
     * goal is met (at the bottom then), or the share of the way given by its progress with the notch-per-point setting
     */
    public static float drop(BlockView world, BlockPos flagPos, BlockPos.Mutable scratch) {
        float share = share(world.getBlockEntity(flagPos));
        return share <= 0f ? 0f : restingDrop(world, flagPos, scratch) * share;
    }

    /** How far down the flag goes: 0 (at the top) to 1 (resting at the bottom). */
    private static float share(Object entity) {
        if (!(entity instanceof GoalPoleBlockEntity pole)) return 0f;
        if (!pole.isFlagSteps()) return pole.isGoalMet() ? 1f : 0f;
        return pole.progressFraction();
    }

    /**
     * @param scratch reused position (no allocation per call)
     * @return how far down (in pixels, 0 or negative) the flag of the pole at {@code flagPos} rests at the bottom: on
     * the lowest segment of its column
     */
    public static float restingDrop(BlockView world, BlockPos flagPos, BlockPos.Mutable scratch) {
        scratch.set(flagPos);
        int bottom = flagPos.getY();
        for (int i = 0; i < MAX_COLUMN; i++) {
            scratch.setY(bottom - 1);
            if (!(world.getBlockState(scratch).getBlock() instanceof GoalPoleBlock)) break;
            bottom--;
        }
        return (bottom - flagPos.getY()) * 16f;
    }
}
