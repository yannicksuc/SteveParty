package fr.lordfinn.steveparty.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.client.gui.paint.PaintedTextures;
import fr.lordfinn.steveparty.client.gui.paint.PixelArt;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.hud.HudShapes;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;

import java.util.Map;
import java.util.function.Supplier;

/**
 * The controllers' screens painted like their approved mock-ups paint them (the art sources:
 * {@code box}, {@code pill}, {@code disc}, {@code bezel}, {@code inset}): any shape of the kit at its exact size, its
 * outline included, with bevels of 1 or 2 pixels and the glossy band of the pills. The {@link PixelArt} kit's console
 * theme: each shape is painted once into a texture of its own, then drawn as is.
 */
public final class ConsolePaint {
    private static final PaintedTextures TEXTURES = new PaintedTextures("console/painted_", 256);

    private ConsolePaint() {
    }

    // ------------------------------------------------------------------ shapes

    /** A kit shape exactly {@code w} x {@code h}, its outline included (PartyGui.button: bevel 1, cut 1; panels: 2, 2). */
    public static void box(DrawContext context, int x, int y, int w, int h, Ramp ramp, int bevel, int cut) {
        draw(context, texture("b" + w + "x" + h + ramp + bevel + "/" + cut, () -> PixelArt.margin(HudShapes.cut(w - 2, h - 2, cut), w, h), ramp, bevel, false), x, y);
    }

    /** A pill exactly {@code w} x {@code h}, its outline included, with or without its glossy band. */
    public static void pill(DrawContext context, int x, int y, int w, int h, Ramp ramp, boolean band) {
        draw(context, texture("p" + w + "x" + h + ramp + band, () -> PixelArt.margin(HudShapes.mask(HudShapes.Form.PILL, w - 2, h - 2), w, h), ramp, 1, band), x, y);
    }

    /**
     * A panel and its selected tab as ONE shape (the creative inventory's way): their union outlined and bevelled once
     * (bevel {@code b} - 2), then the screen, with its 1 px edge, flowing from the panel up into the tab.
     *
     * @param x    the panel's left
     * @param y    the tabs' top (the panel starts {@code tabsH} lower)
     * @param tabX the selected tab's left, from the panel's
     */
    public static void tabbedBezel(DrawContext context, int x, int y, int w, int tabsH, int panelH, int tabX, int tabW, int b, Ramp ramp,
                                   int screen, int edge) {
        int h = tabsH + panelH;
        draw(context, texture("t" + w + "x" + tabsH + "/" + panelH + "@" + tabX + "/" + tabW + ramp + b, () -> {
            boolean[][] m = new boolean[h][w];
            int py = tabsH;
            // Inside the outline: the panel, its corners cut by one more pixel...
            for (int yy = py + 1; yy <= py + panelH - 2; yy++) for (int xx = 1; xx <= w - 2; xx++) m[yy][xx] = true;
            m[py + 1][1] = m[py + 1][w - 2] = m[py + panelH - 2][1] = m[py + panelH - 2][w - 2] = false;
            // ...and the tab, down into the panel's top edge, its top corners cut
            for (int yy = 1; yy <= py + 2; yy++) for (int xx = tabX + 1; xx <= tabX + tabW - 2; xx++) m[yy][xx] = true;
            m[1][tabX + 1] = m[1][tabX + tabW - 2] = false;
            return m;
        }, ramp, Math.max(1, b - 2), false), x, y);
        int py = y + tabsH;
        context.fill(x + b - 1, py + b - 1, x + w - b + 1, py + panelH - b + 1, edge);
        context.fill(x + tabX + 3, y + 3, x + tabX + tabW - 3, py + b, edge);
        context.fill(x + b, py + b, x + w - b, py + panelH - b, screen);
        context.fill(x + tabX + 4, y + 4, x + tabX + tabW - 4, py + b + 1, screen);
    }

    // ------------------------------------------------------------------ pixel icons

    /** The mini-game's icon, 10 x 8 (even: it centres in the 20 and 16 px chips and the HUD's medallions). */
    public static final String[] GAMEPAD = {"..######..", ".#wwwwww#.", "#wdwwwwrw#", "#dddwwbwr#", "#wdwwwwbw#", "#ww####ww#", "#w#....#w#", ".#......#."};
    public static final Map<Character, Integer> GAMEPAD_COLOURS = Map.of('#', 0xFF2A2F36, 'w', 0xFFE6E9EE, 'd', 0xFF3A3F48, 'r', 0xFFE8413C, 'b', 0xFF3A9BFF);

    public static void gamepad(DrawContext context, int x, int y) {
        pattern(context, "gamepad", GAMEPAD, GAMEPAD_COLOURS, x, y);
    }

