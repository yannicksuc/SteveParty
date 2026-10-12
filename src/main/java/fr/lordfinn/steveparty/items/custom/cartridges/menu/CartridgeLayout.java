package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import java.util.Arrays;
import java.util.List;

/**
 * Where the modules of a cartridge go in its shell: top to bottom in a column, then in another column when it is full
 * ({@code maxContentHeight}). Coordinates are relative to the shell's top-left corner. The first module is always at
 * ({@link #PAD_X}, {@link #TOP}): the screen handlers place the ghost slots there (the Inventory Cartridge's first
 * module), whatever the height of the texts under it.
 */
public final class CartridgeLayout {
    /** Shell margins: the side bevel and the traces' gutter; the label on top; the contacts at the bottom. */
    public static final int PAD_X = 10, TOP = 30, BOTTOM = 14;
    public static final int COLUMN_W = 140, COLUMN_GAP = 12, MODULE_GAP = 5;
    /** Between two half modules side by side; the narrowest half (a number's stepper). */
    public static final int HALF_GAP = 8, MIN_HALF_W = 62;
    /** A tab row's panel (see {@link CartridgeModule#tabs}): its inner margin, between its modules. */
    public static final int PANEL_PAD = 4, PANEL_GAP = 3;
    /** The narrowest column (a narrow window: the widgets shrink, the texts wrap or scroll). */
    public static final int MIN_COLUMN_W = 96;
    /** The player inventory's panel under the shell (its tab, 3 rows and the hotbar). */
    public static final int INVENTORY_W = 176, INVENTORY_H = 99, INVENTORY_GAP = 2;
    /** The content height before a second column, in a shell on its own / beside a tile's interface. */
    public static final int MAX_CONTENT_ALONE = 240 - TOP - BOTTOM;
    public static final int MAX_CONTENT_BESIDE_TILE = MAX_CONTENT_ALONE;
    /**
     * With the player's inventory under it (ghost slots), the shell has a fixed size, so that the slots never move:
     * two columns wide, and as high as the smallest GUI (240) leaves over the inventory.
     */
    public static final int SHELL_W_WITH_INVENTORY = 2 * PAD_X + 2 * COLUMN_W + COLUMN_GAP;
    public static final int SHELL_H_WITH_INVENTORY = 240 - INVENTORY_H - INVENTORY_GAP;
    public static final int MAX_CONTENT_WITH_INVENTORY = SHELL_H_WITH_INVENTORY - TOP - BOTTOM;
    /** The empty shell (no cartridge): a few lines. */
    public static final int EMPTY_CONTENT = 40;

    private final int[] xs, ys, ws, owners, panelBottoms;
    private final boolean[] rights;
    private final int width, height, columns;

    private CartridgeLayout(int[] xs, int[] ys, int[] ws, boolean[] rights, int[] owners, int[] panelBottoms,
                            int width, int height, int columns) {
        this.xs = xs;
        this.ys = ys;
        this.ws = ws;
        this.rights = rights;
        this.owners = owners;
        this.panelBottoms = panelBottoms;
        this.width = width;
        this.height = height;
        this.columns = columns;
    }

    /** With the modules' declared heights (see {@link CartridgeModule#height()}). */
    public static CartridgeLayout of(List<CartridgeModule> modules, int maxContentHeight) {
        int[] heights = new int[modules.size()];
        for (int i = 0; i < heights.length; i++) heights[i] = modules.get(i).height();
        return of(modules, maxContentHeight, heights, Integer.MAX_VALUE, COLUMN_W);
    }

