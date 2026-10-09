package fr.lordfinn.steveparty.client.flag;

import fr.lordfinn.steveparty.utils.Easing;
import net.minecraft.util.math.MathHelper;

/**
 * Wind for the small cloth flags: a gentle idle flutter all the time, and from time to time a gust (a stronger,
 * faster flapping for a second or two).
 * <p>
 * Stateless: everything is derived from the world time and the flag's position, so there is nothing to tick, save or
 * sync, and every player sees the same wind. Flags are not in sync: each one has its own idle phase and speed. Gusts
 * are drawn per 16x16 column of blocks (a chunk column): the flags of one column share a gust, which reaches them one
 * after the other (a front sweeping across the column), while the next column has its own gusts.
 */
public final class FlagWind {
    public static final float TAU = (float) (Math.PI * 2);
    /** Gust windows: at most one gust per window and per column of blocks. */
    private static final double GUST_WINDOW_SECONDS = 6.0;
    /** Chance that a window has a gust (on average one gust every ~11 s). */
    private static final float GUST_CHANCE = 0.55f;
    private static final double GUST_MIN_SECONDS = 1.3, GUST_EXTRA_SECONDS = 1.1;
    /** Seconds for a gust front to cross one block. */
    private static final double SWEEP_SECONDS_PER_BLOCK = 0.035;

    private FlagWind() {}

    /** Per-flag constants, derived from its position. */
    public static long seed(int x, int y, int z) {
        return MathHelper.hashCode(x, y, z);
    }

    /** Idle phase of a flag, in radians. */
    public static float phase(long seed) {
        return ((seed >>> 8) & 0xFFFF) / 65536f * TAU;
    }

    /** Idle speed factor of a flag, in [0.88, 1.12]. */
    public static float speed(long seed) {
        return 0.88f + 0.24f * ((seed >>> 24) & 0xFF) / 255f;
    }

    /**
     * Gust strength at {@code seconds} for the flag at (x, z), in [0, 1]: 0 most of the time, rising quickly to the
     * gust's strength then settling back.
     */
    public static float gust(int x, int z, double seconds) {
        // The front sweeps the column from its north-west corner
        double local = seconds - ((x & 15) + (z & 15)) * SWEEP_SECONDS_PER_BLOCK;
        long window = (long) Math.floor(local / GUST_WINDOW_SECONDS);
        long h = mix(MathHelper.hashCode(x >> 4, 0x5EED, z >> 4) ^ (window * 0x9E3779B97F4A7C15L));
        if ((h & 0xFF) >= GUST_CHANCE * 256) return 0f;
        double duration = GUST_MIN_SECONDS + GUST_EXTRA_SECONDS * ((h >>> 8) & 0xFF) / 255.0;
        double start = window * GUST_WINDOW_SECONDS + ((h >>> 16) & 0xFF) / 255.0 * (GUST_WINDOW_SECONDS - duration);
        double u = (local - start) / duration;
        if (u <= 0 || u >= 1) return 0f;
        float strength = 0.65f + 0.35f * ((h >>> 24) & 0xFF) / 255f;
        // Quick rise (first 20 %), slower fall, with a little unsteadiness while it blows
        float envelope = u < 0.2 ? smooth((float) (u / 0.2)) : 1f - smooth((float) ((u - 0.2) / 0.8));
        float unsteady = 0.85f + 0.15f * (float) Math.sin(seconds * 9.0 + (h >>> 32 & 0xFF));
        return strength * envelope * unsteady;
    }

    /**
     * Sideways offset of the cloth, in pixels, at {@code s} along the flag (0 at the pole, 1 at the tip). The pole
     * edge never moves; the amplitude grows towards the tip. Waves travel from the pole to the tip.
     */
    public static float offset(float s, double seconds, float phase, float speed, float gust) {
        float reach = (float) Math.pow(s, 1.3);
        double idle = 0.9 * Math.sin(TAU * 0.55 * speed * seconds - TAU * 0.85 * s + phase)
                + 0.35 * Math.sin(TAU * 1.4 * speed * seconds - TAU * 1.9 * s + 2 * phase);
        double gusty = 2.7 * Math.sin(TAU * 2.4 * seconds - TAU * 1.3 * s + phase)
                + 0.5 * Math.sin(TAU * 4.1 * seconds - TAU * 2.6 * s);
        return reach * (float) (idle * (1 - 0.6 * gust) + gusty * gust);
    }

    /** Swing of the whole flag around the pole, in degrees (only during gusts). */
    public static float swing(double seconds, float phase, float gust) {
        return gust * 13f * (float) Math.sin(TAU * 0.9 * seconds + phase);
    }

    /** How much the tip hangs down, in pixels: some at rest, none when a gust stretches the flag. */
    public static float droop(float s, float gust) {
        return s * s * 0.9f * (1f - gust);
    }

    private static float smooth(float t) {
        return Easing.smoothstep(Easing.clamp01(t));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }
}
