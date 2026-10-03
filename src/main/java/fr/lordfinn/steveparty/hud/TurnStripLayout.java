package fr.lordfinn.steveparty.hud;

import fr.lordfinn.steveparty.hud.HudShapes.Form;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.hud.HudShapes.GAP;
import static fr.lordfinn.steveparty.hud.HudShapes.PAD;

/**
 * The turn bar's strip, laid out (the approved mock-up « 7E v4 »: the art sources, with the
 * bubbles under the strip): the step being played big, the next one medium, the following ones small, on one axis.
 * <ul>
 * <li>Players' turns are candy chevrons with the head (the current one: its name in a fixed width, the party's longest
 * name, {@value #NAME_MAX} px at most; the next one: its name, {@value #NEXT_NAME_MAX} px at most, or its initial when
 * the room is narrow); mini-games and events are round medallions; the start (turn order rolls) and the other steps
 * are neutral; a small numbered medallion starts each new round; while the turn order is not known, a round is a
 * neutral chevron with its number and blank pawns.</li>
 * <li>The next mini-game always shows: in its place, or pinned before « +N » (« dans 3 »); « +N » counts the rounds
 * left hidden.</li>
 * <li>Under the strip, the yellow bubbles of the player of this client: « toi » under their next chip, « à toi ! »
 * under the current one on their turn (then no marker over it), « toi dans 5 » under « +N » when their next turn is
 * hidden in it.</li>
 * </ul>
 * Pure geometry (the client draws it, the GameTests check it): every element is placed by the top-left corner of its
 * picture (a shape and {@link HudShapes#PAD} pixels of margin), and two shapes leave {@link HudShapes#GAP} empty
 * pixels between them on every row they share.
 */
public final class TurnStripLayout {
    /** The strip's axis: every shape of the strip is centred on the boundary between rows AXIS - 1 and AXIS. */
    public static final int AXIS = 36;
    /** Chip heights and head frames: small (later), medium (next), big (now). */
    public static final int[] HEIGHT = {14, 18, 24};
    public static final int[] FRAME = {10, 12, 14};
    /** A mini-game's or an event's medallion on the strip; the one inside the current step's pill. */
    public static final int MEDAL = 14, BIG_MEDAL = 18;
    public static final int NAME_MAX = 48, NEXT_NAME_MAX = 32;
    /** At most this many steps on the strip. */
    public static final int MAX_STEPS = 7;
    /** Narrower than this, the next player shows its initial only. */
    public static final int COMPACT_WIDTH = 180;
    /** Blank pawns in a round whose order is not known yet. */
    public static final int CAPSULE_PAWNS = 4;
    /** The marker over the current step. */
    public static final int MARKER_W = 7, MARKER_H = 5;
    /** A bubble's body and its pointer. */
    public static final int BUBBLE_H = 12, POINTER = 3;

    public enum Kind {
        /** A token's turn ({@link Step#player}). */
        TURN,
        MINI_GAME, EVENT,
        /** The turn order rolls. */
        START,
        /** The board being prepared. */
        PREPARING,
        END,
        /** A whole round whose turn order is not known yet ({@link Step#round}). */
        CAPSULE
    }

    /**
     * A step of the party.
     *
     * @param player TURN: index of its token in the party's players, -1 otherwise
     * @param round  the round it belongs to (1-based), 0 for none (start, end)
     * @param key    its identity from a layout to the next (its element goes on and moves)
     */
    public record Step(Kind kind, int player, int round, int key) {
    }

    public enum Type { PLAYER, BIG_STEP, STEP_DISC, CAPSULE, ROUND_DISC, MORE, PINNED, BUBBLE, MARKER }

    /** An element of the bar: where its picture goes, and what it shows. */
    public static final class El {
        public final String key;
        public final Type type;
        public final Form form;
        /** Its shape's size (its picture has {@link HudShapes#PAD} more on each side, a bubble its pointer too). */
        public final int w, h;
        /** The current step: a 2 px halo round it. */
        public final boolean halo;
        /** The top-left corner of its picture. */
        public int x, y;
        public final Step step;
        /** PLAYER: 0 small, 1 medium, 2 big. */
        public final int level;
        /** Its words: a player's name (fitted), a step's name, « +3 », « dans 2 », a bubble's text. */
        public final String label;
        /** PLAYER: the width given to the name (fixed for the current chip); ROUND_DISC, CAPSULE: the round. */
        public final int labelWidth, number;

        El(String key, Type type, Form form, int w, int h, boolean halo, Step step, int level, String label, int labelWidth, int number) {
            this.key = key;
            this.type = type;
            this.form = form;
            this.w = w;
            this.h = h;
            this.halo = halo;
            this.step = step;
            this.level = level;
            this.label = label;
            this.labelWidth = labelWidth;
            this.number = number;
        }

