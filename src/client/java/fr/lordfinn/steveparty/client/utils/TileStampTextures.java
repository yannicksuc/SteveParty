package fr.lordfinn.steveparty.client.utils;

import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The 32x32 face of a stamped tile ({@link TileStampComponent}), drawn like the tile's own faces (smiley, angry...):
 * a flat square in the dye's colour, its frame darkening toward the edge, and the stamped pattern (16x16, in the
 * middle) in the darkest shade of that colour. Created on first use, LRU bounded, render thread only.
 */
public final class TileStampTextures {
    public static final int SIDE = 32;
    /** Brightness of the frame's rings, from the edge inward, then the middle (as on the angry face: 128 .. 227 red). */
    private static final float[] RINGS = {0.56f, 0.67f, 0.78f, 0.78f, 0.86f, 0.86f};
    /** The pattern: as dark as the outer ring. */
    private static final float PATTERN = 0.56f;
    private static final int MAX_CACHED = 128;

    private record Key(ByteBuffer shape, DyeColor color) {
    }

    private static final Map<Key, Identifier> TEXTURES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Identifier> eldest) {
            if (size() <= MAX_CACHED) return false;
            MinecraftClient.getInstance().getTextureManager().destroyTexture(eldest.getValue());
            return true;
        }
    };

    private TileStampTextures() {
    }

    public static Identifier get(TileStampComponent stamp) {
        byte[] shape = stamp.shapeArray();
        Key key = new Key(ByteBuffer.wrap(shape), stamp.color());
        Identifier texture = TEXTURES.get(key);
        if (texture == null) {
            texture = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("tile_stamp",
                    new NativeImageBackedTexture(draw(shape, stamp.color())));
            TEXTURES.put(key, texture);
        }
        return texture;
    }

    /** The face's base colour: the dye's, lightened when too dark for its pattern to show. */
    public static int baseColor(DyeColor color) {
        int rgb = color.getEntityColor();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        if (0.299 * r + 0.587 * g + 0.114 * b < 70) {
            r += (255 - r) * 3 / 10;
            g += (255 - g) * 3 / 10;
            b += (255 - b) * 3 / 10;
        }
        return (r << 16) | (g << 8) | b;
    }

    private static int shade(int rgb, float factor) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static NativeImage draw(byte[] shape, DyeColor color) {
        int base = baseColor(color);
        NativeImage image = new NativeImage(SIDE, SIDE, true);
        for (int x = 0; x < SIDE; x++) {
            for (int y = 0; y < SIDE; y++) {
                // Same footprint as the tile faces: 28x28 in the middle of the 32x32 (2 px transparent margin)
                if (x < 2 || x > 29 || y < 2 || y > 29) {
                    image.setColorArgb(x, y, 0);
                    continue;
                }
                int ring = Math.min(Math.min(x - 2, 29 - x), Math.min(y - 2, 29 - y));
                float factor = ring < RINGS.length ? RINGS[ring] : 1.0f;
                if (x >= 8 && x < 24 && y >= 8 && y < 24 && shape[StencilShape.index(x - 8, y - 8)] != 0) factor = PATTERN;
                image.setColorArgb(x, y, shade(base, factor));
            }
        }
        return image;
    }
}
