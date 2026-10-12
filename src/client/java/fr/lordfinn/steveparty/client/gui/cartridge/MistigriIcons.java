package fr.lordfinn.steveparty.client.gui.cartridge;

import fr.lordfinn.steveparty.components.MistigriOutcomes.Kind;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Target;
import net.minecraft.client.gui.DrawContext;

/**
 * The small pixel pictograms of the Mistigri's sentence list: who a sentence falls on (a figure with a mark: a space
 * under it, a crown, a rain cloud, three of them, a coin stack...), the badge over a kind's icon (lose, give, share),
 * the weight and the picked cards. Drawn pixel by pixel from masks: {@code #} the figure, {@code o} its mark, {@code x}
 * a dimmed part.
 */
final class MistigriIcons {
    /** A target pictogram's size. */
    static final int SIZE = 11;
    private static final int FIGURE = 0xFFE6E6E6, DIM = 0xFF6E6E6E;

    private static final String[][] TARGETS = {
            { // LANDED: on a space (the plum tile)
                    "....###....",
                    "....###....",
                    "...#####...",
                    "..#######..",
                    "..#.###.#..",
                    "....###....",
                    "....#.#....",
                    "....#.#....",
                    "...........",
                    ".ooooooooo.",
                    "..ooooooo.."},
            { // FIRST: a crown
                    "..o..o..o..",
                    "..ooooooo..",
                    "....###....",
                    "....###....",
                    "...#####...",
                    "..#######..",
                    "..#.###.#..",
                    "....###....",
                    "....#.#....",
                    "....#.#....",
                    "....#.#...."},
            { // LAST: a rain cloud
                    "...ooooo...",
                    "..ooooooo..",
                    "..o.o.o.o..",
                    "....###....",
                    "....###....",
                    "...#####...",
                    "..#.###.#..",
                    "..#.###.#..",
                    "....###....",
                    "....#.#....",
                    "....#.#...."},
            { // ALL: three of them
                    "...........",
                    "...........",
                    "###.###.###",
                    "###.###.###",
                    "...........",
                    "###.###.###",
                    "###.###.###",
                    "###.###.###",
                    "#.#.#.#.#.#",
                    "#.#.#.#.#.#",
                    "..........."},
            { // OTHERS: three, the middle one (the one who landed) dimmed
                    "...........",
                    "...........",
                    "###.xxx.###",
                    "###.xxx.###",
                    "...........",
                    "###.xxx.###",
                    "###.xxx.###",
                    "###.xxx.###",
                    "#.#.x.x.#.#",
                    "#.#.x.x.#.#",
                    "..........."},
            { // RICHEST: a stack of coins
                    ".###.......",
                    ".###.......",
                    "#####.ooooo",
                    "#####......",
                    "#####.ooooo",
                    ".###.......",
                    ".#.#..ooooo",
                    ".#.#.......",
                    ".#.#..ooooo",
                    ".......ooo.",
                    "......ooooo"},
            { // POOREST: one empty coin
                    ".###.......",
                    ".###.......",
                    "#####......",
                    "#####......",
                    "#####......",
                    ".###.......",
                    ".#.#...ooo.",
                    ".#.#..o...o",
                    ".#.#..o...o",
                    "......o...o",
                    ".......ooo."},
            { // RANDOM: a question mark
                    "...ooooo...",
                    "..oo...oo..",
                    "..oo...oo..",
                    ".......oo..",
                    "......oo...",
                    ".....oo....",
                    ".....oo....",
                    "...........",
                    ".....oo....",
                    ".....oo....",
                    "..........."}};

    /** Each target's mark colour. */
    private static final int[] MARKS = {0xFFB57BDB, 0xFFFFC52E, 0xFF9DB4C8, FIGURE, FIGURE, 0xFFFFC52E, 0xFFB08A3A, 0xFFFFC52E};

    private static final String[] WEIGHT = {
            "..ooo..",
            "..o.o..",
            ".ooooo.",
            "ooooooo",
            "ooooooo",
            "ooooooo",
            ".ooooo."};

    private static final String[] CARDS = {
            "xxxxx.....",
            "x...x.....",
            "x..#####..",
            "x..#...#..",
            "x..#.o.#..",
            "xxx#ooo#..",
            "...#.o.#..",
            "...#...#..",
            "...#####..",
            ".........."};

    private static final String[] MINUS = {
            ".......",
            ".......",
            ".......",
            ".#####.",
            ".......",
            ".......",
            "......."}, PLUS = {
            ".......",
            "...#...",
            "...#...",
            ".#####.",
            "...#...",
            "...#...",
            "......."}, SHARE = {
            ".......",
            "..#....",
            ".#####.",
            "..#..#.",
            ".#####.",
            ".....#.",
            "......."};

    private MistigriIcons() {
    }

    /** {@code target}'s pictogram, {@link #SIZE} square, dimmed when {@code off}. */
    static void target(DrawContext context, Target target, int x, int y, boolean off) {
        draw(context, TARGETS[target.ordinal()], x, y, off ? DIM : FIGURE, off ? DIM : MARKS[target.ordinal()], 0xFF4A4A4A);
    }

    /** The weight (a kettlebell), 7 square. */
    static void weight(DrawContext context, int x, int y, int color) {
        draw(context, WEIGHT, x, y, color, color, color);
    }

    /** The picked cards (a card picked from the deck), 10 square. */
    static void cards(DrawContext context, int x, int y) {
        draw(context, CARDS, x, y, FIGURE, 0xFFFFC52E, DIM);
    }

    /**
     * The badge over a kind's 16-pixel icon at ({@code x}, {@code y}): a red minus (they lose), a green plus (they get),
     * blue arrows (the common pot); none for the others (their icon says it).
     */
    static void badge(DrawContext context, Kind kind, int x, int y) {
        String[] mask;
        int color;
        switch (kind) {
            case LOSE_COINS, LOSE_STARS -> {
                mask = MINUS;
                color = 0xFFD9283B;
            }
            case PITY -> {
                mask = PLUS;
                color = 0xFF46AE2E;
            }
            case SHARE_COINS, SHARE_STARS -> {
                mask = SHARE;
                color = 0xFF2A8FC0;
            }
            default -> {
                return;
            }
        }
        int bx = x + 9, by = y + 9;
        context.fill(bx, by + 1, bx + 7, by + 6, 0xFF101010);
        context.fill(bx + 1, by, bx + 6, by + 7, 0xFF101010);
        context.fill(bx + 1, by + 1, bx + 6, by + 6, color);
        draw(context, mask, bx, by, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF);
    }

    private static void draw(DrawContext context, String[] mask, int x, int y, int figure, int mark, int dim) {
        for (int r = 0; r < mask.length; r++) {
            String line = mask[r];
            int c = 0;
            while (c < line.length()) {
                char ch = line.charAt(c);
                if (ch == '.') {
                    c++;
                    continue;
                }
                // One fill per run of the same pixel
                int end = c;
                while (end < line.length() && line.charAt(end) == ch) end++;
                context.fill(x + c, y + r, x + end, y + r + 1, ch == '#' ? figure : ch == 'o' ? mark : dim);
                c = end;
            }
        }
    }
}