        public int pictureWidth() {
            return type == Type.MARKER ? MARKER_W + 2 * PAD : w + 2 * PAD;
        }

        public int pictureHeight() {
            if (type == Type.MARKER) return MARKER_H + 2 * PAD;
            return h + 2 * PAD + (type == Type.BUBBLE ? POINTER : 0);
        }

        /** Solid columns of each row of its picture ({-1, -1}: empty row). */
        public int[][] profile() {
            if (type == Type.MARKER) {
                int[][] rows = new int[pictureHeight()][];
                for (int y = 0; y < rows.length; y++)
                    rows[y] = y >= PAD && y < PAD + MARKER_H ? new int[]{PAD, PAD + MARKER_W - 1} : new int[]{-1, -1};
                return rows;
            }
            int[][] body = HudShapes.profile(form, w, h, halo);
            if (type != Type.BUBBLE) return body;
            // A bubble: its body under its pointer (3 rows, 1, 3 and 5 pixels wide, pointing up)
            int[][] rows = new int[pictureHeight()][];
            for (int y = 0; y < rows.length; y++) rows[y] = y >= POINTER ? body[y - POINTER] : new int[]{-1, -1};
            int cx = PAD + w / 2;
            for (int i = 0; i < POINTER; i++) {
                int[] row = rows[PAD + i];
                int half = i;
                rows[PAD + i] = row[0] < 0 ? new int[]{cx - half, cx + half} : new int[]{Math.min(row[0], cx - half), Math.max(row[1], cx + half)};
            }
            return rows;
        }

        /** The first and last solid rows of its picture, from its top. */
        public int top() {
            int[][] rows = profile();
            for (int y = 0; y < rows.length; y++) if (rows[y][0] >= 0) return y;
            return 0;
        }

        public int bottom() {
            int[][] rows = profile();
            for (int y = rows.length - 1; y >= 0; y--) if (rows[y][0] >= 0) return y;
            return rows.length - 1;
        }

        /** Its solid left and right columns, from its picture's left. */
        public int left() {
            int min = Integer.MAX_VALUE;
            for (int[] row : profile()) if (row[0] >= 0) min = Math.min(min, row[0]);
            return min;
        }

        public int right() {
            int max = 0;
            for (int[] row : profile()) max = Math.max(max, row[1]);
            return max;
        }
    }

    /**
     * What is laid out.
     *
     * @param steps  the step being played first, then the ones to come
     * @param rounds the party's rounds (0 if unknown)
     * @param me     index in the party's players of the token of this client's player, -1 for none
     * @param names  the names of the party's players (their tokens), in their order
     * @param width  the room for the strip, in pixels
     */
    public record Input(List<Step> steps, int rounds, int me, List<String> names, int width) {
    }

    /**
     * The bar.
     *
     * @param elements   its elements, the strip's in reading order, then the marker and the bubbles
     * @param nameWidth  the width of the current player's name (the party's longest name, capped)
     * @param pinnedIn   the next mini-game pinned before « +N »: in how many steps, -1 if not pinned
     * @param toiIn      my next turn hidden in « +N »: in how many steps, -1 if not
     * @param mineNow    the step being played is my turn (« à toi ! », no marker)
     * @param more       the rounds « +N » counts, 0 for none
     */
    public record Result(List<El> elements, int nameWidth, int pinnedIn, int toiIn, boolean mineNow, int more) {
        public El get(String key) {
            for (El el : elements) if (el.key.equals(key)) return el;
            return null;
        }

        /** Where the bar's pictures end (its width) and their lowest row (its height). */
        public int width() {
            int w = 0;
            for (El el : elements) w = Math.max(w, el.x + el.pictureWidth());
            return w;
        }

        public int height() {
            int h = 0;
            for (El el : elements) h = Math.max(h, el.y + el.pictureHeight());
            return h;
        }
    }

    private TurnStripLayout() {
    }

    /** The current chip's name width: the party's longest name, {@value #NAME_MAX} px at most. */
    public static int nameWidth(List<String> names, HudTexts texts) {
        int w = 0;
        for (String name : names) w = Math.max(w, texts.width(name));
        return Math.min(NAME_MAX, w);
    }

