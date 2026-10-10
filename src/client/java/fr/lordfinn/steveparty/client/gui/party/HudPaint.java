package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.paint.PaintedTextures;
import fr.lordfinn.steveparty.client.gui.paint.PaintedTextures.Tex;
import fr.lordfinn.steveparty.client.gui.paint.PixelArt;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.hud.HudShapes;
import fr.lordfinn.steveparty.hud.HudShapes.Form;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.ColorHelper;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The party HUDs' pictures, painted like the approved mock-ups paint them (the art sources,
 * {@code shape}): any shape of {@link HudShapes} in the mod's bevelled look (a dark outline in the colour's hue, the
 * highlight along the top and left edges, the shadow along the bottom and right ones, a glossy band, a soft drop
 * shadow to float over the world), the current step's halo, the bubbles and the small pixel icons.
 * <p>
 * Each picture is painted once, when a layout first needs it (never per frame), into a texture of its own at one
 * texture pixel per GUI pixel; they are kept (the few widths the names make) and drawn with a vertex colour, so that
 * plates can fade. The {@link PixelArt} kit's party HUD theme.
 */
public final class HudPaint {
    /** The players' colours, in the order the tokens without a colour take them. */
    static final Ramp[] PLAYERS = {
            Ramp.of(0x4a0808, 0xffb7ae, 0xe8413c, 0xb02e26), // red
            Ramp.of(0x0a2650, 0xc0e0ff, 0x3a9bff, 0x2370d0), // blue
            Ramp.of(0x06381e, 0xb8f4d0, 0x2fae64, 0x1f8048), // emerald
            Ramp.of(0x3a1e08, 0xe8c4a0, 0x9a6436, 0x6e4420), // brown
            Ramp.of(0x4a0a2a, 0xffc6e2, 0xf36baa, 0xc8407e), // pink
            Ramp.of(0x2e0a4a, 0xe3c6ff, 0xa35cff, 0x7a38d0), // purple
            Ramp.of(0x00363c, 0xb8f6fa, 0x2ec8d8, 0x1a96a8), // cyan
            Ramp.of(0x1a1050, 0xd0c8ff, 0x6a5cff, 0x4a3ad0), // indigo
    };
    static final Ramp NEUTRAL = Ramp.of(0x3a3a3a, 0xffffff, 0xe8e8e8, 0xc4c4c4);
    static final Ramp MINI_GAME = Ramp.of(0x5a2800, 0xffd6a0, 0xf9901d, 0xcc6a10);
    static final Ramp EVENT = Ramp.of(0x2c4a00, 0xeeffb8, 0xa8d82a, 0x7aa818);
    static final Ramp GOLD = Ramp.of(0x5b2e00, 0xfff87e, 0xffc900, 0xff9e00);
    static final Ramp SILVER = Ramp.of(0x2f3a44, 0xffffff, 0xd6dde3, 0xa7b2bc);
    static final Ramp BRONZE = Ramp.of(0x4a2410, 0xffd2a8, 0xd98a4a, 0xa8612c);
    static final Ramp EMPTY_SLOT = Ramp.of(0x9a9a9a, 0xf4f4f4, 0xf4f4f4, 0xe8e8e8);
    static final Ramp PAWN = Ramp.of(0x3a3a3a, 0xffffff, 0xffffff, 0xdddddd);
    /** The notice's plate, and its gold version on my turn. */
    static final Ramp PLATE = new Ramp(0xFF003640, 0xF5FFFFFF, 0xE6FFFFFF, 0xE6DFE9EE);
    static final Ramp PLATE_GOLD = Ramp.of(0x5b2e00, 0xfffbe0, 0xfff6c8, 0xfff87e);
    static final int TEXT = 0xFFFFFFFF, TEXT_DARK = 0xFF404040;
    static final int HALO = 0xFFFFF3A0, HALO_EDGE = 0x806B4E00;

    /** A frame round a head: white, the outline in the player's colour. */
    static Ramp white(int outline) {
        return new Ramp(outline, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFDFE6EA);
    }

