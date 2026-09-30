package fr.lordfinn.steveparty.client.utils;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 *     Stop ({@link #stopFace}), Teleport ({@link #teleportFace}).</li>
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

    private static final Map<Key, Identifier> TEXTURES = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Identifier> eldest) {
            if (size() <= MAX_CACHED) return false;
            MinecraftClient.getInstance().getTextureManager().destroyTexture(eldest.getValue());
            return true;
        }
    };
    /** Value maps (darkness per pixel, NaN transparent) of the face textures, and their own base colour. */
    private static final Map<Identifier, Template> TEMPLATES = new HashMap<>();

    private record Template(float[] big, float[] small, float[] bigFrame, float[] smallFrame, int baseColor) {
    }

    private static final Identifier NEUTRAL = Steveparty.id("block/tile_overlay_neutral");

    private TileStampTextures() {
    }

    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id("tile_face_textures");
            }

            @Override
            public void reload(ResourceManager manager) {
                TEXTURES.values().forEach(id -> MinecraftClient.getInstance().getTextureManager().destroyTexture(id));
                TEXTURES.clear();
                TEMPLATES.clear();
            }
        });
    }

    // ---------------------------------------------------------------- stamps

    public static Identifier get(TileStampComponent stamp) {
        return get(stamp, false);
    }

    public static Identifier get(TileStampComponent stamp, boolean small) {
        byte[] shape = stamp.shapeArray();
        int rgb = stamp.color().getEntityColor();
        return TEXTURES.computeIfAbsent(new Key(ByteBuffer.wrap(shape), rgb, small), key -> register(stampValues(shape, small), rgb, small));
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
        return TEXTURES.computeIfAbsent(new Key("advance_back:" + steps, rgb, small), key -> register(advanceBackValues(steps, small), rgb, small));
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
        return TEXTURES.computeIfAbsent(new Key(texture, rgb, small), key -> register(small ? template.small : template.big, rgb, small));
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
        return TEXTURES.computeIfAbsent(new Key("stop", rgb, small), key -> register(stopValues(rgb, small), rgb, small));
    }

    private static float[] stopValues(int rgb, boolean small) {
        int side = small ? SMALL_SIDE : SIDE;
        float[] values = frame(small).clone();
        boolean dark = TileColors.isDark(rgb);
        float ink = dark ? LIGHT_INK : FEATURE, counter = dark ? FEATURE : NUMBER;
        int size = small ? 10 : 14, barWidth = small ? 6 : 8;
        int left = (side - size) / 2, top = (side - size) / 2;
        double centre = (size - 1) / 2.0, radius = size / 2.0 - 0.15;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (Math.hypot(x - centre, y - centre) > radius) continue;
                boolean bar = y >= size / 2 - 1 && y <= size / 2 && x >= (size - barWidth) / 2 && x < (size + barWidth) / 2;
                values[(top + y) * side + left + x] = bar ? counter : ink;
            }
        }
        return values;
    }

    // ---------------------------------------------------------------- the Teleport face

    /** The cyan heart of the warp swirl (and of the teleport sparkles). */
    private static final int TELEPORT_CYAN = 0x5FE6FF;

    /**
     * The Teleport tile's face: a warp swirl (two light arms turning into a cyan and white heart, a dark rim) on the blank
     * tile face (its rounded bevel), all in the ramp of {@code rgb} (purple by default, a dye changes it); four little
     * sparkles in the corners of the 32x32 face.
     */
    public static Identifier teleportFace(int rgb, boolean small) {
        return TEXTURES.computeIfAbsent(new Key("teleport", rgb, small), key -> {
            int side = small ? SMALL_SIDE : SIDE;
            float[] frame = frame(small);
            int[] argb = new int[side * side];
            Map<Float, Integer> shades = new HashMap<>();
            for (int i = 0; i < argb.length; i++) {
                if (!Float.isNaN(frame[i])) argb[i] = 0xFF000000 | shades.computeIfAbsent(frame[i], v -> TileColors.shade(rgb, v));
            }
            double centre = (side - 1) / 2.0, radius = small ? 6.6 : 11.0;
            double coreWhite = small ? 1.0 : 1.6, coreCyan = small ? 1.9 : 2.9;
            int rim = 0xFF000000 | TileColors.shade(rgb, FEATURE), dark = 0xFF000000 | TileColors.shade(rgb, 0.42f);
            int light = 0xFF000000 | TileColors.shade(rgb, -0.5f), cyan = 0xFF000000 | TileColors.shade(TELEPORT_CYAN, -0.3f);
            int paleCyan = 0xFF000000 | TileColors.shade(TELEPORT_CYAN, -0.4f);
            for (int x = 0; x < side; x++) {
                for (int y = 0; y < side; y++) {
                    double dx = x - centre, dy = y - centre, d = Math.sqrt(dx * dx + dy * dy);
                    if (d > radius) continue;
                    int colour;
                    if (d > radius - 1.2) colour = rim;
                    else if (d < coreWhite) colour = 0xFFFFFFFF;
                    else if (d < coreCyan) colour = paleCyan;
                    else {
                        // Two arms winding in: the spiral coordinate grows with the angle and the distance
                        double u = Math.atan2(dy, dx) / (2 * Math.PI) * 2 + d / radius * 1.25;
                        boolean arm = u - Math.floor(u) < 0.45;
                        colour = !arm ? dark : d < radius * 0.42 ? cyan : light;
                    }
                    argb[y * side + x] = colour;
                }
            }
            if (!small) {
                for (int[] star : new int[][]{{6, 6}, {25, 7}, {6, 25}, {25, 24}}) {
                    argb[star[1] * side + star[0]] = 0xFFFFFFFF;
                    for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        argb[(star[1] + o[1]) * side + star[0] + o[0]] = paleCyan;
                    }
                }
            }
            return register(argb, side);
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
        for (int x = 9; x < 23; x++) for (int y = 9; y < 23; y++) counts.merge(image.getColorArgb(x, y), 1, Integer::sum);
        int base = counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0xFFFFFFFF);
        float baseLum = luminance(base);
        // Levels of the darker colours (close luminances are one level)
        List<Float> levels = new ArrayList<>();
        for (int x = 0; x < SIDE; x++) {
            for (int y = 0; y < SIDE; y++) {
                int argb = image.getColorArgb(x, y);
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
                int argb = image.getColorArgb(x, y);
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
                    image.setColorArgb(x, y, 0);
                    continue;
                }
                int color = shades.computeIfAbsent(value, v -> TileColors.shade(rgb, v));
                image.setColorArgb(x, y, 0xFF000000 | color);
            }
        }
        return MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("tile_face", new NativeImageBackedTexture(image));
    }

    /** A face already in its colours (ARGB, row by row). */
    private static Identifier register(int[] argb, int side) {
        NativeImage image = new NativeImage(side, side, true);
        for (int x = 0; x < side; x++) for (int y = 0; y < side; y++) image.setColorArgb(x, y, argb[y * side + x]);
        return MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("tile_face", new NativeImageBackedTexture(image));
    }
}
