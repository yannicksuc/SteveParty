package fr.lordfinn.steveparty.items.custom.cartridges.menu;

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

    private final int[] xs, ys;
    private final int width, height, columns;

    private CartridgeLayout(int[] xs, int[] ys, int width, int height, int columns) {
        this.xs = xs;
        this.ys = ys;
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
        int[] xs = new int[n], ys = new int[n];
        int column = 0, y = 0, tallest = 0;
        for (int i = 0; i < n; i++) {
            int h = heights[i];
            if (y > 0 && y + h > maxContentHeight && column + 1 < maxColumns) {
                column++;
                y = 0;
            }
            xs[i] = PAD_X + column * (columnW + COLUMN_GAP);
            ys[i] = TOP + y;
            y += h;
            tallest = Math.max(tallest, y);
            y += MODULE_GAP;
        }
        int columns = column + 1;
        int content = n == 0 ? EMPTY_CONTENT : tallest;
        return new CartridgeLayout(xs, ys, width(columns, columnW), TOP + content + BOTTOM, columns);
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
