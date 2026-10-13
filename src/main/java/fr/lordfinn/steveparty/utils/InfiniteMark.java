package fr.lordfinn.steveparty.utils;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * The mark of an infinite storage, the same everywhere it shows (the Party Controller in the world, its dashboard,
 * the tile panels, the shop's stock): a gold « ∞ ».
 */
public final class InfiniteMark {
    public static final String GLYPH = "∞";
    /** Its gold (ARGB), the shop's infinite stock's: on a dark ground (the world, the stall). */
    public static final int COLOR = 0xFFFFD34A;
    /** The same gold, deeper (RGB): in a line of text on a light plate (panels, dashboard). */
    public static final int INK = 0x8A5A00;

    private InfiniteMark() {
    }

    /** « ∞ » in its deep gold (bold, on a light plate), then {@code text}. */
    public static MutableText before(Text text) {
        return before(text, INK);
    }

    /** « ∞ » in its bright gold (bold, on a dark plate: the dashboard), then {@code text}. */
    public static MutableText beforeOnDark(Text text) {
        return before(text, COLOR & 0xFFFFFF);
    }

    private static MutableText before(Text text, int rgb) {
        return Text.empty().append(Text.literal(GLYPH).styled(style -> style.withColor(rgb).withBold(true)))
                .append(" ").append(text);
    }
}
