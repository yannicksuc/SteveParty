package fr.lordfinn.steveparty.items.custom.jumpshoes;

/**
 * The rhythm of the Triple Jump Shoes: a jump made on the ground less than {@link #WINDOW_TICKS} after landing from
 * the previous one, while moving, is the next of the chain (1, 2, 3, then 1 again). A late jump, a jump on the spot,
 * or a {@link #reset()} (hit, water, sneaking, a wall) starts over at 1.
 */
public final class TripleJumpChain {
    /** Ticks after landing during which the next jump keeps the chain going (holding jump re-jumps on landing). */
    public static final int WINDOW_TICKS = 6;
    /** Minimum horizontal speed (blocks per tick) for a jump to continue the chain: walking is about 0.2. */
    public static final double MIN_SPEED = 0.08;
    /** Vertical speed multiplier of each jump of the chain: 1.25, about 2.6 and about 4.3 blocks high. */
    private static final double[] BOOST = {1.0, 1.5, 2.0};

    private int last;
    private boolean grounded = true;
    private long landedAt = Long.MIN_VALUE / 2;

    /** Called every tick the player stands on the ground: the first one is the landing. */
    public void onGround(long tick) {
        if (!grounded) {
            grounded = true;
            landedAt = tick;
        }
    }

    /** A jump off the ground: returns its number in the chain (1, 2 or 3). */
    public int jump(long tick, double horizontalSpeed) {
        boolean inRhythm = last > 0 && last < 3 && tick - landedAt <= WINDOW_TICKS && horizontalSpeed >= MIN_SPEED;
        last = inRhythm ? last + 1 : 1;
        grounded = false;
        return last;
    }

    public void reset() {
        last = 0;
    }

    /** The number of the last jump of the chain, 0 after a reset. */
    public int last() {
        return last;
    }

    /** The vertical speed multiplier of jump {@code number} (1 to 3). */
    public static double boost(int number) {
        return BOOST[Math.clamp(number, 1, 3) - 1];
    }
}
