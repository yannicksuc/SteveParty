package fr.lordfinn.steveparty.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static fr.lordfinn.steveparty.hud.HudShapes.GAP;

/**
 * The standings, laid out (the approved mock-up « S5, tableau des scores »: the art sources, with
 * the user's corrections): the currencies' items once, full size, over their columns; one thin row per player, best
 * first: its rank medallion (1, 1, 3 for a tie broken by nothing), its head, its pawn's name, its stars and coins in
 * right-aligned three-digit columns, and its bonuses (up to {@value #BONUS_SLOTS}; the column shows only when someone
 * has one); « toi » at the end of my row.
 * <ul>
 * <li>The names' column is as wide as the longest name shown, {@value #NAME_CAP} px at most (« … » beyond).</li>
 * <li>Past {@value #MAX_ROWS} players: the first three, « … N », me between my two neighbours, « … N », the last one
 * (a spectator: the first three, « … N », the last one).</li>
 * </ul>
 * Everything in one row shares one axis: the rank medallion, the frame of the head, the digits (8 rows with their
 * shadow) and the bonus slots are centred on the row's middle. Coordinates are the shapes' (masks), from the table's
 * top-left.
 */
public final class StandingsLayout {
    public static final int NAME_CAP = 64;
    public static final int MAX_ROWS = 8;
    public static final int BONUS_SLOTS = 3;
    public static final int ROW_H = 14, BADGE = 12, HEAD = 10, SLOT = 10, ICON = 16, GAP_H = 10;
    /** Where the plates start: after the rank medallion. */
    public static final int PLATE_X = 4 + BADGE + GAP + 1;
    /** The header (the items over their columns): its height. */
    public static final int HEADER_H = 20;

    /**
     * A player of the standings.
     *
     * @param index   its index in the party's players
     * @param rank    1 for the best (tied players share it)
     * @param name    its pawn's name
     * @param bonuses how many bonuses it holds
     * @param mine    the player of this client
     */
    public record Entry(int index, int rank, String name, int stars, int coins, int bonuses, boolean mine) {
    }

    /**
     * A row: a player at {@code y}, or « … N » for {@code hidden} players left out.
     */
    public record Row(Entry entry, int hidden, int y) {
        public boolean gap() {
            return entry == null;
        }
    }

    /**
     * The table.
     *
     * @param nameWidth   the names' column
     * @param plateWidth  a row's plate
     * @param bonuses     the bonus column shows
     * @param starColumn  where the stars' digits start, from a plate's left (then the coins', the bonuses')
     * @param digitsWidth a column of digits: « 888 »
     * @param bubbleWidth the « toi » bubble (its body), at the end of my row, or before it ({@code bubbleLeft})
     * @param rankX       where the rank medallions start (after the bubble when it is on the left)
     * @param plateX      where the rows' plates start
     * @param bubbleX     where my bubble's picture starts (its pointer included), from the table's left
     */
    public record Layout(List<Row> rows, int nameWidth, int plateWidth, boolean bonuses, int starColumn, int coinColumn,
                         int bonusColumn, int digitsWidth, int bubbleWidth, int width, int height, boolean bubbleLeft,
                         int rankX, int plateX, int bubbleX) {
        /** The names of the players shown, fitted. */
        public int nameX() {
            return 17;
        }
    }

    private StandingsLayout() {
    }

    /**
     * The rows shown: everyone up to {@value #MAX_ROWS} players, else the first three, me and my neighbours, the last
     * one, with « … N » for the others.
     *
     * @param ranked the players, best first
     */
    public static List<Row> collapse(List<Entry> ranked) {
        List<Row> rows = new ArrayList<>();
        int n = ranked.size();
        if (n <= MAX_ROWS) {
            for (Entry entry : ranked) rows.add(new Row(entry, 0, 0));
            return rows;
        }
        int me = -1;
        for (int i = 0; i < n; i++) if (ranked.get(i).mine()) me = i;
        TreeSet<Integer> keep = new TreeSet<>(List.of(0, 1, 2, n - 1));
        if (me >= 0) {
            for (int i = me - 1; i <= me + 1; i++) if (i >= 0 && i < n) keep.add(i);
        }
        int last = -1;
        for (int i : keep) {
            if (i > last + 1) rows.add(new Row(null, i - last - 1, 0));
            rows.add(new Row(ranked.get(i), 0, 0));
            last = i;
        }
        return rows;
    }

    /**
     * Lays the table out.
     *
     * @param ranked the players, best first
     */
    public static Layout layout(List<Entry> ranked, HudTexts texts) {
        return layout(ranked, texts, false);
    }

    /**
     * Lays the table out; {@code bubbleLeft}: my « toi » bubble before my row (the table anchored on the right: the
     * rows' ends stay on the screen's edge), else after it.
     */
    public static Layout layout(List<Entry> ranked, HudTexts texts, boolean bubbleLeft) {
        List<Row> shown = collapse(ranked);
        int nameW = 0;
        boolean bonuses = false;
        for (Entry entry : ranked) bonuses |= entry.bonuses() > 0;
        for (Row row : shown) if (!row.gap()) nameW = Math.max(nameW, texts.width(row.entry().name()));
        nameW = Math.min(NAME_CAP, nameW);
        int digits = texts.width("888");
        int star = 17 + nameW + 6, coin = star + digits + 6, bonus = coin + digits + 6;
        // The plate ends a pixel after the last bonus slot, or after the coins' column and its margin
        int plate = HudShapes.odd(bonuses ? bonus + BONUS_SLOTS * 12 - 1 : bonus - 1);
        int bubble = HudShapes.odd(texts.width(texts.toi()) + 1 + 8);
        List<Row> rows = new ArrayList<>();
        int y = 4 + HEADER_H;
        boolean mine = false;
        for (Row row : shown) {
            rows.add(new Row(row.entry(), row.hidden(), y));
            if (row.gap()) {
                y += GAP_H + GAP;
            } else {
                mine |= row.entry().mine();
                y += ROW_H + GAP;
            }
        }
        // My bubble: after my row, or before the rank medallions (pointing right at them)
        int bubbleRoom = mine ? bubble + 3 + GAP + 1 : 0;
        int shift = bubbleLeft ? bubbleRoom : 0;
        int plateX = PLATE_X + shift, rankX = 4 + shift;
        int bubbleX = bubbleLeft ? rankX - GAP - 1 - (bubble + 3) : plateX + plate + GAP + 1;
        int width = plateX + plate + (bubbleLeft ? 0 : bubbleRoom) + 6;
        return new Layout(rows, nameW, plate, bonuses, star, coin, bonus, digits, bubble, width, y + 4, bubbleLeft, rankX, plateX, bubbleX);
    }
}
