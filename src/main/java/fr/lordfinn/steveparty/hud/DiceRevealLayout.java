package fr.lordfinn.steveparty.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the pieces of the dice reveal go (client HUD {@code DiceRevealHud}), on a fixed grid, from the widths of their
 * texts (measured by the client's font, any measure in tests):
 * <pre>
 *  [⚄ LordFinn :]  [ 3 ] + [ 5 ] + [ ? ]  =  [ 11 ]  [Double !]
 * </pre>
 * <ul>
 *     <li>every die's pill has the same width in every state (« ? », a number, « −10¢ »...): {@link #dieWidth};</li>
 *     <li>{@link #GUTTER} on both sides of « + » and « = », {@link #PLATE_GAP} after the plate;</li>
 *     <li>the Double / Triple badge always {@link #BADGE_GAP} after the last piece before it (the last die, or the
 *     total once shown);</li>
 *     <li>nothing before the badge moves from one state to the next: the row starts where the whole final row
 *     ({@link #reserved}) is centred, and only grows to the right.</li>
 * </ul>
 * Widths are the shapes' own (outline included; their padding for the shadow is the drawer's business).
 */
public final class DiceRevealLayout {
    /** The height of the plates and pills. */
    public static final int H = 15;
    /** Plate: the icon's left margin, the text's start, and the room after the text. */
    public static final int PLATE_ICON_X = 4, PLATE_TEXT_X = 17, PLATE_RIGHT = 7;
    /** Room on each side of the text in a pill: a die, the total, the badge. */
    public static final int DIE_PAD = 5, PILL_PAD = 6;
    /** Between the plate and the first pill; on each side of an operator; between the result and the badge. */
    public static final int PLATE_GAP = 6, GUTTER = 3, BADGE_GAP = 6;
    /** The texts a die may show, the widest of them sets every die's pill (with the faces actually shown). */
    public static final List<String> WIDEST_FACES = List.of("?", "10", "12", "10★", "12★", "10☠", "+10¢", "−10¢", "⇄", "–");
    /** The total reserved from the start (wider totals push only the badge). */
    public static final String RESERVED_TOTAL = "00";

    public enum Kind { PLATE, DIE, PLUS, EQUALS, TOTAL, BADGE }

    /** A piece: its kind, its left edge (from the row's start), its width, and for a die its index. */
    public record Box(Kind kind, int x, int width, int index) {
        public int right() {
            return x + width;
        }
    }

    /** Measures a text, in pixels (the font's advance: a trailing pixel of spacing included). */
    @FunctionalInterface
    public interface Measure {
        int width(String text);
    }

    private DiceRevealLayout() {
    }

    /** The width of a text as drawn (without the font's trailing pixel). */
    private static int ink(Measure measure, String text) {
        return Math.max(0, measure.width(text) - 1);
    }

    /** An operator as drawn: its ink and the font's shadow, a pixel right of it. */
    private static int sign(Measure measure, String sign) {
        return ink(measure, sign) + 1;
    }

    public static int plateWidth(int textInk) {
        return PLATE_TEXT_X + textInk + PLATE_RIGHT;
    }

    public static int pillWidth(int textInk) {
        return Math.max(H, textInk + 2 * PILL_PAD);
    }

    /** Every die's pill: the widest face it may show ({@link #WIDEST_FACES} and {@code shown}), padded. */
    public static int dieWidth(Measure measure, List<String> shown) {
        int widest = 0;
        for (String face : WIDEST_FACES) widest = Math.max(widest, ink(measure, face));
        for (String face : shown) widest = Math.max(widest, ink(measure, face));
        return Math.max(H, widest + 2 * DIE_PAD);
    }

    /**
     * The row as it is now.
     *
     * @param plate the roller's text (already cut to fit)
     * @param dice  the dice of the throw (one: no die pills, the result only)
     * @param faces the faces' texts revealed so far
     * @param total the total's text, null while it is to come
     * @param badge « Double ! » / « Triple ! », null if none
     */
    public static List<Box> layout(Measure measure, String plate, int dice, List<String> faces, String total, String badge) {
        List<Box> boxes = new ArrayList<>();
        int x = 0;
        boxes.add(new Box(Kind.PLATE, x, plateWidth(ink(measure, plate)), -1));
        x += boxes.getLast().width() + PLATE_GAP;
        boolean multi = dice > 1;
        if (multi) {
            int die = dieWidth(measure, faces);
            int plus = sign(measure, "+");
            for (int i = 0; i < dice; i++) {
                if (i > 0) {
                    boxes.add(new Box(Kind.PLUS, x, plus, -1));
                    x += plus + GUTTER;
                }
                boxes.add(new Box(Kind.DIE, x, die, i));
                x += die + GUTTER;
            }
        }
        if (total != null) {
            if (multi) {
                int equals = sign(measure, "=");
                boxes.add(new Box(Kind.EQUALS, x, equals, -1));
                x += equals + GUTTER;
            }
            boxes.add(new Box(Kind.TOTAL, x, pillWidth(ink(measure, total)), -1));
            x += boxes.getLast().width() + GUTTER;
        }
        if (badge != null) {
            // Always the same gap after the last piece (a die or the total), whatever the state
            x += BADGE_GAP - GUTTER;
            boxes.add(new Box(Kind.BADGE, x, pillWidth(ink(measure, badge)), -1));
        }
        return boxes;
    }

    /** The width of a row. */
    public static int width(List<Box> boxes) {
        return boxes.isEmpty() ? 0 : boxes.getLast().right() - boxes.getFirst().x();
    }

    /**
     * The width of the throw's final row, reserved from its start (the row is centred on it): every die, the total
     * ({@link #RESERVED_TOTAL} at least) and, for several dice, the widest badge.
     */
    public static int reserved(Measure measure, String plate, int dice, List<String> faces, String total, List<String> badges) {
        String widestTotal = total != null && ink(measure, total) > ink(measure, RESERVED_TOTAL) ? total : RESERVED_TOTAL;
        String widestBadge = null;
        if (dice > 1) {
            for (String badge : badges) {
                if (widestBadge == null || ink(measure, badge) > ink(measure, widestBadge)) widestBadge = badge;
            }
        }
        return width(layout(measure, plate, dice, faces, widestTotal, widestBadge));
    }
}
