package fr.lordfinn.steveparty.client.flag;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Colours of a dyed flag, taken from the wool of its dye.
 * <p>
 * A flag is drawn with {@link #LEVELS} shading levels (the level masks {@code goal_pole_flag_level_N} and
 * {@code flag_level_N}), darkest first. For each dye, the colours of the matching wool texture are sorted by luminance
 * and weighted by how many pixels use them; the colours at {@link #QUANTILES} are the flag's ramp, so every pixel of a
 * dyed flag is a colour of that wool. The wool textures are read from the resources (resource packs included) on each
 * reload.
 * <p>
 * A mixed colour (several dyes, like leather armour) gets its own ramp: the ramp of the nearest dye, moved around the
 * exact colour with the same shifts (hue and saturation offsets, brightness ratios) as between that dye's colour and
 * its wool, so it keeps the wool's hand-painted look and two close mixes stay two different flags. For a dye's exact
 * colour this gives back the wool ramp itself. Ramps are cached (a small LRU): computed once per colour.
 */
public final class FlagPalettes {
    public static final int LEVELS = 4;
    private static final float[] QUANTILES = {0.10f, 0.36f, 0.64f, 0.92f};
    private static final int CACHE_SIZE = 128;
    /** Wool ramps of vanilla 1.21.3, used until the textures are read (or if one cannot be). */
    private static final int[][] FALLBACK = {
            {0xD6DBDC, 0xE3E6E7, 0xF2F4F4, 0xFEFEFE}, {0xE56604, 0xEE6F0E, 0xF67B18, 0xF9932B},
            {0xAE35A4, 0xB73EAD, 0xC349B8, 0xD660D1}, {0x2994CC, 0x34A9D5, 0x41BBDF, 0x4EC5E7},
            {0xF4B519, 0xF7C021, 0xFBCD2C, 0xFED93F}, {0x62AD18, 0x6AB418, 0x75BE18, 0x86CC26},
            {0xDF6F96, 0xEF83A4, 0xF498B4, 0xF4B2C9}, {0x383C3F, 0x3C4144, 0x41484B, 0x474F52},
            {0x818178, 0x898981, 0x93948D, 0x9D9D97}, {0x157B8A, 0x15848E, 0x158F94, 0x169B9C},
            {0x69219F, 0x7326A6, 0x7F2CAF, 0x9743CD}, {0x2E3092, 0x323598, 0x383BA1, 0x3E4DB2},
            {0x643E21, 0x6D4426, 0x784B2B, 0x835432}, {0x4C5F22, 0x51681D, 0x587218, 0x658619},
            {0x922220, 0x9B2421, 0xA62922, 0xB8342C}, {0x0A0C11, 0x101216, 0x18181C, 0x252529},
    };

    private static final int[][] WOOL = new int[DyeColor.values().length][];
    private static final Map<Integer, int[]> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, int[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    static {
        for (DyeColor dye : DyeColor.values()) WOOL[dye.ordinal()] = FALLBACK[dye.ordinal()];
    }

    private FlagPalettes() {}

    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id("flag_palettes");
            }

            @Override
            public void reload(ResourceManager manager) {
                for (DyeColor dye : DyeColor.values()) {
                    int[] ramp = readWool(manager, dye);
                    WOOL[dye.ordinal()] = ramp != null ? ramp : FALLBACK[dye.ordinal()];
                }
                synchronized (CACHE) {
                    CACHE.clear();
                }
            }
        });
    }

    /** @return the ramp of a flag colour (0xRRGGBB each, darkest first). The array is shared: do not modify it. */
    public static int[] ramp(int color) {
        DyeColor dye = FlagItem.matchingDye(color);
        if (dye != null) return WOOL[dye.ordinal()];
        synchronized (CACHE) {
            int[] cached = CACHE.get(color);
            if (cached == null) {
                cached = transfer(color);
                CACHE.put(color, cached);
            }
            return cached;
        }
    }

    // ------------------------------------------------------------------ ramps

    private static int[] readWool(ResourceManager manager, DyeColor dye) {
        Identifier id = Identifier.ofVanilla("textures/block/" + dye.getName() + "_wool.png");
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) return null;
        try (InputStream stream = resource.get().getInputStream(); NativeImage image = NativeImage.read(stream)) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getColorArgb(x, y);
                    if ((argb >>> 24) < 128) continue;
                    counts.merge(argb & 0xFFFFFF, 1, Integer::sum);
                }
            }
            if (counts.isEmpty()) return null;
            return rampOf(counts);
        } catch (Exception e) {
            Steveparty.LOGGER.warn("Could not read {} for the flag colours", id, e);
            return null;
        }
    }

    /** The colours at {@link #QUANTILES} of the luminance-sorted, pixel-weighted palette. */
    static int[] rampOf(Map<Integer, Integer> counts) {
        List<Integer> colors = new ArrayList<>(counts.keySet());
        colors.sort((a, b) -> Float.compare(luminance(a), luminance(b)));
        int total = 0;
        for (int count : counts.values()) total += count;
        int[] ramp = new int[LEVELS];
        for (int level = 0; level < LEVELS; level++) {
            int accumulated = 0;
            ramp[level] = colors.getLast();
            for (int color : colors) {
                accumulated += counts.get(color);
                if (accumulated >= QUANTILES[level] * total) {
                    ramp[level] = color;
                    break;
                }
            }
        }
        return ramp;
    }

    /** Ramp of an arbitrary colour: the nearest dye's wool ramp, moved around it (see the class comment). */
    static int[] transfer(int target) {
        DyeColor nearest = DyeColor.WHITE;
        long best = Long.MAX_VALUE;
        for (DyeColor dye : DyeColor.values()) {
            long distance = distanceSq(target, FlagItem.dyeColor(dye));
            if (distance < best) {
                best = distance;
                nearest = dye;
            }
        }
        int dyeColor = FlagItem.dyeColor(nearest);
        float[] t = hsv(target), d = hsv(dyeColor);
        int[] wool = WOOL[nearest.ordinal()];
        int[] ramp = new int[LEVELS];
        for (int level = 0; level < LEVELS; level++) {
            float[] c = hsv(wool[level]);
            float dh = c[0] - d[0];
            if (dh > 0.5f) dh -= 1f;
            if (dh < -0.5f) dh += 1f;
            // A hue shift only means something on a coloured dye (not on white, grey or black)
            float h = t[0] + dh * Math.min(1f, d[1] * 4f);
            float s = MathHelper.clamp(t[1] + (c[1] - d[1]), 0f, 1f);
            float v = d[2] > 0.02f ? MathHelper.clamp(t[2] * c[2] / d[2], 0f, 1f) : MathHelper.clamp(t[2] + (c[2] - d[2]), 0f, 1f);
            ramp[level] = MathHelper.hsvToRgb(h - (float) Math.floor(h), s, v) & 0xFFFFFF;
        }
        return ramp;
    }

    private static float luminance(int rgb) {
        return 0.2126f * (rgb >> 16 & 0xFF) + 0.7152f * (rgb >> 8 & 0xFF) + 0.0722f * (rgb & 0xFF);
    }

    private static long distanceSq(int a, int b) {
        long dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF), dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    /** @return hue (0..1), saturation, value */
    private static float[] hsv(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        float h;
        if (delta == 0) h = 0;
        else if (max == r) h = ((g - b) / delta) / 6f;
        else if (max == g) h = ((b - r) / delta + 2f) / 6f;
        else h = ((r - g) / delta + 4f) / 6f;
        if (h < 0) h += 1f;
        return new float[]{h, max == 0 ? 0 : delta / max, max};
    }
}
