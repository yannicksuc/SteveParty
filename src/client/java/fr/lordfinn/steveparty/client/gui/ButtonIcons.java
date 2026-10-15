package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * The mod's one set of button icons ({@code button_icons.png}, made with the Workshop's GUI kit): each icon in a 16 x 16
 * cell, its box (10 x 10 for a symbol, 12 x 12 for a pictogram) in the cell's middle, strokes 2 px wide, the ink white
 * so that it takes the colour of the button's face (dark on a light face, brown on the gold one, white with the font's
 * shadow on the green one). An icon is centred by its box in its button and never moves: pressing a button only changes
 * its face ({@link PartyButton#icon}). The coloured ones (the (i), the hourglasses) keep their colours.
 */
public final class ButtonIcons {
    public static final Identifier SHEET = Steveparty.id("textures/gui/button_icons.png");
    public static final int CELL = 16;
    private static final int SHEET_W = 256, SHEET_H = 32;

    /** In the sheet's order (the GUI kit's). */
    public enum Icon {
        MINUS, PLUS, LESS, LESS_OR_EQUAL, EQUAL, GREATER_OR_EQUAL, GREATER, ANY, PLAY, BACK, UP, DOWN, PREV, NEXT, CROSS, CHECK,
        TRASH, LOCK, STAR, SAVE, UNDO, REDO, INFO(true), HOURGLASS(true), HOURGLASS_OFF(true),
        // added after the first sheet: always at the end, a cell never moves
        STOP;

        private final boolean coloured;

        Icon() {
            this(false);
        }

        Icon(boolean coloured) {
            this.coloured = coloured;
        }

        /** It keeps its own colours (drawn untinted). */
        public boolean coloured() {
            return coloured;
        }
    }

    private ButtonIcons() {
    }

    /** {@code icon} in the middle of the box (x, y, w, h), its ink in {@code argb}. */
    public static void draw(DrawContext context, Icon icon, int x, int y, int w, int h, int argb) {
        cell(context, icon, x + (w - CELL) / 2, y + (h - CELL) / 2, argb, false);
    }

    /** The same with the font's shadow (a quarter of its colour, one pixel down and right): white ink on a green face. */
    public static void draw(DrawContext context, Icon icon, int x, int y, int w, int h, int argb, boolean shadow) {
        cell(context, icon, x + (w - CELL) / 2, y + (h - CELL) / 2, argb, shadow);
    }

    /** {@code icon} centred on (centreX, centreY), as {@link PartyButton.Content} gives a button's middle. */
    public static void drawCentred(DrawContext context, Icon icon, int centreX, int centreY, int argb, boolean shadow) {
        cell(context, icon, centreX - CELL / 2, centreY - CELL / 2, argb, shadow);
    }

    private static void cell(DrawContext context, Icon icon, int x, int y, int argb, boolean shadow) {
        if (shadow && !icon.coloured()) tinted(context, icon, x + 1, y + 1, ComparisonGlyphs.shadowColour(argb));
        tinted(context, icon, x, y, icon.coloured() ? 0xFFFFFFFF : argb);
    }

    private static void tinted(DrawContext context, Icon icon, int x, int y, int argb) {
        int u = (icon.ordinal() % 16) * CELL, v = (icon.ordinal() / 16) * CELL;
        context.setShaderColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
        context.drawTexture(SHEET, x, y, u, v, CELL, CELL, SHEET_W, SHEET_H);
        context.setShaderColor(1, 1, 1, 1);
    }
}