    public static Result layout(Input input, HudTexts texts) {
        List<Step> steps = input.steps();
        List<El> els = new ArrayList<>();
        if (steps.isEmpty()) return new Result(els, 0, -1, -1, false, 0);
        int width = input.width();
        boolean compact = width < COMPACT_WIDTH;
        int nameW = nameWidth(input.names(), texts);
        int kMini = -1, kMe = -1;
        for (int i = 1; i < steps.size(); i++) {
            Step s = steps.get(i);
            if (kMini < 0 && s.kind() == Kind.MINI_GAME) kMini = i;
            if (kMe < 0 && s.kind() == Kind.TURN && s.player() == input.me() && input.me() >= 0) kMe = i;
        }
        int shownRound = Math.max(1, steps.getFirst().round());
        int lastRound = steps.getFirst().round();
        El moreMax = more(99, texts), pinnedMax = pinned(9, texts);
        int moreRoom = moreMax.pictureWidth() - 2 * PAD + GAP;
        El prev = null;
        int count = 0, cut = steps.size();
        List<El> strip = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            int level = count == 0 ? 2 : count == 1 ? 1 : 0;
            El el = chip(s, level, nameW, compact, input.names(), texts);
            el.y = onAxis(el);
            boolean newRound = s.round() != lastRound && s.round() != 0 && prev != null;
            int x = prev == null ? 4 : gapX(prev, el, el.y, prev.x);
            El disc = null;
            if (newRound && s.kind() != Kind.CAPSULE) {
                disc = roundDisc(s.round());
                disc.y = onAxis(disc);
                disc.x = gapX(prev, disc, disc.y, prev.x) + 1;
                x = disc.x + disc.pictureWidth() - 2 * PAD + GAP + 1;
            }
            // Room for what must stay: the pinned mini-game (if not shown yet) and « +N »
            int need = moreRoom;
            if (kMini >= 0 && i < kMini) need += pinnedMax.pictureWidth() - 2 * PAD + GAP;
            int end = x - PAD + el.pictureWidth() - PAD;
            if (count >= MAX_STEPS || (prev != null && count >= 2 && end > width - need)
                    || (prev != null && end > width - moreRoom)) {
                cut = i;
                break;
            }
            if (disc != null) strip.add(disc);
            el.x = x;
            strip.add(el);
            if (s.round() != 0) {
                shownRound = Math.max(shownRound, s.round());
                lastRound = s.round();
            }
            prev = el;
            count++;
        }
        // The next mini-game always shows
        int pinnedIn = -1;
        if (kMini >= 0 && kMini >= cut && prev != null) {
            pinnedIn = kMini - 1;
            El p = pinned(pinnedIn, texts);
            p.y = onAxis(p);
            p.x = gapX(prev, p, p.y, prev.x) + 1;
            strip.add(p);
            prev = p;
        }
        // « +N »: the rounds left hidden (the steps left hidden when the rounds are not known)
        int left = input.rounds() > 0 ? input.rounds() - shownRound : steps.size() - cut;
        El morePill = null;
        if (left > 0 && prev != null) {
            morePill = more(left, texts);
            morePill.y = onAxis(morePill);
            morePill.x = gapX(prev, morePill, morePill.y, prev.x);
            strip.add(morePill);
        } else {
            left = 0;
        }
        els.addAll(strip);

