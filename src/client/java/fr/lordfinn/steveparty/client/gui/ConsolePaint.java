package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.client.utils.ClientTextures;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.hud.HudShapes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * The controllers' screens painted like their approved mock-ups paint them (the art sources:
 * {@code box}, {@code pill}, {@code disc}, {@code bezel}, {@code inset}): any shape of the kit at its exact size, its
 * outline included, with bevels of 1 or 2 pixels and the glossy band of the pills. Each shape is painted once into a
 * texture of its own (one texture pixel per GUI pixel), then drawn as is: never painted per frame.
 */
public final class ConsolePaint {
    /** A colour ramp of the kit: outline, highlight, body, shadow. */
    public record Ramp(int outline, int hi, int body, int shadow) {
        public static Ramp of(int outline, int hi, int body, int shadow) {
            return new Ramp(0xFF000000 | outline, 0xFF000000 | hi, 0xFF000000 | body, 0xFF000000 | shadow);
        }
    }

    private record Tex(Identifier id, int width, int height) {
    }

    private static final Map<String, Tex> TEXTURES = new HashMap<>();
    private static final int MAX_TEXTURES = 256;
    private static int serial;

    private ConsolePaint() {
    }

    // ------------------------------------------------------------------ shapes

    /** A rectangle with its corners cut by {@code c} pixels (buttons: 1, panels: 2). */
    private static boolean[][] cut(int w, int h, int c) {
        boolean[][] m = new boolean[h][w];
        for (boolean[] row : m) java.util.Arrays.fill(row, true);
        for (int k = 0; k < c; k++) {
            int[][] corners = {{k, 0}, {0, k}, {w - 1 - k, 0}, {w - 1, k}, {k, h - 1}, {0, h - 1 - k}, {w - 1 - k, h - 1}, {w - 1, h - 1 - k}};
            for (int[] p : corners) if (p[1] >= 0 && p[1] < h && p[0] >= 0 && p[0] < w) m[p[1]][p[0]] = false;
        }
        return m;
    }

    /** {@code inner} with one pixel of margin all round: its outline. */
    private static boolean[][] margin(boolean[][] inner, int w, int h) {
        boolean[][] m = new boolean[h][w];
        for (int y = 0; y < inner.length; y++) System.arraycopy(inner[y], 0, m[y + 1], 1, inner[y].length);
        return m;
    }

    /** A kit shape exactly {@code w} x {@code h}, its outline included (PartyGui.button: bevel 1, cut 1; panels: 2, 2). */
    public static void box(DrawContext context, int x, int y, int w, int h, Ramp ramp, int bevel, int cut) {
        draw(context, texture("b" + w + "x" + h + ramp + bevel + "/" + cut, w, h, () -> margin(cut(w - 2, h - 2, cut), w, h), ramp, bevel, false), x, y);
    }

