package fr.lordfinn.steveparty.client.gui.cartridge;

import fr.lordfinn.steveparty.client.gui.UiText;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * What goes inside the cartridge menus' small buttons, centred on the pixel grid: the − + × ◀ signs drawn as pixels
 * (a font glyph sits off by one in a button of an even size), and a text centred on its ink (its cap height, 7
 * pixels; its width without the font's trailing space). A button of an odd size centres a 1-pixel stroke exactly, one
 * of an even size a 2-pixel one. The content never moves with the button's state: only its colours change.
 */
public final class ButtonGlyphs {
    /** The height of a capital letter or a figure in the game's font. */
    public static final int CAP_H = 7;

    private ButtonGlyphs() {
    }

    /** {@code text} centred in the box ({@code x}, {@code y}, {@code w}, {@code h}): the same room above and below its capitals. */
    public static void text(DrawContext context, TextRenderer font, Text text, int x, int y, int w, int h, int color, boolean shadow) {
        int ink = font.getWidth(text) - 1;
        UiText.line(context, font, text, x + (w - ink) / 2, y + (h - CAP_H) / 2, ink + 1, color, shadow);
    }

    public static void text(DrawContext context, TextRenderer font, String text, int x, int y, int w, int h, int color) {
        text(context, font, Text.literal(text), x, y, w, h, color, false);
    }

    /** The sign's size and stroke in a box: 5 / 1 in an odd box, 6 / 2 in an even one (per axis). */
    private static int size(int box) {
        return box % 2 == 1 ? 5 : 6;
    }

    public static void minus(DrawContext context, int x, int y, int w, int h, int color) {
        int sw = size(w), sh = h % 2 == 1 ? 1 : 2;
        int gx = x + (w - sw) / 2, gy = y + (h - sh) / 2;
        context.fill(gx, gy, gx + sw, gy + sh, color);
    }

    public static void plus(DrawContext context, int x, int y, int w, int h, int color) {
        int s = Math.min(size(w), size(h));
        int strokeX = w % 2 == 1 ? 1 : 2, strokeY = h % 2 == 1 ? 1 : 2;
        int cx = x + (w - strokeX) / 2, cy = y + (h - strokeY) / 2;
        int sx = x + (w - s) / 2, sy = y + (h - s) / 2;
        context.fill(sx, cy, sx + s, cy + strokeY, color);
        context.fill(cx, sy, cx + strokeX, sy + s, color);
    }

    /** A diagonal cross (delete), as wide as high: 5 in an odd box, 6 in an even one. */
    public static void cross(DrawContext context, int x, int y, int w, int h, int color) {
        int s = Math.min(size(w), size(h));
        int gx = x + (w - s) / 2, gy = y + (h - s) / 2;
        for (int i = 0; i < s; i++) {
            context.fill(gx + i, gy + i, gx + i + 1, gy + i + 1, color);
            context.fill(gx + s - 1 - i, gy + i, gx + s - i, gy + i + 1, color);
        }
    }

    /** A small triangle pointing left (earlier in the order): 3 wide, 5 high. */
    public static void left(DrawContext context, int x, int y, int w, int h, int color) {
        int gx = x + (w - 3) / 2, gy = y + (h - 5) / 2;
        context.fill(gx, gy + 2, gx + 3, gy + 3, color);
        context.fill(gx + 1, gy + 1, gx + 3, gy + 4, color);
        context.fill(gx + 2, gy, gx + 3, gy + 5, color);
    }
}
