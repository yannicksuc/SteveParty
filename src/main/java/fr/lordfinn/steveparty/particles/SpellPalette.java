package fr.lordfinn.steveparty.particles;

import fr.lordfinn.steveparty.utils.Argb;
import fr.lordfinn.steveparty.utils.Easing;
import net.minecraft.util.math.random.Random;

/**
 * The Tokenizer Wand's colours, shared by every visual of the token spell (world particles, the spell screen, the
 * shapes orbiting the wand): a fuchsia to violet to blue gradient that settles on lapis blue, the colour of the
 * wand's jewel, with pale lilac for highlights (sparkles, flashes, shimmers) and a deep indigo for shadows.
 */
public final class SpellPalette {
    public static final int FUCHSIA = 0xF03CC8, PURPLE = 0xB848F0, VIOLET = 0x7B55F5, BLUE = 0x3D7BE8;
    /** The anchor colour: the lapis of the wand's jewel and of the crystal ball. */
    public static final int LAPIS = 0x2852D6;
    /** Highlights: sparkle heads, flashes and shimmers (never a plain white). */
    public static final int LILAC = 0xEEE4FF;
    /** Outlines and plates behind spell texts. */
    public static final int SHADOW = 0x1E1530;

    /** The gradient, from its warm end to the lapis anchor. */
    public static final int[] GRADIENT = {FUCHSIA, PURPLE, VIOLET, BLUE, LAPIS};
    /** Random picks lean on the cool end: lapis and blue first, violet, then fuchsia and purple as accents. */
    private static final int[] WEIGHTED = {LAPIS, LAPIS, LAPIS, BLUE, BLUE, VIOLET, VIOLET, PURPLE, FUCHSIA};

    private SpellPalette() {
    }

    /** A random spell colour, weighted towards lapis. */
    public static int random(Random random) {
        return WEIGHTED[random.nextInt(WEIGHTED.length)];
    }

    /**
     * The gradient flowing along a line: {@code t} runs freely (one unit per gradient step); the colours go from
     * fuchsia to lapis and back, so a loop has no hard seam.
     */
    public static int flow(float t) {
        int steps = GRADIENT.length - 1;
        float p = t % (2 * steps);
        if (p < 0) p += 2 * steps;
        if (p > steps) p = 2 * steps - p;
        int index = Math.min(steps - 1, (int) p);
        float blend = p - index;
        return Argb.lerp(GRADIENT[index], GRADIENT[index + 1], Easing.smoothstep(blend));
    }
}
