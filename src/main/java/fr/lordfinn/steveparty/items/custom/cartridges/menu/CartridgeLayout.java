package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import java.util.List;

/**
 * Where the modules of a cartridge go in its shell: top to bottom in a column, then in a second column when the
 * first is full ({@code maxContentHeight}). Pure numbers, the same on both sides (the screen handler places the ghost
 * slots with it, the screen draws with it). Coordinates are relative to the shell's top-left corner.
 */
public final class CartridgeLayout {
    /** Shell margins: the side bevel and the traces' gutter; the label on top; the contacts at the bottom. */
    public static final int PAD_X = 10, TOP = 30, BOTTOM = 14;
    public static final int COLUMN_W = 140, COLUMN_GAP = 12, MODULE_GAP = 5;
    /** The player inventory's panel under the shell (its tab, 3 rows and the hotbar). */
    public static final int INVENTORY_W = 176, INVENTORY_H = 99, INVENTORY_GAP = 2;
    /** Content heights that keep the whole screen within 240 GUI pixels (the smallest GUI height). */
    public static final int MAX_CONTENT_WITH_INVENTORY = 240 - INVENTORY_H - INVENTORY_GAP - TOP - BOTTOM;
    public static final int MAX_CONTENT_ALONE = 180;
    /** Next to a tile's interface (183 high). */
    public static final int MAX_CONTENT_BESIDE_TILE = 183 - TOP - BOTTOM;
    /** The empty shell (no cartridge): one line. */
    public static final int EMPTY_CONTENT = 20;

    private final int[] xs, ys;
    private final int width, height, columns;

    private CartridgeLayout(int[] xs, int[] ys, int width, int height, int columns) {
        this.xs = xs;
        this.ys = ys;
        this.width = width;
        this.height = height;
        this.columns = columns;
    }

    public static CartridgeLayout of(List<CartridgeModule> modules, int maxContentHeight) {
        int n = modules.size();
        int[] xs = new int[n], ys = new int[n];
        int column = 0, y = 0, tallest = 0;
        for (int i = 0; i < n; i++) {
            int h = modules.get(i).height();
            if (y > 0 && y + h > maxContentHeight) {
                column++;
                y = 0;
            }
            xs[i] = PAD_X + column * (COLUMN_W + COLUMN_GAP);
            ys[i] = TOP + y;
            y += h;
            tallest = Math.max(tallest, y);
            y += MODULE_GAP;
        }
        int columns = column + 1;
        int content = n == 0 ? EMPTY_CONTENT : tallest;
        return new CartridgeLayout(xs, ys, 2 * PAD_X + columns * COLUMN_W + (columns - 1) * COLUMN_GAP, TOP + content + BOTTOM, columns);
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
