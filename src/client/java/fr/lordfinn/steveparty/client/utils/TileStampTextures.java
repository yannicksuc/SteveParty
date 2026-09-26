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
 *     <li>stamped looks ({@link TileStampComponent}): the dye's colour, the pattern in its darkest shade.</li>
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

    private record Template(float[] big, float[] small, int baseColor) {
    }

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
        float[] values = frame(small);
        // 32x32: the pattern in the middle, over the base; 16x16: the pattern over the whole face (frame where empty)
        int offset = small ? 0 : 8;
        for (int x = 0; x < StencilShape.SIDE; x++) {
            for (int y = 0; y < StencilShape.SIDE; y++) {
                if (shape[StencilShape.index(x, y)] != 0) values[(y + offset) * side + x + offset] = FEATURE;
            }
        }
        return values;
    }

    /** A plain face: base colour, soft rings toward the edge. */
    private static float[] frame(boolean small) {
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
        // 16x16: soft rings drawn anew, the features of the big face brought to the block's pixel density
        float[] small = frame(true);
        for (int i = 2; i < SMALL_SIDE - 2; i++) {
            for (int j = 2; j < SMALL_SIDE - 2; j++) {
                int x = (int) Math.floor(16 + (i + 0.5 - 8) * 1.75), y = (int) Math.floor(16 + (j + 0.5 - 8) * 1.75);
                if (big[y * SIDE + x] == FEATURE) small[j * SMALL_SIDE + i] = FEATURE;
            }
        }
        return new Template(big, small, base & 0xFFFFFF);
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
}
