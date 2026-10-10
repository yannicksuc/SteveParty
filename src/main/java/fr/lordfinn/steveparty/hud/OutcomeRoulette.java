package fr.lordfinn.steveparty.hud;

/**
 * The timing of an outcome roulette (client HUD {@code OutcomeRouletteHud}, server service
 * {@code OutcomeRoulettes}): the same numbers on both sides, so every client lights the same line at the same moment
 * and the server knows when the roulette is over.
 * <ol>
 *     <li><b>Reveal</b> ({@link #REVEAL_TICKS}): every possible outcome is listed, nothing lit: time to read them.</li>
 *     <li><b>Spin</b> ({@link #SPIN_TICKS}): a light runs down the list, top to bottom and round again, one line per
 *     step, fast at first then slower and slower (ease-out, the last steps about {@link #SLOW} ticks apart), and
 *     stops on the result: the light starts on the first line and moves {@code steps} times
 *     ({@link #steps}: {@code steps} modulo the line count is the result).</li>
 *     <li><b>Hold</b> ({@link #HOLD_TICKS}): the result flashes and stays lit; then what it says happens.</li>
 * </ol>
 * The result is drawn by the server before the roulette starts (its odds are the server's): the roulette only shows
 * it. Pure arithmetic, no Minecraft classes.
 */
public final class OutcomeRoulette {
    /** Reading time, spinning time, the result held (ticks). */
    public static final int REVEAL_TICKS = 60, SPIN_TICKS = 120, HOLD_TICKS = 30;
    /** The whole roulette: the effect runs once it is over. */
    public static final int TOTAL_TICKS = REVEAL_TICKS + SPIN_TICKS + HOLD_TICKS;
    /** At least this many steps (it goes round the list a few times), before the scaling to {@link #SPIN_TICKS}. */
    public static final int MIN_STEPS = 20;
    /** The first and the last gaps between two steps before the scaling (ticks); the curve between them. */
    private static final double FAST = 1.6, SLOW = 12, CURVE = 2.2;
    /** The most lines a roulette may list. */
    public static final int MAX_LINES = 12;

    private OutcomeRoulette() {
    }

    /**
     * How many times the light moves to stop on line {@code result} of {@code lines}: the fewest from
     * {@link #MIN_STEPS} up that land on it.
     */
    public static int steps(int lines, int result) {
        if (lines <= 0) return 0;
        int steps = MIN_STEPS;
        while (Math.floorMod(steps, lines) != Math.floorMod(result, lines)) steps++;
        return steps;
    }

    /**
     * When each step happens, from the start of the spin (ticks): {@code at[k - 1]} is step {@code k}, the last one
     * exactly at {@link #SPIN_TICKS}. The gaps grow from fast to slow whatever the number of steps.
     */
    public static double[] stepTimes(int steps) {
        double[] at = new double[Math.max(0, steps)];
        if (steps <= 0) return at;
        double sum = 0;
        for (int k = 1; k <= steps; k++) {
            sum += gap(k, steps);
            at[k - 1] = sum;
        }
        double scale = SPIN_TICKS / sum;
        for (int k = 0; k < steps; k++) at[k] *= scale;
        at[steps - 1] = SPIN_TICKS;
        return at;
    }

    private static double gap(int k, int steps) {
        double progress = steps == 1 ? 1 : (k - 1) / (double) (steps - 1);
        return FAST + (SLOW - FAST) * Math.pow(progress, CURVE);
    }

    /** How many steps were taken {@code spinTicks} into the spin ({@code times}: {@link #stepTimes}). */
    public static int stepsTaken(double[] times, double spinTicks) {
        int taken = 0;
        while (taken < times.length && times[taken] <= spinTicks) taken++;
        return taken;
    }

    /**
     * The lit line {@code ticks} after the roulette started (-1 while the list is read), on {@code lines} lines,
     * the light moving {@code steps} times ({@code times}: {@link #stepTimes} of them).
     */
    public static int litLine(int lines, double[] times, double ticks) {
        if (lines <= 0 || ticks < REVEAL_TICKS) return -1;
        return Math.floorMod(stepsTaken(times, ticks - REVEAL_TICKS), lines);
    }

    /** The phase {@code ticks} after the start. */
    public static Phase phase(double ticks) {
        if (ticks < REVEAL_TICKS) return Phase.REVEAL;
        if (ticks < REVEAL_TICKS + SPIN_TICKS) return Phase.SPIN;
        if (ticks < TOTAL_TICKS) return Phase.HOLD;
        return Phase.OVER;
    }

    public enum Phase { REVEAL, SPIN, HOLD, OVER }
}