    /**
     * @param heights the height of each module: the declared one, or (client) the one measured with the font, texts
     *                wrapped on as many lines as they need
     * @param maxColumns the columns the place leaves room for: the last one takes all the modules left (the shell
     *                   then scrolls)
     * @param columnW    the width of a column ({@link #COLUMN_W}, less in a narrow window)
     */
    public static CartridgeLayout of(List<CartridgeModule> modules, int maxContentHeight, int[] heights, int maxColumns, int columnW) {
        int n = modules.size();
        int[] xs = new int[n], ys = new int[n], ws = new int[n], owners = new int[n], panelBottoms = new int[n];
        boolean[] rights = new boolean[n];
        Arrays.fill(owners, -1);
        for (int i = 0; i < n; i++) {
            int last = Math.min(n - 1, i + modules.get(i).panelSize());
            for (int j = i + 1; j <= last; j++) owners[j] = i;
        }
        boolean pairs = halfWidth(columnW) >= MIN_HALF_W;
        int column = 0, y = 0, tallest = 0;
        for (int i = 0; i < n; i++) {
            // In a tab row's panel: placed with it
            if (owners[i] >= 0) continue;
            // Inside the previous module's box: its place, no height of its own
            if (i > 0 && modules.get(i).inline()) {
                xs[i] = xs[i - 1];
                ys[i] = ys[i - 1];
                ws[i] = ws[i - 1];
                continue;
            }
            int h = heights[i];
            boolean half = pairs && modules.get(i).isHalf();
            ws[i] = half ? halfWidth(columnW) : columnW;
            // The right half of a row started by a left half: beside it, the row as high as the higher one
            if (half && i > 0 && ws[i - 1] == ws[i] && !rights[i - 1] && owners[i - 1] < 0 && modules.get(i - 1).isHalf()
                    && !modules.get(i - 1).inline()) {
                rights[i] = true;
                xs[i] = xs[i - 1] + ws[i] + HALF_GAP;
                ys[i] = ys[i - 1];
                int bottom = ys[i] - TOP + Math.max(heights[i - 1], h);
                tallest = Math.max(tallest, bottom);
                y = bottom + MODULE_GAP;
                continue;
            }
            // A tab row: its panel's modules under it, inside a frame; the whole never split between columns
            int last = Math.min(n - 1, i + modules.get(i).panelSize());
            int group = h;
            if (last > i) group = h + 2 * PANEL_PAD + run(modules, i + 1, last, heights, ws[i] - 2 * PANEL_PAD, xs, ys, ws, rights);
            if (y > 0 && y + group > maxContentHeight && column + 1 < maxColumns) {
                column++;
                y = 0;
            }
            xs[i] = PAD_X + column * (columnW + COLUMN_GAP);
            ys[i] = TOP + y;
            for (int j = i + 1; j <= last; j++) {
                xs[j] += xs[i] + PANEL_PAD;
                ys[j] += ys[i] + h + PANEL_PAD;
            }
            panelBottoms[i] = ys[i] + group;
            y += group;
            tallest = Math.max(tallest, y);
            y += MODULE_GAP;
        }
        int columns = column + 1;
        int content = n == 0 ? EMPTY_CONTENT : tallest;
        return new CartridgeLayout(xs, ys, ws, rights, owners, panelBottoms, width(columns, columnW), TOP + content + BOTTOM, columns);
    }

    /**
     * Modules {@code from} to {@code to} top to bottom in {@code width}, half modules two a row, from (0, 0).
     * @return the height they take
     */
    private static int run(List<CartridgeModule> modules, int from, int to, int[] heights, int width, int[] xs, int[] ys,
                           int[] ws, boolean[] rights) {
        boolean pairs = halfWidth(width) >= MIN_HALF_W;
        int y = 0, bottom = 0;
        for (int j = from; j <= to; j++) {
            int h = heights[j];
            boolean half = pairs && modules.get(j).isHalf();
            ws[j] = half ? halfWidth(width) : width;
            if (half && j > from && ws[j - 1] == ws[j] && !rights[j - 1] && modules.get(j - 1).isHalf()) {
                rights[j] = true;
                xs[j] = xs[j - 1] + ws[j] + HALF_GAP;
                ys[j] = ys[j - 1];
                bottom = Math.max(bottom, ys[j] + h);
                y = bottom + PANEL_GAP;
                continue;
            }
            xs[j] = 0;
            ys[j] = y;
            bottom = Math.max(bottom, y + h);
            y += h + PANEL_GAP;
        }
        return bottom;
    }

    /** The width of a half module in a column {@code columnW} wide. */
    public static int halfWidth(int columnW) {
        return (columnW - HALF_GAP) / 2;
    }

    /** The shell's width for that many columns. */
    public static int width(int columns, int columnW) {
        return 2 * PAD_X + columns * columnW + (columns - 1) * COLUMN_GAP;
    }

    public int x(int module) {
        return xs[module];
    }

    public int y(int module) {
        return ys[module];
    }

    /** Its width: the column's, or half of it. */
    public int w(int module) {
        return ws[module];
    }

    /** The tab row whose panel it is in, -1 for none. */
    public int owner(int module) {
        return owners[module];
    }

    /** The bottom of a tab row's panel (relative to the shell), 0 for another module. */
    public int panelBottom(int module) {
        return panelBottoms[module];
    }

    /** Whether it is the right half of a row (beside the module before it). */
    public boolean right(int module) {
        return rights[module];
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int columns() {
        return columns;
    }

    /** The index of the first module of that kind, or -1. */
    public static int indexOf(List<CartridgeModule> modules, Class<? extends CartridgeModule> kind) {
        for (int i = 0; i < modules.size(); i++) {
            if (kind.isInstance(modules.get(i))) return i;
        }
        return -1;
    }
}
