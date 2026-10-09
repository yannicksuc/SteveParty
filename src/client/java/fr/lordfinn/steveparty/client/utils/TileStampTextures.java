package fr.lordfinn.steveparty.client.utils;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The faces drawn on tiles, coloured with {@link TileColors} ramps (vivid shades, not a grey texture multiplied):
 * <ul>
 *     <li>the tile's own faces (neutral, angry, excited, blow...), read from their 32x32 textures as a map of values
 *     (the base, rings darkening toward the edge, the darker features) and painted with the ramp of any colour;</li>
 *     <li>stamped looks ({@link TileStampComponent}): the dye's colour, the pattern in its darkest shade;</li>
 *     <li>the role faces drawn over the blank face (its rounded bevel): Move Forward / Back ({@link #advanceBack}),
 *     Stop ({@link #stopFace}), Replay ({@link #replayFace}), Shop ({@link #shopFace}): hand-drawn pixel grids, one per
 *     size;</li>
 *     <li>the Teleport face ({@link #teleportFace}): a portal over the whole flat of the blank face, a few images of it
 *     shown in turn (its rings drifting);</li>
 * </ul>
 * Each exists at two pixel densities: 32x32 over the 2 blocks of a standard (or large) tile (28x28 drawn, 2 px margin),
 * and 16x16 over the single block of a small tile, at the block's own pixel density. Created on first use, LRU
 * bounded, cleared on resource reload; render thread only.
 */
public final class TileStampTextures {
    public static final int SIDE = 32;
    public static final int SMALL_SIDE = 16;
    /** Transparent margin of the 32x32 faces. */
    public static final int MARGIN = 2;

    /** Darkness of the rings, from the edge inward (soft: the tile reads flat, not domed). */
    private static final float[] RINGS = {0.5f, 0.36f, 0.24f, 0.24f, 0.12f, 0.12f};
    private static final float[] SMALL_RINGS = {0.5f, 0.3f, 0.14f};
    /** Features of a face, and stamped patterns: the darkest shade. */
    private static final float FEATURE = 0.72f;
    private static final float HIGHLIGHT = -0.35f;
    private static final float TRANSPARENT = Float.NaN;
    private static final int MAX_CACHED = 192;

    private record Key(Object source, int rgb, boolean small) {
    }

    private static final DynamicTextureCache<Key> TEXTURES = new DynamicTextureCache<>(MAX_CACHED);
    /** Value maps (darkness per pixel, NaN transparent) of the face textures, and their own base colour. */
    private static final Map<Identifier, Template> TEMPLATES = new HashMap<>();

    private record Template(float[] big, float[] small, float[] bigFrame, float[] smallFrame, int baseColor) {
    }

    private static final Identifier NEUTRAL = Steveparty.id("block/tile_overlay_neutral");

    private TileStampTextures() {
    }

    public static void registerReloadListener() {
        DynamicTextureCache.onResourceReload("tile_face_textures", () -> {
            TEXTURES.clear();
            TEMPLATES.clear();
        });
    }

    // ---------------------------------------------------------------- stamps

    public static Identifier get(TileStampComponent stamp) {
        return get(stamp, false);
    }

    public static Identifier get(TileStampComponent stamp, boolean small) {
        byte[] shape = stamp.shapeArray();
        int rgb = stamp.color().getEntityColor();
        return TEXTURES.get(new Key(ByteBuffer.wrap(shape), rgb, small), key -> register(stampValues(shape, small), rgb, small));
    }

    private static float[] stampValues(byte[] shape, boolean small) {
        int side = small ? SMALL_SIDE : SIDE;
        float[] values = frame(small).clone();
        // 32x32: the pattern in the middle, over the base; 16x16: the pattern over the whole face (frame where empty)
        int offset = small ? 0 : 8;
        for (int x = 0; x < StencilShape.SIDE; x++) {
            for (int y = 0; y < StencilShape.SIDE; y++) {
                if (shape[StencilShape.index(x, y)] != 0) values[(y + offset) * side + x + offset] = FEATURE;
            }
        }
        return values;
    }

    /** The blank tile face: the neutral face's rounded bevel without its features (square rings if it is missing). */
    private static float[] frame(boolean small) {
        Template neutral = template(NEUTRAL);
        if (neutral != null) return small ? neutral.smallFrame : neutral.bigFrame;
        return squareFrame(small);
    }

    private static float[] squareFrame(boolean small) {
        int side = small ? SMALL_SIDE : SIDE;
        int margin = small ? 0 : MARGIN;
        float[] rings = small ? SMALL_RINGS : RINGS;
        float[] values = new float[side * side];
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                if (x < margin || y < margin || x >= side - margin || y >= side - margin) {
                    values[y * side + x] = TRANSPARENT;
                    continue;
                }
                int ring = Math.min(Math.min(x - margin, side - 1 - margin - x), Math.min(y - margin, side - 1 - margin - y));
                values[y * side + x] = ring < rings.length ? rings[ring] : 0;
            }
        }
        return values;
    }

    // ---------------------------------------------------------------- Move Forward / Back faces

    /** Double arrows (like ⏩) pointing right, then 5x7 digits (32x32 faces) and 3x5 digits (16x16 faces). */
    private static final String[] ARROW = {
            "#...#...",
            "##..##..",
            "###.###.",
            "########",
            "###.###.",
            "##..##..",
            "#...#..."};
    private static final String[] SMALL_ARROW = {
            "#..#..",
            "##.##.",
            "######",
            "##.##.",
            "#..#.."};
    private static final String[][] DIGITS = {
            {".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."},
            {"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."},
            {".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"},
            {"####.", "....#", "....#", ".###.", "....#", "....#", "####."},
            {"...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."},
            {"#####", "#....", "####.", "....#", "....#", "#...#", ".###."},
            {".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."}};
    private static final String[][] SMALL_DIGITS = {
            {"###", "#.#", "#.#", "#.#", "###"},
            {".#.", "##.", ".#.", ".#.", "###"},
            {"##.", "..#", ".#.", "#..", "###"},
            {"##.", "..#", ".#.", "..#", "##."},
            {"#.#", "#.#", "###", "..#", "..#"},
            {"###", "#..", "##.", "..#", "##."},
            {".##", "#..", "###", "#.#", "###"}};
    /** The number: almost white, over the colour. */
    private static final float NUMBER = -0.95f;

    /**
     * The face of a Move Forward / Back tile: the blank rounded bevel in green (forward) or pink-magenta (back), a double
     * arrow in the darkest shade and the number of spaces almost white ("3 ⏩", "⏪ 2").
     */
    public static Identifier advanceBack(int steps, boolean small) {
        int rgb = fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem.color(steps);
        return TEXTURES.get(new Key("advance_back:" + steps, rgb, small), key -> register(advanceBackValues(steps, small), rgb, small));
    }

    private static float[] advanceBackValues(int steps, boolean small) {
        int side = small ? SMALL_SIDE : SIDE;
        float[] values = frame(small).clone();
        String[] arrow = small ? SMALL_ARROW : ARROW;
        int digit = Math.min(9, Math.abs(steps));
        String[] number = (small ? SMALL_DIGITS : DIGITS)[Math.min(digit, (small ? SMALL_DIGITS : DIGITS).length - 1)];
        boolean back = steps < 0;
        int gap = small ? 1 : 2;
        int width = arrow[0].length() + gap + number[0].length();
        int height = Math.max(arrow.length, number.length);
        // Centred on the flat middle of the face (the base, inside the bevel)
        int minX = side, minY = side, maxX = -1, maxY = -1;
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                if (values[y * side + x] != 0) continue;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < 0) {
            minX = minY = 0;
            maxX = maxY = side - 1;
        }
        int left = (minX + maxX + 1 - width) / 2, top = (minY + maxY + 1 - height) / 2;
        // Forward: the number, then the arrow pointing on; back: the arrow pointing back, then the number
        int numberX = back ? left + arrow[0].length() + gap : left;
        int arrowX = back ? left : left + number[0].length() + gap;
        paint(values, side, number, numberX, top + (height - number.length) / 2, false, NUMBER);
        paint(values, side, arrow, arrowX, top + (height - arrow.length) / 2, back, FEATURE);
        return values;
    }

    private static void paint(float[] values, int side, String[] glyph, int left, int top, boolean mirrored, float value) {
        for (int row = 0; row < glyph.length; row++) {
            String line = glyph[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(mirrored ? line.length() - 1 - col : col) != '#') continue;
                int x = left + col, y = top + row;
                if (x >= 0 && y >= 0 && x < side && y < side) values[y * side + x] = value;
            }
        }
    }

    // ---------------------------------------------------------------- faces

    /** The face {@code texture} (a 32x32 tile face) painted with the ramp of {@code rgb}. */
    public static @Nullable Identifier face(Identifier texture, int rgb, boolean small) {
        Template template = template(texture);
        if (template == null) return null;
        return TEXTURES.get(new Key(texture, rgb, small), key -> register(small ? template.small : template.big, rgb, small));
    }

    /** The face {@code texture} in its own colours (the angry, excited... faces). */
    public static @Nullable Identifier face(Identifier texture, boolean small) {
        Template template = template(texture);
        return template == null ? null : face(texture, template.baseColor, small);
    }

    // ---------------------------------------------------------------- the Stop face

    /** Light ink of a pictogram on a dark colour (the Stop anthracite): its lightest shade but one. */
    private static final float LIGHT_INK = -0.8f;

    /**
     * The Stop tile's face: a "no entry" disc barred across (the disc in the ramp's darkest shade, the bar almost white;
     * on a dark colour such as the default anthracite, a light disc and a dark bar), 14 px wide on the blank tile face
     * (its rounded bevel) of a standard tile, 10 px on a small one, all in the ramp of {@code rgb}.
     */
    public static Identifier stopFace(int rgb, boolean small) {
        return TEXTURES.get(new Key("stop", rgb, small), key -> register(stopValues(rgb, small), rgb, small));
    }

    /** The barred disc, drawn by hand for each size (smooth 4-2-1-2-4 and 4-2-2-4 stair steps): '#' disc, 'o' bar. */
    private static final String[] STOP_DISC = {
            ".....####.....",
            "...########...",
            "..##########..",
            ".############.",
            ".############.",
            "##############",
            "###oooooooo###",
            "###oooooooo###",
            "##############",
            ".############.",
            ".############.",
            "..##########..",
            "...########...",
            ".....####....."};
    private static final String[] SMALL_STOP_DISC = {
            "...####...",
            ".########.",
            ".########.",
            "##########",
            "##oooooo##",
            "##oooooo##",
            "##########",
            ".########.",
            ".########.",
            "...####..."};

    private static float[] stopValues(int rgb, boolean small) {
        boolean dark = TileColors.isDark(rgb);
        return glyphValues(small ? SMALL_STOP_DISC : STOP_DISC, small, dark ? LIGHT_INK : FEATURE, dark ? FEATURE : NUMBER);
    }

    /**
     * The blank face with {@code glyph} centred on it: '#' painted {@code ink}, 'o' painted {@code counter}. Glyphs
     * are hand-drawn pixel grids, one per size (never scaled).
     */
    private static float[] glyphValues(String[] glyph, boolean small, float ink, float counter) {
        return glyphValues(glyph, small, Map.of('#', ink, 'o', counter));
    }

    /** The blank face with {@code glyph} centred on it, each character painted with its shade in {@code shades}. */
    private static float[] glyphValues(String[] glyph, boolean small, Map<Character, Float> shades) {
        int side = small ? SMALL_SIDE : SIDE;
        float[] values = frame(small).clone();
        int left = (side - glyph[0].length()) / 2, top = (side - glyph.length) / 2;
        for (int row = 0; row < glyph.length; row++) {
            for (int col = 0; col < glyph[row].length(); col++) {
                Float shade = shades.get(glyph[row].charAt(col));
                if (shade != null) values[(top + row) * side + left + col] = shade;
            }
        }
        return values;
    }

    // ---------------------------------------------------------------- the Replay face

    /** A clockwise circular arrow (2 px stroke, a 270 degree arc from 3 o'clock round to its head at 12), by hand. */
    private static final String[] REPLAY_ARROW = {
            ".......#......",
            ".......##.....",
            ".....#####....",
            "...#######....",
            "..###..##.....",
            ".##....#......",
            ".##...........",
            "##............",
            "##............",
            "##..........##",
            "##..........##",
            ".##........##.",
            ".##........##.",
            "..###....###..",
            "...########...",
            ".....####....."};
    private static final String[] SMALL_REPLAY_ARROW = {
            "....#...",
            "....##..",
            "..#####.",
            ".######.",
            "###.##..",
            "##..#...",
            "##....##",
            "###..###",
            ".######.",
            "..####.."};

    /**
     * The Replay tile's face: a circular arrow (roll again) in the ramp's darkest shade (light on a dark dyed colour)
     * on the blank tile face (its rounded bevel), in the ramp of {@code rgb} (cyan by default).
     */
    public static Identifier replayFace(int rgb, boolean small) {
        return TEXTURES.get(new Key("replay", rgb, small), key -> {
            boolean dark = TileColors.isDark(rgb);
            return register(glyphValues(small ? SMALL_REPLAY_ARROW : REPLAY_ARROW, small, dark ? LIGHT_INK : FEATURE, FEATURE), rgb, small);
        });
    }

    // ---------------------------------------------------------------- the Shop face

    /**
     * The Boxed Trader in his open cardboard box: his head (bandana, unibrow, eyes, nose, all above the rim), the box
     * (a tape seam down its front) and its two side flaps open at 45 degrees; a simpler open box on a small tile.
     * '#' darkest shade, 'b' bandana, 's' skin, 'o' eye white, 'n' nose, '+' cardboard,
     * 't' tape.
     */
    private static final String[] SHOP_BOX = {
            "..................",
            "......######......",
            "......#bbbb#......",
            "#.....######.....#",
            "+#....#o##o#....#+",
            ".-#...#snns#...#-.",
            "..-#..#snns#..#-..",
            "...############...",
            "...#+mmmmmmmm+#...",
            "...#+mmmmmmmm+#...",
            "...#+mmmmmmmm+#...",
            "...#+mmmmmmmm+#...",
            "...#+mmmmmmmm+#...",
            "...#+mmmmmmmm+#...",
            "...#+mmmmmmmm+#...",
            "...############...",
            "...+++##++##+++...",
            "......##..##......"};
    private static final String[] SMALL_SHOP_BOX = {
            "............",
            "....####....",
            "#...#++#...#",
            ".#..#tt#..#.",
            "..#.#tt#.#..",
            "..########..",
            "..#+mmmm+#..",
            "..#+mmmm+#..",
            "..#+mmmm+#..",
            "..#+mmmm+#..",
            "..########..",
            "...##..##..."};
    private static final Map<Character, Float> SHOP_SHADES = Map.of(
            '#', FEATURE, 'b', 0.2f, 's', -0.5f, 'o', NUMBER, 'n', 0.5f, '+', 0.3f, 't', -0.3f, '-', 0.08f, 'm', 0.12f);

    /** The shop tile's face, in the ramp of {@code rgb} (the Shop Cartridge's lime green). */
    public static Identifier shopFace(int rgb, boolean small) {
        return TEXTURES.get(new Key("shop", rgb, small),
                key -> register(glyphValues(small ? SMALL_SHOP_BOX : SHOP_BOX, small, SHOP_SHADES), rgb, small));
    }

    // ---------------------------------------------------------------- the Star face

    /** A five-pointed star, by hand for each size: '#' the ramp's darkest shade, 'o' a light glint on its top point. */
    private static final String[] STAR = {
            "......#o......",
            "......##......",
            ".....####.....",
            ".....####.....",
            "##############",
            ".############.",
            "..##########..",
            "...########...",
            "...########...",
            "..####..####..",
            "..###....###..",
            ".###......###.",
            ".##........##."};
    private static final String[] SMALL_STAR = {
            "....#o....",
            "....##....",
            "...####...",
            "##########",
            ".########.",
            "..######..",
            "..######..",
            ".###..###.",
            ".##....##."};

    /** The star space's face: a star in the ramp's darkest shade on the blank tile face, in the ramp of {@code rgb} (yellow). */
    public static Identifier starFace(int rgb, boolean small) {
        return TEXTURES.get(new Key("star", rgb, small),
                key -> register(glyphValues(small ? SMALL_STAR : STAR, small, FEATURE, -0.6f), rgb, small));
    }

    // ---------------------------------------------------------------- the Glandouille face

    /**
     * An acorn, by hand for each size: '#' its cap and its two-pixel stem (the ramp's darkest shade), 'c' the light on
     * the cap's top left, 'o' its nut (a middle shade), 'h' the glint on the nut's top left, 'd' its shaded bottom right.
     */
    private static final String[] ACORN = {
            "..............",
            "......##......",
            "..#cc#######..",
            ".#c##########.",
            "##############",
            ".############.",
            "..oooooooooo..",
            "..ohhooooooo..",
            "..ohoooooood..",
            "...oooooood...",
            "...oooooodd...",
            "....oooodd....",
            ".....oood.....",
            "......od......"};
    private static final String[] SMALL_ACORN = {
            "....##....",
            "..#c####..",
            ".#c######.",
            "##########",
            ".ohoooooo.",
            ".oooooood.",
            "..oooood..",
            "..oooodd..",
            "...oood...",
            "....od...."};
    private static final Map<Character, Float> ACORN_SHADES =
            Map.of('#', FEATURE, 'c', 0.55f, 'o', 0.3f, 'h', 0.06f, 'd', 0.45f);

    /** The Glandouille space's face: an acorn on the blank tile face, in the ramp of {@code rgb} (brown). */
    public static Identifier glandouilleFace(int rgb, boolean small) {
        return TEXTURES.get(new Key("glandouille", rgb, small),
                key -> register(glyphValues(small ? SMALL_ACORN : ACORN, small, ACORN_SHADES), rgb, small));
    }

    /** The Frousseux: a little ghost with its wick and flame on its head, two eyes, a ragged sheet hem. */
    private static final String[] FROUSSEUX = {
            "......ff......",
            ".....ffff.....",
            ".....ffff.....",
            "......ww......",
            "..oooooooooo..",
            "..oooooooooo..",
            "..oo#oooo#oo..",
            "..oo#oooo#oo..",
            "..oooooooooo..",
            "..oooooooooo..",
            "..oooooooooo..",
            "..oooooooooo..",
            "..oo.ooo.ooo..",
            "..o...o...o..."};
    private static final String[] SMALL_FROUSSEUX = {
            "....ff....",
            "...ffff...",
            "....ww....",
            ".oooooooo.",
            ".o#oooo#o.",
            ".o#oooo#o.",
            ".oooooooo.",
            ".oooooooo.",
            ".oo.oo.oo.",
            ".o...o..o."};
    private static final Map<Character, Float> FROUSSEUX_SHADES =
            Map.of('#', FEATURE, 'w', FEATURE, 'o', -0.55f, 'f', -0.9f);

    /** The Frousseux space's face: the little candle ghost on the blank tile face, in the ramp of {@code rgb} (night indigo). */
    public static Identifier frousseuxFace(int rgb, boolean small) {
        return TEXTURES.get(new Key("frousseux", rgb, small),
                key -> register(glyphValues(small ? SMALL_FROUSSEUX : FROUSSEUX, small, FROUSSEUX_SHADES), rgb, small));
    }

    /** The Threshold obstacle: a striped hurdle, two posts and two bars. */
    private static final String[] THRESHOLD = {
            "..............",
            "..##......##..",
            "..##......##..",
            "oo--oo--oo--oo",
            "oo--oo--oo--oo",
            "..##......##..",
            "..##......##..",
            "oo--oo--oo--oo",
            "oo--oo--oo--oo",
            "..##......##..",
            "..##......##..",
            "..##......##..",
            ".####....####.",
            ".............."};
    private static final String[] SMALL_THRESHOLD = {
            "..........",
            ".##....##.",
            "oo--oo--oo",
            "oo--oo--oo",
            ".##....##.",
            "oo--oo--oo",
            "oo--oo--oo",
            ".##....##.",
            "####..####",
            ".........."};
    private static final Map<Character, Float> THRESHOLD_SHADES = Map.of('#', FEATURE, 'o', -0.7f, '-', 0.45f);

    /** The Threshold obstacle's face: a striped hurdle on the blank tile face, in the ramp of {@code rgb} (steel blue). */
    public static Identifier thresholdFace(int rgb, boolean small) {
        return TEXTURES.computeIfAbsent(new Key("threshold", rgb, small),
                key -> register(glyphValues(small ? SMALL_THRESHOLD : THRESHOLD, small, THRESHOLD_SHADES), rgb, small));
    }

    /** The Common pot: a woven nest full of coins. */
    private static final String[] POT = {
            "..............",
            ".....oooo.....",
            "...oooooooo...",
            "..oo-oo-oo-o..",
            "..oooooooooo..",
            ".############.",
            "#-#-#-#-#-#-##",
            "##-#-#-#-#-#-#",
            "#-#-#-#-#-#-##",
            ".############.",
            "..##########..",
            "....######....",
            "..............",
            ".............."};
    private static final String[] SMALL_POT = {
            "..........",
            "...oooo...",
            ".oooooooo.",
            ".o-o-o-oo.",
            "##########",
            "#-#-#-#-##",
            "##-#-#-#-#",
            ".########.",
            "..######..",
            ".........."};
    private static final Map<Character, Float> POT_SHADES = Map.of('#', FEATURE, 'o', -0.8f, '-', 0.25f);

    /** The Common pot's face: a nest full of coins on the blank tile face, in the ramp of {@code rgb} (straw). */
    public static Identifier potFace(int rgb, boolean small) {
        return TEXTURES.computeIfAbsent(new Key("pot", rgb, small),
                key -> register(glyphValues(small ? SMALL_POT : POT, small, POT_SHADES), rgb, small));
    }

    /** The Key gate: a gate's frame, its veil, a keyhole in the middle. */
    private static final String[] KEY_GATE = {
            "..............",
            ".############.",
            ".#oooooooooo#.",
            ".#oooooooooo#.",
            ".#oooo##oooo#.",
            ".#ooo####ooo#.",
            ".#ooo####ooo#.",
            ".#oooo##oooo#.",
            ".#oooo##oooo#.",
            ".#ooo####ooo#.",
            ".#oooooooooo#.",
            ".#oooooooooo#.",
            ".############.",
            ".............."};
    private static final String[] SMALL_KEY_GATE = {
            "..........",
            ".########.",
            ".#oooooo#.",
            ".#oo##oo#.",
            ".#o####o#.",
            ".#oo##oo#.",
            ".#o####o#.",
            ".#oooooo#.",
            ".########.",
            ".........."};
    private static final Map<Character, Float> KEY_GATE_SHADES = Map.of('#', FEATURE, 'o', -0.6f);

    /** The Key gate's face: a gate with a keyhole on the blank tile face, in the ramp of {@code rgb} (teal). */
    public static Identifier keyGateFace(int rgb, boolean small) {
        return TEXTURES.computeIfAbsent(new Key("key_gate", rgb, small),
                key -> register(glyphValues(small ? SMALL_KEY_GATE : KEY_GATE, small, KEY_GATE_SHADES), rgb, small));
    }

    /** The Trap: open jaws seen from above, their teeth inward, a trigger plate in the middle. */
    private static final String[] TRAP = {
            "..............",
            ".############.",
            ".#.#.#.#.#.##.",
            ".#..........#.",
            ".#..........#.",
            ".#...oooo...#.",
            ".#...oooo...#.",
            ".#...oooo...#.",
            ".#...oooo...#.",
            ".#..........#.",
            ".#..........#.",
            ".##.#.#.#.#.#.",
            ".############.",
            ".............."};
    private static final String[] SMALL_TRAP = {
            "..........",
            ".########.",
            ".#.#.#.##.",
            ".#......#.",
            ".#..oo..#.",
            ".#..oo..#.",
            ".#......#.",
            ".##.#.#.#.",
            ".########.",
            ".........."};
    private static final Map<Character, Float> TRAP_SHADES = Map.of('#', FEATURE, 'o', -0.7f);

    /** The Trap's face: open jaws on the blank tile face, in the ramp of {@code rgb} (moss green). */
    public static Identifier trapFace(int rgb, boolean small) {
        return TEXTURES.computeIfAbsent(new Key("trap", rgb, small),
                key -> register(glyphValues(small ? SMALL_TRAP : TRAP, small, TRAP_SHADES), rgb, small));
    }

    /** The Mistigri: a cat's head, pointed ears (one notched), one slit eye open, the other shut, a nose, whiskers. */
    private static final String[] MISTIGRI = {
            "..o.......o.o.",
            "..oo.....oooo.",
            "..ooo...ooooo.",
            "..oooooooooo..",
            ".oooooooooooo.",
            ".ooo#oooooooo.",
            ".ooo#ooo---oo.",
            "woooooooooooow",
            ".oooooonooooo.",
            "woooooooooooow",
            "..oooooooooo..",
            "...oooooooo...",
            "....oooooo....",
            ".............."};
    private static final String[] SMALL_MISTIGRI = {
            ".o......o.",
            ".oo....oo.",
            ".oooooooo.",
            "oo#ooo--oo",
            "oo#ooooooo",
            "woooonooow",
            ".oooooooo.",
            "w.oooooo.w",
            "..oooooo..",
            "...oooo..."};
    private static final Map<Character, Float> MISTIGRI_SHADES =
            Map.of('#', FEATURE, '-', 0.3f, 'n', 0.1f, 'o', -0.7f, 'w', 0.2f);

    /** The Mistigri space's face: the black cat's head on the blank tile face, in the ramp of {@code rgb} (witch plum). */
    public static Identifier mistigriFace(int rgb, boolean small) {
        return TEXTURES.get(new Key("mistigri", rgb, small),
                key -> register(glyphValues(small ? SMALL_MISTIGRI : MISTIGRI, small, MISTIGRI_SHADES), rgb, small));
    }

    // ---------------------------------------------------------------- the Teleport face

    /** Images of the portal's drift (its rings one pixel further in at each) and how long each one shows. */
    public static final int PORTAL_FRAMES = 6;
    private static final long PORTAL_FRAME_MS = 450;
    /**
     * The portal's rings, from a corner of the flat of the face toward its middle: two pixels of deep colour, a soft
     * edge, two light pixels and a soft edge again (concentric diamonds: straight 45 degree steps at every size).
     */
    private static final float[] PORTAL_RINGS = {0.42f, 0.42f, 0.14f, -0.42f, -0.42f, 0.14f};
    /** The sparkle in the middle of the portal: almost white. */
    private static final float PORTAL_SPARKLE = -0.9f;

    /**
     * The Teleport tile's face: a Nether-portal-like window over the whole flat of the blank tile face (inside its
     * rounded bevel), deep rings and light ones in concentric diamonds around a sparkle, all in the ramp of
     * {@code rgb} (violet by default: the colour of its network). The rings drift slowly toward the middle: a few
     * images, each drawn once and kept, the one to show picked by the time.
     */
    public static Identifier teleportFace(int rgb, boolean small) {
        return teleportFace(rgb, small, (int) (Util.getMeasuringTimeMs() / PORTAL_FRAME_MS % PORTAL_FRAMES));
    }

    /** The image {@code frame} (0 to {@link #PORTAL_FRAMES} - 1) of the Teleport tile's face. */
    public static Identifier teleportFace(int rgb, boolean small, int frame) {
        int image = Math.floorMod(frame, PORTAL_FRAMES);
        return TEXTURES.get(new Key("teleport:" + image, rgb, small), key -> register(portalValues(frame(small), small, image), rgb, small));
    }

    /**
     * The blank face {@code blank} with the portal over its flat (the pixels of its base, inside the bevel): each
     * pixel takes the ring of its distance to the nearest corner of the flat, counted along both axes (diamonds),
     * {@code frame} pixels further out; the middle is the sparkle. On a standard face a light ring around a deep
     * heart, and another in the corners; on a small one a single light ring.
     */
    private static float[] portalValues(float[] blank, boolean small, int frame) {
        int side = small ? SMALL_SIDE : SIDE;
        float[] values = blank.clone();
        int min = side, max = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != 0) continue;
            min = Math.min(min, Math.min(i % side, i / side));
            max = Math.max(max, Math.max(i % side, i / side));
        }
        if (max < 0) return values;
        int phase = small ? 4 : 0;
        int middle = max - min - 1;
        for (int x = min; x <= max; x++) {
            for (int y = min; y <= max; y++) {
                if (values[y * side + x] != 0) continue;
                int distance = Math.min(x - min, max - x) + Math.min(y - min, max - y);
                values[y * side + x] = distance >= middle ? PORTAL_SPARKLE
                        : PORTAL_RINGS[Math.floorMod(distance + phase - frame, PORTAL_RINGS.length)];
            }
        }
        return values;
    }

    // ---------------------------------------------------------------- the pipe pictogram (for a pipe cartridge to come)

    /** A travel pipe seen from the side (rim over its body), '#' in the darkest shade, 'o' its light highlight. */
    private static final String[] PIPE = {
            "##############",
            "#oo###########",
            "#oo###########",
            "##############",
            "..#oo#######..",
            "..#oo#######..",
            "..#oo#######..",
            "..#oo#######..",
            "..#oo#######..",
            "..#oo#######..",
            "..#oo#######..",
            "..#oo#######..",
            "..##########.."};
    private static final String[] SMALL_PIPE = {
            "##########",
            "#o########",
            "##########",
            ".#o######.",
            ".#o######.",
            ".#o######.",
            ".#o######.",
            ".#o######.",
            ".########."};
    /** The pipe's highlight: a light shade of the colour. */
    private static final float PIPE_HIGHLIGHT = -0.5f;

    /**
     * A travel pipe on the blank tile face (its rounded bevel), all in the ramp of {@code rgb}: the pipe in the darkest
     * shade with a light highlight down its rim and body. No tile shows it yet: the face of a pipe cartridge to come.
     */
    public static Identifier pipeFace(int rgb, boolean small) {
        return TEXTURES.get(new Key("pipe", rgb, small), key -> {
            boolean dark = TileColors.isDark(rgb);
            float[] values = glyphValues(small ? SMALL_PIPE : PIPE, small, dark ? LIGHT_INK : FEATURE, dark ? FEATURE : PIPE_HIGHLIGHT);
            return register(values, rgb, small);
        });
    }

    private static @Nullable Template template(Identifier texture) {
        Template template = TEMPLATES.get(texture);
        if (template != null) return template;
        Identifier file = texture.withPath(path -> "textures/" + path + ".png");
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return null;
        try (InputStream stream = resource.get().getInputStream(); NativeImage image = NativeImage.read(stream)) {
            if (image.getWidth() != SIDE || image.getHeight() != SIDE) return null;
            template = readTemplate(image);
        } catch (IOException e) {
            Steveparty.LOGGER.error("Can't read tile face {}", file, e);
            return null;
        }
        TEMPLATES.put(texture, template);
        return template;
    }

    private static float luminance(int argb) {
        return 0.299f * ((argb >> 16) & 0xFF) + 0.587f * ((argb >> 8) & 0xFF) + 0.114f * (argb & 0xFF);
    }

    /**
     * Reads a face as values: its most common colour in the middle is the base; darker colours are the rings, by rank
     * (softened), and the darker pixels in the middle are its features; brighter ones highlights.
     */
    private static Template readTemplate(NativeImage image) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int x = 9; x < 23; x++) for (int y = 9; y < 23; y++) counts.merge(ColorHelper.Abgr.toAbgr(image.getColor(x, y)), 1, Integer::sum);
        int base = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0xFFFFFFFF);
        float baseLum = luminance(base);
        // Levels of the darker colours (close luminances are one level)
        List<Float> levels = new ArrayList<>();
        for (int x = 0; x < SIDE; x++) {
            for (int y = 0; y < SIDE; y++) {
                int argb = ColorHelper.Abgr.toAbgr(image.getColor(x, y));
                if (((argb >>> 24) & 0xFF) < 128) continue;
                float lum = luminance(argb);
                if (lum >= baseLum - 3) continue;
                if (levels.stream().noneMatch(level -> Math.abs(level - lum) < 4)) levels.add(lum);
            }
        }
        levels.sort((a, b) -> Float.compare(b, a));
        float[] big = new float[SIDE * SIDE];
        for (int x = 0; x < SIDE; x++) {
            for (int y = 0; y < SIDE; y++) {
                int argb = ColorHelper.Abgr.toAbgr(image.getColor(x, y));
                float value;
                if (((argb >>> 24) & 0xFF) < 128) value = TRANSPARENT;
                else {
                    float lum = luminance(argb);
                    if (Math.abs(lum - baseLum) < 3) value = 0;
                    else if (lum > baseLum) value = HIGHLIGHT;
                    else if (x >= 9 && x < 23 && y >= 9 && y < 23) value = FEATURE;
                    else {
                        int rank = 0;
                        for (int i = 0; i < levels.size(); i++) if (Math.abs(levels.get(i) - lum) < 4) rank = i;
                        // By rank: the darkest level is the outer ring, the lightest the innermost
                        value = ringValue(rank, levels.size());
                    }
                }
                big[y * SIDE + x] = value;
            }
        }
        // Without the features: the blank face (its rounded bevel), for stamped looks
        float[] bigFrame = big.clone();
        for (int i = 0; i < bigFrame.length; i++) if (bigFrame[i] == FEATURE) bigFrame[i] = 0;
        // 16x16: the drawn 28x28 brought to the block's pixel density (every ring, the rounded corners, the features)
        return new Template(big, downsample(big), bigFrame, downsample(bigFrame), base & 0xFFFFFF);
    }

    private static float[] downsample(float[] big) {
        float[] small = new float[SMALL_SIDE * SMALL_SIDE];
        for (int i = 0; i < SMALL_SIDE; i++) {
            for (int j = 0; j < SMALL_SIDE; j++) {
                int x = (int) Math.floor(16 + (i + 0.5 - 8) * 1.75), y = (int) Math.floor(16 + (j + 0.5 - 8) * 1.75);
                small[j * SMALL_SIDE + i] = big[y * SIDE + x];
            }
        }
        return small;
    }

    /** Ring darkness by rank among the darker levels (0 = lightest, just around the base). */
    private static float ringValue(int rank, int count) {
        if (count <= 1) return RINGS[0];
        float t = rank / (float) (count - 1); // 0 lightest .. 1 darkest
        return 0.12f + t * (RINGS[0] - 0.12f);
    }

    private static Identifier register(float[] values, int rgb, boolean small) {
        int side = small ? SMALL_SIDE : SIDE;
        NativeImage image = new NativeImage(side, side, true);
        Map<Float, Integer> shades = new HashMap<>();
        for (int x = 0; x < side; x++) {
            for (int y = 0; y < side; y++) {
                float value = values[y * side + x];
                if (Float.isNaN(value)) {
                    image.setColor(x, y, 0);
                    continue;
                }
                int color = shades.computeIfAbsent(value, v -> TileColors.shade(rgb, v));
                image.setColor(x, y, ColorHelper.Abgr.toAbgr(0xFF000000 | color));
            }
        }
        return MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("tile_face", new NativeImageBackedTexture(image));
    }

    /** A face already in its colours (ARGB, row by row). */
    private static Identifier register(int[] argb, int side) {
        NativeImage image = new NativeImage(side, side, true);
        for (int x = 0; x < side; x++) for (int y = 0; y < side; y++) image.setColor(x, y, ColorHelper.Abgr.toAbgr(argb[y * side + x]));
        return MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("tile_face", new NativeImageBackedTexture(image));
    }
}
