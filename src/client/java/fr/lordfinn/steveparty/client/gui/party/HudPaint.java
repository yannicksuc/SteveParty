package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.hud.HudShapes;
import fr.lordfinn.steveparty.hud.HudShapes.Form;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The party HUDs' pictures, painted like the approved mock-ups paint them (the art sources,
 * {@code shape}): any shape of {@link HudShapes} in the mod's bevelled look (a dark outline in the colour's hue, the
 * highlight along the top and left edges, the shadow along the bottom and right ones, a glossy band, a soft drop
 * shadow to float over the world), the current step's halo, the bubbles and the small pixel icons.
 * <p>
 * Each picture is painted once, when a layout first needs it (never per frame), into a texture of its own at one
 * texture pixel per GUI pixel; they are kept (the few widths the names make) and drawn with a vertex colour, so that
 * plates can fade.
 */
public final class HudPaint {
    /** A colour ramp of the kit: outline, highlight, body, shadow (opaque ARGB, or with their own alpha). */
    public record Ramp(int outline, int hi, int body, int shadow) {
        static Ramp of(int outline, int hi, int body, int shadow) {
            return new Ramp(0xFF000000 | outline, 0xFF000000 | hi, 0xFF000000 | body, 0xFF000000 | shadow);
        }

        /** The standings' rows: the colour, very light. */
        Ramp pastel() {
            return new Ramp(outline, 0xFFFFFFFF, mix(body, 0xFFFFFFFF, 0.8f), mix(body, 0xFFFFFFFF, 0.6f));
        }
    }

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

    static int mix(int a, int b, float t) {
        return ColorHelper.lerp(t, a, b) | 0xFF000000;
    }

    /** The ramp of a token: its colour's nearest among the players' (the palette by turn order without one). */
    public static Ramp playerRamp(int color, int index) {
        if (color < 0) return PLAYERS[Math.floorMod(index, PLAYERS.length)];
        Ramp best = PLAYERS[0];
        long bestDistance = Long.MAX_VALUE;
        for (Ramp ramp : PLAYERS) {
            int dr = ColorHelper.getRed(ramp.body) - ((color >> 16) & 0xFF), dg = ColorHelper.getGreen(ramp.body) - ((color >> 8) & 0xFF),
                    db = ColorHelper.getBlue(ramp.body) - (color & 0xFF);
            long distance = 2L * dr * dr + 4L * dg * dg + 3L * db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ramp;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ textures

    /** A painted picture. */
    record Tex(Identifier id, int width, int height) {
    }

    private static final int MAX_TEXTURES = 384;
    private static final Map<String, Tex> TEXTURES = new LinkedHashMap<>();
    private static int serial;

    private HudPaint() {
    }

    private static Tex texture(String key, int width, int height, java.util.function.Consumer<int[][]> paint) {
        Tex known = TEXTURES.get(key);
        if (known != null) return known;
        if (TEXTURES.size() >= MAX_TEXTURES) clear();
        int[][] pixels = new int[height][width];
        paint.accept(pixels);
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setColorArgb(x, y, pixels[y][x]);
        Identifier id = Steveparty.id("party_hud/painted_" + serial++);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
        Tex tex = new Tex(id, width, height);
        TEXTURES.put(key, tex);
        return tex;
    }

    /** Forgets every painted picture (painted again when needed). */
    static void clear() {
        var manager = MinecraftClient.getInstance().getTextureManager();
        for (Tex tex : TEXTURES.values()) manager.destroyTexture(tex.id());
        TEXTURES.clear();
    }

    static void draw(DrawContext context, Tex tex, int x, int y, float alpha) {
        if (alpha <= 0.02f) return;
        context.drawTexture(RenderLayer::getGuiTextured, tex.id(), x, y, 0, 0, tex.width(), tex.height(), tex.width(), tex.height(),
                HudDraw.white(alpha));
    }

    // ------------------------------------------------------------------ shapes

    static final int SHADOW = 1, OUTLINE = 2, BAND = 4;

    /** A shape in the kit's look, in a picture with {@link HudShapes#PAD} pixels of margin. */
    static Tex shape(Form form, int w, int h, Ramp ramp, int flags) {
        String key = "s" + form + w + "x" + h + ramp + flags;
        return texture(key, w + 2 * HudShapes.PAD, h + 2 * HudShapes.PAD, out -> paint(out, HudShapes.padded(HudShapes.mask(form, w, h)), ramp, flags));
    }

    private static void paint(int[][] out, boolean[][] m, Ramp ramp, int flags) {
        boolean outline = (flags & OUTLINE) != 0;
        boolean[][] grown = HudShapes.dilate(m, 1);
        int h = m.length, w = m[0].length;
        if ((flags & SHADOW) != 0) {
            boolean[][] base = outline ? grown : m;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (base[y][x]) continue;
                    if (y >= 2 && base[y - 2][x] && out[y][x] == 0) out[y][x] = 0x32000000;
                    if (y >= 1 && base[y - 1][x]) out[y][x] = 0x69000000;
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (m[y][x]) out[y][x] = ramp.body();
                else if (outline && grown[y][x]) out[y][x] = ramp.outline();
            }
        }
        // The bevel: the highlight on the top and left edges, then the shadow on the bottom and right ones
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y][x]) continue;
                boolean top = y == 0 || !m[y - 1][x], left = x == 0 || !m[y][x - 1];
                if (top || left) out[y][x] = ramp.hi();
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y][x]) continue;
                boolean bottom = y == h - 1 || !m[y + 1][x], right = x == w - 1 || !m[y][x + 1];
                if (bottom || right) out[y][x] = ramp.shadow();
            }
        }
        if ((flags & BAND) != 0) {
            // A glossy band: the two top rows of each column, moved two rows down (not on the side edges)
            int light = mix(ramp.body(), 0xFFFFFFFF, 0.45f);
            for (int y = 2; y < h; y++) {
                for (int x = 1; x < w - 1; x++) {
                    if (!m[y][x] || isTop(m, x, y) || !isTop(m, x, y - 2)) continue;
                    if (!m[y][x - 1] || !m[y][x + 1]) continue;
                    out[y][x] = light;
                }
            }
        }
    }

    /** One of the two top rows of its column: in the shape, the pixel two rows above it is not. */
    private static boolean isTop(boolean[][] m, int x, int y) {
        return m[y][x] && (y < 2 || !m[y - 2][x]);
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
        int width = 0;
        for (String row : rows) width = Math.max(width, row.length());
        int w = width;
        return texture("p" + key, w, rows.length, out -> {
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    Integer c = colours.get(rows[y].charAt(x));
                    if (c != null) out[y][x] = c;
                }
            }
        });
    }

    /** The mini-game's icon, 10 x 8: the same as the dashboard's ({@link fr.lordfinn.steveparty.client.gui.ConsolePaint#GAMEPAD}). */
    static Tex gamepad() {
        return pattern("gamepad", fr.lordfinn.steveparty.client.gui.ConsolePaint.GAMEPAD, fr.lordfinn.steveparty.client.gui.ConsolePaint.GAMEPAD_COLOURS);
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