    /** A picture made of pixels ({@code [y][x]}, ARGB, 0: nothing), painted once under {@code key}. */
    public static void image(DrawContext context, String key, Supplier<int[][]> pixels, int x, int y) {
        draw(context, TEXTURES.get("x" + key, pixels), x, y);
    }

    /** The « i » of every menu of the mod: a 12 px teal disc, its « i » white with its dark shadow. */
    public static void infoButton(DrawContext context, int ix, int iy) {
        disc(context, ix, iy, 12, Ramp.of(0x002a2a, 0xa0ffff, 0x00bbbb, 0x008c8c));
        int[][] light = {{5, 2}, {5, 4}, {5, 5}, {5, 6}, {5, 7}, {5, 8}}, dark = {{6, 3}, {6, 5}, {6, 6}, {6, 7}, {6, 8}, {6, 9}};
        for (int[] p : dark) context.fill(ix + p[0], iy + p[1], ix + p[0] + 1, iy + p[1] + 1, 0xFF006666);
        for (int[] p : light) context.fill(ix + p[0], iy + p[1], ix + p[0] + 1, iy + p[1] + 1, 0xFFFFFFFF);
    }

    /** A small icon from rows of characters, each one a colour (others: nothing). */
    public static void pattern(DrawContext context, String key, String[] rows, Map<Character, Integer> colours, int x, int y) {
        draw(context, TEXTURES.get("i" + key, () -> PixelArt.pattern(rows, colours)), x, y);
    }

    /**
     * The highlight of a {@link #box} ({@code cut} its corners' cut) or of a {@link #pill} ({@code cut} -1) of the same
     * size, drawn over it: its outline in {@code ring}, its body in {@code fill} (0 for none). It follows the shape:
     * a round button lights up round, its cut corners stay cut.
     */
    public static void highlight(DrawContext context, int x, int y, int w, int h, int cut, int ring, int fill) {
        draw(context, TEXTURES.get("h" + w + "x" + h + "/" + cut + "/" + ring + "/" + fill, () -> {
            boolean[][] m = cut < 0 ? PixelArt.margin(HudShapes.mask(HudShapes.Form.PILL, w - 2, h - 2), w, h) : PixelArt.margin(HudShapes.cut(w - 2, h - 2, cut), w, h);
            return PixelArt.paint(m, new Ramp(ring, fill, fill, fill), 0, PixelArt.OUTLINE);
        }), x, y);
    }

    /** A disc {@code d} pixels across, its outline included. */
    public static void disc(DrawContext context, int x, int y, int d, Ramp ramp) {
        pill(context, x, y, d, d, ramp, false);
    }

    /** A bezel: the panel (outline 1, bevel {@code b} - 2, corners cut by 2), the screen's edge (1 px) and its screen. */
    public static void bezel(DrawContext context, int x, int y, int w, int h, int b, Ramp ramp, int screen, int edge) {
        box(context, x, y, w, h, ramp, Math.max(1, b - 2), 2);
        context.fill(x + b - 1, y + b - 1, x + w - b + 1, y + h - b + 1, edge);
        context.fill(x + b, y + b, x + w - b, y + h - b, screen);
    }

    /** A sunken box (a slot: 18 x 18 for {@code w} = {@code h} = 17): dark top and left edge, light bottom and right one. */
    public static void inset(DrawContext context, int x, int y, int w, int h, int body, int edge, int low) {
        PixelArt.inset(context, x, y, w, h, body, edge, low);
    }

    /** Dark text with a light shadow one pixel down right (the mock-ups' {@code dark}). */
    public static void darkText(DrawContext context, TextRenderer textRenderer, OrderedText text, int x, int y, int colour, int shade) {
        context.drawText(textRenderer, text, x + 1, y + 1, shade, false);
        context.drawText(textRenderer, text, x, y, colour, false);
    }

    // ------------------------------------------------------------------ painting

    /** A shape in the console's look: always outlined, its bevel {@code bevel} pixels wide, with or without the band. */
    private static PaintedTextures.Tex texture(String key, Supplier<boolean[][]> mask, Ramp ramp, int bevel, boolean band) {
        return TEXTURES.get(key, () -> PixelArt.paint(mask.get(), ramp, bevel, PixelArt.OUTLINE | (band ? PixelArt.BAND : 0)));
    }

    private static void draw(DrawContext context, PaintedTextures.Tex tex, int x, int y) {
        RenderSystem.enableBlend();
        tex.draw(context, x, y);
        RenderSystem.disableBlend();
    }

    /** Forgets every painted shape (painted again when needed). */
    public static void clear() {
        TEXTURES.clear();
    }
}
