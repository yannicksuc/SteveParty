package fr.lordfinn.steveparty.utils;

import net.minecraft.util.math.MathHelper;

/**
 * Colour arithmetic on packed {@code 0xAARRGGBB} ints, one set of semantics for the whole mod (reading channels:
 * {@link net.minecraft.util.math.ColorHelper.Argb}). Channels are truncated, not rounded, and every helper keeps the
 * alpha byte as it is unless it says otherwise: an RGB colour ({@code 0x00RRGGBB}) stays RGB, wrap the result in
 * {@link #opaque} for a fully opaque one. Pure functions, safe on any thread.
 */
public final class Argb {
    private Argb() {
    }

    /** {@code rgb} fully opaque. */
    public static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    /** An opacity (0 to 1) as an alpha byte, rounded and clamped. */
    public static int alpha(float alpha) {
        return MathHelper.clamp(Math.round(alpha * 255), 0, 255);
    }

    /** {@code argb} with its own alpha multiplied by {@code alpha} (0 to 1). */
    public static int fade(int argb, float alpha) {
        return (MathHelper.clamp(Math.round((argb >>> 24) * alpha), 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    /** {@code argb} moved toward white by {@code amount} (0 the colour, 1 white); alpha kept. */
    public static int lighten(int argb, float amount) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** {@code argb} moved toward black by {@code amount} (0 the colour, 1 black); alpha kept. */
    public static int darken(int argb, float amount) {
        return scale(argb, 1 - amount);
    }

    /** Each colour channel multiplied by {@code factor} (capped at 255); alpha kept. */
    public static int scale(int argb, float factor) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** How bright the colour looks, 0 (black) to 255 (white): Rec. 601 weights, truncated; alpha ignored. */
    public static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    /** From {@code from} (t = 0) to {@code to} (t = 1), each of the four channels on its own. */
    public static int lerp(int from, int to, float t) {
        int argb = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 0xFF, b = (to >>> shift) & 0xFF;
            argb |= ((int) (a + (b - a) * t) & 0xFF) << shift;
        }
        return argb;
    }
}