    /** The ramp of a token: its colour's nearest among the players' (the palette by turn order without one). */
    public static Ramp playerRamp(int color, int index) {
        if (color < 0) return PLAYERS[Math.floorMod(index, PLAYERS.length)];
        Ramp best = PLAYERS[0];
        long bestDistance = Long.MAX_VALUE;
        for (Ramp ramp : PLAYERS) {
            int dr = ColorHelper.Argb.getRed(ramp.body()) - ((color >> 16) & 0xFF), dg = ColorHelper.Argb.getGreen(ramp.body()) - ((color >> 8) & 0xFF),
                    db = ColorHelper.Argb.getBlue(ramp.body()) - (color & 0xFF);
            long distance = 2L * dr * dr + 4L * dg * dg + 3L * db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ramp;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ textures

    private static final PaintedTextures TEXTURES = new PaintedTextures("party_hud/painted_", 384);

    private HudPaint() {
    }

    /** The picture named {@code key}, {@code width} x {@code height}, painted by {@code paint} on first use. */
    private static Tex texture(String key, int width, int height, Consumer<int[][]> paint) {
        return TEXTURES.get(key, () -> {
            int[][] pixels = new int[height][width];
            paint.accept(pixels);
            return pixels;
        });
    }

    /** Forgets every painted picture (painted again when needed). */
    static void clear() {
        TEXTURES.clear();
    }

    static void draw(DrawContext context, Tex tex, int x, int y, float alpha) {
        if (alpha <= 0.02f) return;
        HudDraw.faded(alpha, () -> tex.draw(context, x, y));
    }

    // ------------------------------------------------------------------ shapes

    static final int SHADOW = PixelArt.SHADOW, OUTLINE = PixelArt.OUTLINE, BAND = PixelArt.BAND;

    /** A shape in the kit's look, in a picture with {@link HudShapes#PAD} pixels of margin. */
    static Tex shape(Form form, int w, int h, Ramp ramp, int flags) {
        String key = "s" + form + w + "x" + h + ramp + flags;
        return texture(key, w + 2 * HudShapes.PAD, h + 2 * HudShapes.PAD, out -> paint(out, HudShapes.padded(HudShapes.mask(form, w, h)), ramp, flags));
    }

    /**
     * The white frame round a head ({@code size} square, the outline in {@code outline}), open where the 8 x 8 face
     * goes: the face never lies under it, so it shows whatever order the draws end up in (ImmediatelyFast's HUD
     * batching draws each texture in the order of its first use: a frame first used after the skins covered them).
     */
    static Tex headFrame(int size, int outline) {
        Ramp ramp = white(outline);
        return texture("f" + size + ramp, size + 2 * HudShapes.PAD, size + 2 * HudShapes.PAD, out -> {
            paint(out, HudShapes.padded(HudShapes.mask(Form.CUT1, size, size)), ramp, OUTLINE);
            int o = HudShapes.PAD + (size - 8) / 2;
            for (int y = o; y < o + 8; y++) Arrays.fill(out[y], o, o + 8, 0);
        });
    }

    private static void paint(int[][] out, boolean[][] m, Ramp ramp, int flags) {
        PixelArt.paint(out, m, ramp, 1, flags);
    }

    /** The current step's halo round a shape: 2 px of pale gold, then 1 px of dark gold at half alpha. */
    static Tex halo(Form form, int w, int h) {
        String key = "h" + form + w + "x" + h;
        return texture(key, w + 2 * HudShapes.PAD, h + 2 * HudShapes.PAD, out -> {
            boolean[][] solid = HudShapes.dilate(HudShapes.padded(HudShapes.mask(form, w, h)), 1);
            boolean[][] two = HudShapes.dilate(solid, 2), three = HudShapes.dilate(solid, 3);
            for (int y = 0; y < out.length; y++) {
                for (int x = 0; x < out[y].length; x++) {
                    if (two[y][x] && !solid[y][x]) out[y][x] = HALO;
                    else if (three[y][x] && !two[y][x]) out[y][x] = HALO_EDGE;
                }
            }
        });
    }

    /** A ring of one pixel round a shape's outline (my row in the standings: gold). */
    static Tex ring(Form form, int w, int h, int colour) {
        String key = "r" + form + w + "x" + h + Integer.toHexString(colour);
        return texture(key, w + 2 * HudShapes.PAD, h + 2 * HudShapes.PAD, out -> {
            boolean[][] outline = HudShapes.dilate(HudShapes.padded(HudShapes.mask(form, w, h)), 1);
            boolean[][] ring = HudShapes.dilate(outline, 1);
            for (int y = 0; y < out.length; y++)
                for (int x = 0; x < out[y].length; x++) if (ring[y][x] && !outline[y][x]) out[y][x] = colour;
        });
    }

    /**
     * A yellow bubble: its body ({@code w} x 12, a drop shadow) and its pointer, up (under a chip) or left (at the end
     * of a row). Up: the picture is 3 rows higher, the body under the pointer; left: 3 columns wider.
     */
    static Tex bubble(int w, boolean left) {
        return bubble(w, left ? Pointer.LEFT : Pointer.UP);
    }

    /** Where a bubble's pointer is: up (under a chip), left or right (beside a row). */
    enum Pointer { UP, LEFT, RIGHT }

    /** A yellow bubble whose pointer is on one side: the picture is 3 pixels higher (up) or wider (left, right). */
    static Tex bubble(int w, Pointer pointer) {
        String key = "b" + w + pointer;
        int pad = HudShapes.PAD, h = 12;
        boolean side = pointer != Pointer.UP;
        int width = w + 2 * pad + (side ? 3 : 0), height = h + 2 * pad + (side ? 0 : 3);
        return texture(key, width, height, out -> {
            int[][] body = new int[h + 2 * pad][w + 2 * pad];
            paint(body, HudShapes.padded(HudShapes.mask(Form.PILL, w, h)), GOLD, SHADOW | OUTLINE);
            int ox = pointer == Pointer.LEFT ? 3 : 0, oy = side ? 0 : 3;
            for (int y = 0; y < body.length; y++) for (int x = 0; x < body[y].length; x++) out[y + oy][x + ox] = body[y][x];
            if (side) {
                int cy = pad + 6;
                for (int i = 0; i < 3; i++) {
                    int column = pointer == Pointer.LEFT ? pad + i : width - 1 - pad - i;
                    for (int y = cy - i - 1; y <= cy + i; y++) out[y][column] = y == cy - i - 1 || y == cy + i ? GOLD.outline() : GOLD.body();
                }
            } else {
                int cx = pad + w / 2;
                for (int i = 0; i < 3; i++) {
                    for (int x = cx - i; x <= cx + i; x++) out[pad + i][x] = Math.abs(x - cx) == i ? GOLD.outline() : GOLD.body();
                }
            }
        });
    }

    // ------------------------------------------------------------------ pixel icons

    /** A small icon from rows of characters, each one a colour (space: nothing). */
    static Tex pattern(String key, String[] rows, Map<Character, Integer> colours) {
        return TEXTURES.get("p" + key, () -> PixelArt.pattern(rows, colours));
    }

    /** The mini-game's icon, 10 x 8: the same as the dashboard's ({@link ConsolePaint#GAMEPAD}). */
    static Tex gamepad() {
        return pattern("gamepad", ConsolePaint.GAMEPAD, ConsolePaint.GAMEPAD_COLOURS);
    }

    static Tex bell() {
        return pattern("bell", new String[]{"   ##   ", "  ####  ", " ###### ", " ###### ", " ###### ", "########", "        ", "   dd   "},
                Map.of('#', EVENT.shadow(), 'd', EVENT.outline()));
    }

    static Tex die() {
        return pattern("die", new String[]{"########", "#wwwwww#", "#w##www#", "#w##www#", "#www##w#", "#www##w#", "#wwwwww#", "########"},
                Map.of('#', 0xFF3A3A3A, 'w', 0xFFFFFFFF));
    }

    static Tex gear() {
        return pattern("gear", new String[]{"  #  #  ", " ###### ", "##w##w##", " #w  w# ", " #w  w# ", "##w##w##", " ###### ", "  #  #  "},
                Map.of('#', 0xFF3A3A3A, 'w', 0xFF8A8A8A));
    }

    static Tex flag() {
        return pattern("flag", new String[]{"#######", "#w#w#w#", "##w#w##", "#w#w#w#", "#######", "#      ", "#      ", "#      "},
                Map.of('#', 0xFF3A3A3A, 'w', 0xFFFFFFFF));
    }

    /** « … », as three dots on the font's baseline row. */
    static Tex dots(int colour) {
        return pattern("dots" + Integer.toHexString(colour), new String[]{"", "", "", "", "", "", "# # #"}, Map.of('#', colour));
    }

    private static final String[][] SMALL = {
            {"###", "# #", "# #", "# #", "###"}, {" # ", "## ", " # ", " # ", "###"}, {"###", "  #", "###", "#  ", "###"},
            {"###", "  #", " ##", "  #", "###"}, {"# #", "# #", "###", "  #", "  #"}, {"###", "#  ", "###", "  #", "###"},
            {"###", "#  ", "###", "# #", "###"}, {"###", "  #", " # ", " # ", " # "}, {"###", "# #", "###", "# #", "###"},
            {"###", "# #", "###", "  #", "###"}};

    /** Small digits (3 x 5, 4 apart) for the round medallions and « … N ». */
    static void small(DrawContext context, String digits, int x, int y, int colour, float alpha) {
        for (int i = 0; i < digits.length(); i++) {
            int d = digits.charAt(i) - '0';
            if (d < 0 || d > 9) continue;
            draw(context, pattern("small" + d + Integer.toHexString(colour), SMALL[d], Map.of('#', colour)), x + 4 * i, y, alpha);
        }
    }

    /** Width of small digits. */
    static int smallWidth(String digits) {
        return 4 * digits.length() - 1;
    }
}
