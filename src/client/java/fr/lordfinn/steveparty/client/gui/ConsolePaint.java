package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.hud.HudShapes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
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
    private static final int MAX_TEXTURES = 128;
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

    /** A kit shape exactly {@code w} x {@code h}, its outline included (PartyGui.button: bevel 1, cut 1; panels: 2, 2). */
    public static void box(DrawContext context, int x, int y, int w, int h, Ramp ramp, int bevel, int cut) {
        draw(context, texture("b" + w + "x" + h + ramp + bevel + "/" + cut, w, h, () -> cut(w - 2, h - 2, cut), ramp, bevel, false), x, y);
    }

    /** A pill exactly {@code w} x {@code h}, its outline included, with or without its glossy band. */
    public static void pill(DrawContext context, int x, int y, int w, int h, Ramp ramp, boolean band) {
        draw(context, texture("p" + w + "x" + h + ramp + band, w, h, () -> HudShapes.mask(HudShapes.Form.PILL, w - 2, h - 2), ramp, 1, band), x, y);
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
        if (TEXTURES.size() >= MAX_TEXTURES) clear();
        // The mask with one pixel of margin all round: its outline
        boolean[][] inner = mask.get();
        boolean[][] m = new boolean[h][w];
        for (int y = 0; y < inner.length; y++) System.arraycopy(inner[y], 0, m[y + 1], 1, inner[y].length);
        int[][] out = paint(m, ramp, bevel, band);
        NativeImage image = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setColorArgb(x, y, out[y][x]);
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
            int light = ColorHelper.lerp(0.45f, ramp.body(), 0xFFFFFFFF) | 0xFF000000;
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
        context.drawTexture(RenderLayer::getGuiTextured, tex.id(), x, y, 0, 0, tex.width(), tex.height(), tex.width(), tex.height());
    }

    /** Forgets every painted shape (painted again when needed). */
    public static void clear() {
        var manager = MinecraftClient.getInstance().getTextureManager();
        for (Tex tex : TEXTURES.values()) manager.destroyTexture(tex.id());
        TEXTURES.clear();
    }
}
