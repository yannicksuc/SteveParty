package fr.lordfinn.steveparty.utils;

import net.minecraft.util.math.MathHelper;

/** Easing curves on a progress {@code t} from 0 to 1. Pure functions, safe on any thread. */
public final class Easing {
    private Easing() {
    }

    /** {@code t} clamped to 0..1. */
    public static float clamp01(float t) {
        return MathHelper.clamp(t, 0f, 1f);
    }

    /** Slow start, slow end ({@code t} expected in 0..1, not clamped). */
    public static float smoothstep(float t) {
        return t * t * (3 - 2 * t);
    }

    /** Slow start, slow end ({@code t} expected in 0..1, not clamped). */
    public static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Fast start, slow end ({@code t} clamped to 0..1). */
    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    /** Overshoots a little before settling: the pops ({@code t} clamped to 0..1). */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }
}