        // Over the current step: the marker (not on my turn); under the strip: my bubbles
        El first = strip.getFirst();
        boolean mineNow = first.step != null && first.step.kind() == Kind.TURN && first.step.player() == input.me() && input.me() >= 0;
        int stripBottom = 0;
        for (El el : strip) stripBottom = Math.max(stripBottom, el.y + el.bottom());
        List<El> bubbles = new ArrayList<>();
        if (mineNow) {
            bubbles.add(bubbleUnder("toi_now", texts.toiNow(), first, stripBottom, texts));
        } else {
            El marker = new El("marker", Type.MARKER, Form.CUT1, MARKER_W, MARKER_H, false, null, 0, "", 0, 0);
            marker.x = first.x + first.pictureWidth() / 2 - marker.pictureWidth() / 2;
            marker.y = first.y + first.top() - GAP - MARKER_H - PAD;
            els.add(marker);
        }
        for (El el : strip) {
            if (el != first && el.type == Type.PLAYER && el.step.player() == input.me() && input.me() >= 0)
                bubbles.add(bubbleUnder("toi" + el.key, texts.toi(), el, stripBottom, texts));
        }
        int toiIn = -1;
        if (morePill != null && kMe >= cut) {
            toiIn = kMe - 1;
            bubbles.add(bubbleUnder("toi_more", texts.toiIn(toiIn), morePill, stripBottom, texts));
        }
        // Bubbles stay within the room (centred under their chip but at the ends: « toi dans 5 » is wider than
        // « +N »), and never overlap: from the right, a bubble moves left of the next one
        int right = width + PAD;
        for (El el : strip) right = Math.max(right, el.x + el.pictureWidth());
        for (int i = bubbles.size() - 1; i >= 0; i--) {
            El b = bubbles.get(i);
            b.x = Math.max(0, Math.min(b.x, right - b.pictureWidth()));
            if (i + 1 < bubbles.size()) {
                El next = bubbles.get(i + 1);
                while (b.x > 0 && gapX(b, next, next.y, Integer.MIN_VALUE) > next.x) b.x--;
            }
        }
        els.addAll(bubbles);
        return new Result(els, nameW, pinnedIn, toiIn, mineNow, left);
    }

    private static int onAxis(El el) {
        return AXIS - el.pictureHeight() / 2;
    }

    /** A bubble under an element, its pointer up to it, centred on it. */
    private static El bubbleUnder(String key, String label, El over, int stripBottom, HudTexts texts) {
        El b = bubble(key, label, texts);
        b.x = over.x + over.pictureWidth() / 2 - b.pictureWidth() / 2;
        // The pointer's tip two empty rows under the strip's lowest solid row
        b.y = stripBottom + 3 - PAD;
        return b;
    }

    /** The smallest x for {@code el} (its picture at {@code y}) leaving {@link HudShapes#GAP} empty pixels after {@code prev} on every row they share. */
    public static int gapX(El prev, El el, int y, int xMin) {
        int[][] p = prev.profile(), n = el.profile();
        int best = xMin;
        for (int row = 0; row < n.length; row++) {
            int pr = y + row - prev.y;
            if (pr < 0 || pr >= p.length || p[pr][0] < 0 || n[row][0] < 0) continue;
            best = Math.max(best, prev.x + p[pr][1] + 1 + GAP - n[row][0]);
        }
        return best;
    }

    // ------------------------------------------------------------------ the elements

    static El chip(Step s, int level, int nameW, boolean compact, List<String> names, HudTexts texts) {
        String key = "s" + s.key();
        return switch (s.kind()) {
            case TURN -> player(key, s, level, nameW, compact, names, texts);
            case CAPSULE -> {
                int h = 18, d = h / 4;
                yield new El(key, Type.CAPSULE, Form.CHEVRON, HudShapes.odd(d + 3 + 12 + 3 + CAPSULE_PAWNS * 7 + 3 + d), h, false, s, level, "", 0, s.round());
            }
            default -> {
                if (level == 2) {
                    String label = texts.step(s.kind());
                    yield new El(key, Type.BIG_STEP, Form.PILL, HudShapes.odd(3 + BIG_MEDAL + 4 + texts.width(label) + 1 + 6), 24, true, s, 2, label, texts.width(label), 0);
                }
                yield new El(key, Type.STEP_DISC, Form.PILL, MEDAL, MEDAL, false, s, level, "", 0, 0);
            }
        };
    }

    private static El player(String key, Step s, int level, int nameW, boolean compact, List<String> names, HudTexts texts) {
        int h = HEIGHT[level], d = h / 4, size = FRAME[level];
        String name = s.player() >= 0 && s.player() < names.size() ? names.get(s.player()) : "?";
        String label = "";
        int lw = 0;
        if (level == 2) {
            label = texts.fit(name, nameW);
            lw = nameW;
        } else if (level == 1) {
            label = compact || name.isEmpty() ? name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)) : texts.fit(name, NEXT_NAME_MAX);
            lw = texts.width(label);
        }
        int w = HudShapes.odd(d + 3 + size + (label.isEmpty() ? 0 : 4 + lw + 1) + 3 + d + 1);
        return new El(key, Type.PLAYER, Form.CHEVRON, w, h, level == 2, s, level, label, lw, 0);
    }

    static El roundDisc(int round) {
        int d = 10;
        int w = Math.max(d, 4 * Integer.toString(round).length() + 1 + 4);
        w += w % 2;
        return new El("rd" + round, Type.ROUND_DISC, Form.PILL, w, d, false, null, 0, Integer.toString(round), 0, round);
    }

    static El more(int n, HudTexts texts) {
        String label = "+" + n;
        return new El("more", Type.MORE, Form.PILL, HudShapes.odd(texts.width(label) + 1 + 10), 14, false, null, 0, label, texts.width(label), n);
    }

    static El pinned(int n, HudTexts texts) {
        String words = texts.pinned(n);
        return new El("pinned", Type.PINNED, Form.PILL, HudShapes.odd(14 + 3 + texts.width(words) + 1 + 6), 14, false, null, 0, words, texts.width(words), n);
    }

    static El bubble(String key, String label, HudTexts texts) {
        return new El(key, Type.BUBBLE, Form.PILL, HudShapes.odd(texts.width(label) + 1 + 8), BUBBLE_H, false, null, 0, label, texts.width(label), 0);
    }
}