    /** A pill exactly {@code w} x {@code h}, its outline included, with or without its glossy band. */
    public static void pill(DrawContext context, int x, int y, int w, int h, Ramp ramp, boolean band) {
        draw(context, texture("p" + w + "x" + h + ramp + band, w, h, () -> margin(HudShapes.mask(HudShapes.Form.PILL, w - 2, h - 2), w, h), ramp, 1, band), x, y);
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
        draw(context, texture("t" + w + "x" + tabsH + "/" + panelH + "@" + tabX + "/" + tabW + ramp + b, w, h, () -> {
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
    public static void image(DrawContext context, String key, java.util.function.Supplier<int[][]> pixels, int x, int y) {
        Tex known = TEXTURES.get("x" + key);
        if (known == null) known = register("x" + key, pixels.get());
        draw(context, known, x, y);
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
        Tex known = TEXTURES.get("i" + key);
        if (known == null) {
            int w = 0;
            for (String row : rows) w = Math.max(w, row.length());
            int[][] out = new int[rows.length][w];
            for (int yy = 0; yy < rows.length; yy++) {
                for (int xx = 0; xx < rows[yy].length(); xx++) {
                    Integer c = colours.get(rows[yy].charAt(xx));
                    if (c != null) out[yy][xx] = c;
                }
            }
            known = register("i" + key, out);
        }
        draw(context, known, x, y);
    }

    /**
     * The highlight of a {@link #box} ({@code cut} its corners' cut) or of a {@link #pill} ({@code cut} -1) of the same
     * size, drawn over it: its outline in {@code ring}, its body in {@code fill} (0 for none). It follows the shape:
     * a round button lights up round, its cut corners stay cut.
     */
    public static void highlight(DrawContext context, int x, int y, int w, int h, int cut, int ring, int fill) {
        Tex known = TEXTURES.get("h" + w + "x" + h + "/" + cut + "/" + ring + "/" + fill);
        if (known == null) {
            boolean[][] m = cut < 0 ? margin(HudShapes.mask(HudShapes.Form.PILL, w - 2, h - 2), w, h) : margin(cut(w - 2, h - 2, cut), w, h);
            int[][] out = new int[h][w];
            for (int yy = 0; yy < h; yy++) {
                for (int xx = 0; xx < w; xx++) {
                    if (m[yy][xx]) {
                        out[yy][xx] = fill;
                        continue;
                    }
                    boolean edge = false;
                    for (int dy = -1; dy <= 1 && !edge; dy++) for (int dx = -1; dx <= 1 && !edge; dx++) edge = in(m, xx + dx, yy + dy);
                    if (edge) out[yy][xx] = ring;
                }
            }
            known = register("h" + w + "x" + h + "/" + cut + "/" + ring + "/" + fill, out);
        }
        draw(context, known, x, y);
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
        context.fill(x + 1, y + 1, x + w, y + h, body);
        context.fill(x + 2, y, x + w, y + 1, edge);
        context.fill(x, y + 2, x + 1, y + h, edge);
        context.fill(x + 1, y + 1, x + 2, y + 2, edge);
        context.fill(x + 1, y + h, x + w + 1, y + h + 1, low);
        context.fill(x + w, y + 1, x + w + 1, y + h + 1, low);
    }

    // ------------------------------------------------------------------ painting

    private static Tex texture(String key, int w, int h, java.util.function.Supplier<boolean[][]> mask, Ramp ramp, int bevel, boolean band) {
        Tex known = TEXTURES.get(key);
        if (known != null) return known;
        return register(key, paint(mask.get(), ramp, bevel, band));
    }

    private static Tex register(String key, int[][] out) {
        if (TEXTURES.size() >= MAX_TEXTURES) clear();
        int h = out.length, w = out[0].length;
        NativeImage image = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setColor(x, y, ColorHelper.Abgr.toAbgr(out[y][x]));
        Identifier id = Steveparty.id("console/painted_" + serial++);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
        Tex tex = new Tex(id, w, h);
        TEXTURES.put(key, tex);
        return tex;
    }

    private static boolean in(boolean[][] m, int x, int y) {
        return y >= 0 && y < m.length && x >= 0 && x < m[0].length && m[y][x];
    }

    /** The mock-ups' {@code shape}: outline (diagonals included), body, highlight then shadow on {@code bevel} pixels, band. */
    static int[][] paint(boolean[][] m, Ramp ramp, int bevel, boolean band) {
        int h = m.length, w = m[0].length;
        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (m[y][x]) {
                    out[y][x] = ramp.body();
                    continue;
                }
                boolean edge = false;
                for (int dy = -1; dy <= 1 && !edge; dy++) for (int dx = -1; dx <= 1 && !edge; dx++) edge = in(m, x + dx, y + dy);
                if (edge) out[y][x] = ramp.outline();
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y][x]) continue;
                for (int k = 1; k <= bevel; k++) if (!in(m, x, y - k) || !in(m, x - k, y)) out[y][x] = ramp.hi();
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y][x]) continue;
                for (int k = 1; k <= bevel; k++) if (!in(m, x, y + k) || !in(m, x + k, y)) out[y][x] = ramp.shadow();
            }
        }
        if (band) {
            int light = ColorHelper.Argb.lerp(0.45f, ramp.body(), 0xFFFFFFFF) | 0xFF000000;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean top = m[y][x] && !in(m, x, y - 2);
                    if (!m[y][x] || top) continue;
                    boolean aboveTop = in(m, x, y - 2) && !in(m, x, y - 4);
                    if (!aboveTop || !in(m, x - 1, y) || !in(m, x + 1, y)) continue;
                    out[y][x] = light;
                }
            }
        }
        return out;
    }

    private static void draw(DrawContext context, Tex tex, int x, int y) {
        RenderSystem.enableBlend();
        context.drawTexture(tex.id(), x, y, 0, 0, tex.width(), tex.height(), tex.width(), tex.height());
        RenderSystem.disableBlend();
    }

    /** Forgets every painted shape (painted again when needed). */
    public static void clear() {
        for (Tex tex : TEXTURES.values()) ClientTextures.destroy(tex.id());
        TEXTURES.clear();
    }
}
