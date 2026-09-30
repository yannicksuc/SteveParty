package fr.lordfinn.steveparty.client.utils;

/**
 * Colour ramps for the tiles: shades of a colour that keep it vivid, like hand-made pixel-art palettes. Darker shades
 * don't just darken (tinting a grey texture makes everything greyish): they gain saturation and turn their hue
 * (yellow shades toward orange, red toward crimson, green and blue toward deep blue); lighter shades go toward white.
 * Pure functions: safe on the chunk builder threads (block colour providers).
 */
public final class TileColors {
    /** Default tile colour (no cartridge, or one without colour). */
    public static final int WHITE = 0xFFFFFF;

    private TileColors() {
    }

    /**
     * A shade of {@code rgb}: {@code darkness} 0 is the colour itself, up to 1 its darkest shade; negative values
     * are highlights (down to -1, almost white).
     */
    public static int shade(int rgb, float darkness) {
        float[] hsv = toHsv(rgb);
        float h = hsv[0], s = hsv[1], v = hsv[2];
        if (darkness > 0) {
            if (s < 0.08f) {
                // Whites and greys shade toward a cool blue-grey
                h = 225;
                s = s + 0.14f * darkness;
            } else {
                h = shiftHue(h, 30 * darkness);
                s = Math.min(1, s + (1 - s) * 0.35f * darkness);
            }
            v = v * (1 - 0.5f * darkness);
        } else if (darkness < 0) {
            float light = -darkness;
            if (s >= 0.08f) h = shiftHue(h, -12 * light);
            v = v + (1 - v) * 0.6f * light;
            s = s * (1 - 0.4f * light);
        }
        return fromHsv(h, s, v);
    }

    /** Warm hues shade toward red, the others toward blue; a negative amount goes the other way (highlights). */
    private static float shiftHue(float h, float amount) {
        if (h < 70 || h >= 300) {
            // Toward red: yellow -> orange -> red, magenta -> crimson
            h = h < 70 ? h - amount : h + amount;
        } else {
            float target = 245;
            float distance = target - h;
            float step = Math.min(Math.abs(amount), Math.abs(distance)) * Math.signum(amount);
            h = h + Math.signum(distance) * step;
        }
        return ((h % 360) + 360) % 360;
    }

    private static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        float h;
        if (delta == 0) h = 0;
        else if (max == r) h = 60 * (((g - b) / delta) % 6);
        else if (max == g) h = 60 * (((b - r) / delta) + 2);
        else h = 60 * (((r - g) / delta) + 4);
        if (h < 0) h += 360;
        return new float[]{h, max == 0 ? 0 : delta / max, max};
    }

    private static int fromHsv(float h, float s, float v) {
        s = Math.max(0, Math.min(1, s));
        v = Math.max(0, Math.min(1, v));
        float c = v * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /** Whether {@code rgb} is a dark colour, whose pictograms are drawn light rather than in its darkest shade. */
    public static boolean isDark(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return 0.299f * r + 0.587f * g + 0.114f * b < 100;
    }

    // ---------------------------------------------------------------- tint indexes of the tile models

    /**
     * Darkness of each tint index of the tile models: 0 the sides of the picture; 1..6 the value layers of the start
     * tile's top (tile_start_layer_0..5, from its lightest grey to its symbol).
     */
    private static final float[] TINT_DARKNESS = {0.25f, 0.0f, 0.1f, 0.2f, 0.3f, 0.42f, 0.72f};

    /** The colour of the tint index {@code tintIndex} of a tile model coloured {@code rgb}. */
    public static int tint(int rgb, int tintIndex) {
        if (tintIndex < 0 || tintIndex >= TINT_DARKNESS.length) return rgb;
        return shade(rgb, TINT_DARKNESS[tintIndex]);
    }
}
