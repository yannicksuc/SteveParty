package fr.lordfinn.steveparty.client.utils;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.util.math.ColorHelper;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Dynamic textures of stencil shapes, one per shape and {@link Kind}, created on first use: 16x16, plus the kind's
 * {@link Kind#margin() margin} on every side (the metal stencil's frame, never cut).
 * <p>
 * Keyed by the shape CONTENT (callers build a new array every frame). LRU bounded: evicted textures are destroyed.
 * Cleared on resource reload, so that a resource pack changing the base textures is taken into account.
 * Render thread only.
 */
public class StencilResourceManager {
    /** How the shape is drawn. */
    public enum Kind {
        /** The stencil itself: 18x18 metal plate, the shape cut out inside its 1 px frame (items, stencil maker). */
        METAL(Steveparty.id("textures/item/stencil.png"), true, 1),
        /** Painted wood grain in the shape (wooden signs, as they always were). */
        WOOD(Steveparty.id("textures/block/easel_sign_overlay_base.png"), false, 0),
        /** Plain white shape, tinted by the paint colour (rock, plastic, sprayed paint, engravings). */
        FLAT(null, false, 0);

        private final @Nullable Identifier base;
        private final boolean cutOut;
        private final int margin;

        Kind(@Nullable Identifier base, boolean cutOut, int margin) {
            this.base = base;
            this.cutOut = cutOut;
            this.margin = margin;
        }

        /** Pixels around the 16x16 shape. */
        public int margin() {
            return margin;
        }

        /** Side of the texture: 16 + 2 margins. */
        public int size() {
            return StencilShape.SIDE + 2 * margin;
        }    }

    /** Max number of textures kept on the GPU (tiny 16x16 textures). */
    private static final int MAX_CACHED_TEXTURES = 512;
    private static final Map<Key, Identifier> TEXTURES = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Identifier> eldest) {
            if (size() > MAX_CACHED_TEXTURES) {
                destroy(eldest.getValue());
                generation++;
                return true;
            }
            return false;
        }
    };

    private record Key(Kind kind, ByteBuffer shape) {
    }

    /** Changes whenever a texture is destroyed: texture ids kept by callers from an older generation may be gone. */
    private static int generation;

    private StencilResourceManager() {
    }

    /** Clears the cache on every resource reload. */
    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id("stencil_textures");
            }

            @Override
            public void reload(ResourceManager manager) {
                clearCache();
            }
        });
    }

    /** @return the texture of {@code shape} drawn as {@code kind}, or null if the shape is invalid. */
    public static @Nullable Identifier getTexture(@Nullable byte[] shape, Kind kind) {
        if (!StencilShape.isValid(shape)) return null;
        Identifier texture = TEXTURES.get(new Key(kind, ByteBuffer.wrap(shape)));
        if (texture == null) {
            byte[] key = shape.clone(); // the stored key must never be mutated by the caller
            texture = createTexture(key, kind);
            if (texture == null) return null;
            TEXTURES.put(new Key(kind, ByteBuffer.wrap(key)), texture);
        }
        return texture;
    }

    public static void clearCache() {
        TEXTURES.values().forEach(StencilResourceManager::destroy);
        TEXTURES.clear();
        generation++;
    }

    /** @return the current generation: a texture id from {@link #getTexture} is valid while it does not change. */
    public static int generation() {
        return generation;
    }

    private static void destroy(Identifier texture) {
        if (texture == null) return;
        ClientTextures.destroy(texture);
    }

    private static @Nullable Identifier createTexture(byte[] shape, Kind kind) {
        NativeImage image;
        if (kind.base == null) {
            image = new NativeImage(StencilShape.SIDE, StencilShape.SIDE, true);
            image.fillRect(0, 0, StencilShape.SIDE, StencilShape.SIDE, 0xFFFFFFFF);
        } else {
            Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(kind.base);
            if (resource.isEmpty()) return null;
            try (InputStream stream = resource.get().getInputStream()) {
                image = NativeImage.read(stream);
            } catch (IOException e) {
                Steveparty.LOGGER.error("Can't read stencil base texture {}", kind.base, e);
                return null;
            }
            if (image.getWidth() != kind.size() || image.getHeight() != kind.size()) {
                image.close();
                Steveparty.LOGGER.error("Stencil base texture {} must be {}x{}", kind.base, kind.size(), kind.size());
                return null;
            }
        }
        // Painted kinds keep the pixels of the shape, the metal stencil keeps the others (the shape is cut out)
        for (int x = 0; x < StencilShape.SIDE; x++) {
            for (int y = 0; y < StencilShape.SIDE; y++) {
                boolean set = shape[StencilShape.index(x, y)] != 0;
                int px = x + kind.margin, py = y + kind.margin;
                int color = ColorHelper.Abgr.toAbgr(image.getColor(px, py));
                int alpha = set != kind.cutOut ? 0xFF : 0;
                image.setColor(px, py, ColorHelper.Abgr.toAbgr((alpha << 24) | (color & 0x00FFFFFF)));
            }
        }
        String name = "stencil_" + kind.name().toLowerCase() + "_" + Integer.toHexString(Arrays.hashCode(shape));
        return MinecraftClient.getInstance().getTextureManager().registerDynamicTexture(name, new NativeImageBackedTexture(image));
    }
}
