package fr.lordfinn.steveparty.blocks.custom;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * Where the flags of a pole column are drawn. When their goal is met they slide down to the bottom of the pole (just
 * above the base, or the lowest segment without a base) and stack there, the lowest flag first, 1 pixel apart. With
 * the "one notch per point" setting ({@link GoalPoleBlockEntity#isFlagSteps()}), a flag goes that way step by step:
 * the share of the way it has gone is the share of its goal reached.
 * <p>
 * Worked out bottom-up along the column: a flag rests on whatever is below it (the bottom, or the flag below,
 * wherever that one is); a flag that has not started down stays at its place. A flag never rests higher than its own
 * place, and flags never overlap nor slide through one another. Only the render moves: the flag stays on its segment
 * on the server (shears, dye and drops work where it is placed).
 */
public final class GoalPoleFlags {
    /** Flag bottom above its segment's floor, height, and the gap between stacked flags, in pixels. */
    public static final float FLAG_BOTTOM = 2.5f, FLAG_HEIGHT = 11f, STACK_GAP = 1f;
    /** Longest column looked at (a pole taller than that stacks its flags from there). */
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

    /** How far down a flag goes: 0 (at its place) to 1 (resting at the bottom). */
    private static float share(Object entity) {
        if (!(entity instanceof GoalPoleBlockEntity pole)) return 0f;
        if (!pole.isFlagSteps()) return pole.isGoalMet() ? 1f : 0f;
        return pole.progressFraction();
    }

    /**
     * @param scratch reused position (no allocation per call)
     * @return how far down (in pixels, 0 or negative) the flag of the pole at {@code flagPos} rests at the bottom (on
     * the flags below it, where they are drawn)
     */
    public static float restingDrop(BlockView world, BlockPos flagPos, BlockPos.Mutable scratch) {
        // Lowest segment of the column
        scratch.set(flagPos);
        int bottom = flagPos.getY();
        for (int i = 0; i < MAX_COLUMN; i++) {
            scratch.setY(bottom - 1);
            if (!(world.getBlockState(scratch).getBlock() instanceof GoalPoleBlock)) break;
            bottom--;
        }
        // Up the column: each flag rests on the one below it
        float next = bottom * 16f + FLAG_BOTTOM;
        for (int y = bottom; y < flagPos.getY(); y++) {
            scratch.setY(y);
            BlockState state = world.getBlockState(scratch);
            if (!state.contains(GoalPoleBlock.FLAG) || !state.get(GoalPoleBlock.FLAG)) continue;
            float own = y * 16f + FLAG_BOTTOM;
            // Where the flag below is drawn: its share of the way from its place down to where it would rest
            float share = share(world.getBlockEntity(scratch));
            next = own + (Math.min(next, own) - own) * share + FLAG_HEIGHT + STACK_GAP;
        }
        float own = flagPos.getY() * 16f + FLAG_BOTTOM;
        return Math.min(next, own) - own;
    }
}
