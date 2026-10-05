package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.utils.TileColors;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The board path chevrons in colour. Tinting the grey / white arrow particle sprite by multiplication gave greyish,
 * dull chevrons; instead, each value level of the sprite is mapped onto a hand-style ramp of the colour
 * ({@link TileColors#shade}, like the tiles): its lightest pixels a highlight, the darker ones more saturated and
 * hue-shifted (yellow toward orange, blue toward deep indigo, green toward teal, red toward crimson). One small texture
 * per colour, made on first use and kept.
 */
final class ChevronSprites {
    /** How dark the darkest level of the sprite gets (TileColors darkness), and how light the lightest. */
    private static final float DARKEST = 0.9f, LIGHTEST = -0.45f;

    private static final Map<Integer, Identifier> TEXTURES = new HashMap<>();
    private static NativeImage source;
    private static boolean failed;

    private ChevronSprites() {
    }

    /** The chevron texture in {@code rgb} (alpha ignored), or the plain sprite if it could not be read. */
    static Identifier of(int rgb) {
        int key = rgb & 0xFFFFFF;
        Identifier cached = TEXTURES.get(key);
        if (cached != null) return cached;
        NativeImage sprite = source();
        if (sprite == null) return WorldDraw.CHEVRON;
        NativeImage image = new NativeImage(sprite.getWidth(), sprite.getHeight(), true);
        float min = 1, max = 0;
        for (int x = 0; x < sprite.getWidth(); x++) {
            for (int y = 0; y < sprite.getHeight(); y++) {
                int argb = ColorHelper.Abgr.toAbgr(sprite.getColor(x, y));
                if ((argb >>> 24) == 0) continue;
                float value = value(argb);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        for (int x = 0; x < sprite.getWidth(); x++) {
            for (int y = 0; y < sprite.getHeight(); y++) {
                int argb = ColorHelper.Abgr.toAbgr(sprite.getColor(x, y));
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    image.setColor(x, y, 0);
                    continue;
                }
                // Lightest level of the sprite -> highlight, darkest -> deepest shade
                float t = max > min ? (value(argb) - min) / (max - min) : 1;
                float darkness = DARKEST + (LIGHTEST - DARKEST) * t;
                image.setColor(x, y, ColorHelper.Abgr.toAbgr((alpha << 24) | TileColors.shade(key, darkness)));
            }
        }
        Identifier id = MinecraftClient.getInstance().getTextureManager()
                .registerDynamicTexture(Steveparty.MOD_ID + "_chevron_" + Integer.toHexString(key), new NativeImageBackedTexture(image));
        TEXTURES.put(key, id);
        return id;
    }

    private static float value(int argb) {
        return Math.max((argb >> 16) & 0xFF, Math.max((argb >> 8) & 0xFF, argb & 0xFF)) / 255f;
    }

    private static NativeImage source() {
        if (source != null || failed) return source;
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(WorldDraw.CHEVRON);
        if (resource.isEmpty()) {
            failed = true;
            return null;
        }
        try (InputStream in = resource.get().getInputStream()) {
            source = NativeImage.read(in);
        } catch (IOException e) {
            Steveparty.LOGGER.warn("Could not read the chevron sprite", e);
            failed = true;
        }
        return source;
    }
}
