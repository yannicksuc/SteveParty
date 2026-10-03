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
    /** The table's frame hugs what it draws: the rank medallions' outline on its left, the items on its top. */
    public static final int LEFT = 1, TOP = 0;
    public static final int PLATE_X = LEFT + BADGE + GAP + 1;
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
     * @param ranked      someone holds stars or coins: the rank medallions show (before that, no column for them)
     * @param rankX       where the rank medallions start
     * @param plateX      where the rows' plates start
     */
    public record Layout(List<Row> rows, int nameWidth, int plateWidth, boolean bonuses, int starColumn, int coinColumn,
                         int bonusColumn, int digitsWidth, int width, int height, boolean ranked, int rankX, int plateX) {
        /** The names of the players shown, fitted. */
        public int nameX() {
            return 17;
        }

        /**
         * Where a currency's item (16 px) goes over its column, from a plate's left: centred over the column's last
         * two digits (the numbers are right-aligned: most are one or two digits long).
         */
        public int iconX(int column, int twoDigits) {
            return column + digitsWidth - (twoDigits + 1) / 2 - ICON / 2;
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
     * Lays the table out. Ranked players (a rank above 0) show a rank medallion before their row; until someone holds
     * stars or coins nobody is ranked, and the table has no medallion column. My row is marked by a gold outline (a
     * pixel round its plate): no room taken.
     *
     * @param ranked the players, best first
     */
    public static Layout layout(List<Entry> ranked, HudTexts texts) {
        List<Row> shown = collapse(ranked);
        int nameW = 0;
        boolean bonuses = false, ranks = false;
        for (Entry entry : ranked) {
            bonuses |= entry.bonuses() > 0;
            ranks |= entry.rank() > 0;
        }
        for (Row row : shown) if (!row.gap()) nameW = Math.max(nameW, texts.width(row.entry().name()));
        nameW = Math.min(NAME_CAP, nameW);
        int digits = texts.width("888");
        int star = 17 + nameW + 6, coin = star + digits + 6, bonus = coin + digits + 6;
        // The plate ends a pixel after the last bonus slot, or after the coins' column and its margin
        int plate = HudShapes.odd(bonuses ? bonus + BONUS_SLOTS * 12 - 1 : bonus - 1);
        List<Row> rows = new ArrayList<>();
        int y = TOP + HEADER_H;
        for (Row row : shown) {
            rows.add(new Row(row.entry(), row.hidden(), y));
            y += row.gap() ? GAP_H + GAP : ROW_H + GAP;
        }
        // My row's gold outline: a pixel round the plate's outline, on every side
        int plateX = ranks ? PLATE_X : LEFT + 1;
        int width = plateX + plate + 2;
        return new Layout(rows, nameW, plate, bonuses, star, coin, bonus, digits, width, y, ranks, LEFT, plateX);
    }
}
