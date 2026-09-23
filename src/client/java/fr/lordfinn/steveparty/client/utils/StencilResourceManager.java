package fr.lordfinn.steveparty.client.utils;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class StencilResourceManager {
    /** Max number of stencil shapes kept on the GPU (2 tiny 16x16 textures each). */
    private static final int MAX_CACHED_SHAPES = 256;
    /*
     * Keyed by the shape CONTENT (ByteBuffer equals/hashCode compare bytes), not by array identity:
     * callers build a new array every frame. LRU bounded; evicted textures are destroyed.
     * Render thread only.
     */
    private static final Map<ByteBuffer, StencilTextures> identifiers = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ByteBuffer, StencilTextures> eldest) {
            if (size() > MAX_CACHED_SHAPES) {
                destroy(eldest.getValue());
                return true;
            }
            return false;
        }
    };
    private static final Identifier baseTextureId = Steveparty.id("textures/block/traffic_sign_overlay_base.png");
    private static final Identifier stencilTextureId = Steveparty.id("textures/item/stencil.png");

    private static final int WIDTH = 16;
    private static final int HEIGHT = 16;
    private static final int SIZE = 256;


    static ColorBlender plankBlender = (stencilValue, existingColor) -> {
        int alpha = stencilValue == 1 ? 255 : 0; // Convert 1 to 255 (opaque), 0 to 0 (transparent)

        // Get the existing color components
        int red = (existingColor >> 16) & 0xFF;
        int green = (existingColor >> 8) & 0xFF;
        int blue = existingColor & 0xFF;

        // Modify the alpha channel based on the stencil data (preserving color)
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    };

    static ColorBlender metalBlender = (stencilValue, existingColor) -> {
        int alpha = stencilValue == 1 ? 0 : 255; // Convert 1 to 255 (opaque), 0 to 0 (transparent)

        // Get the existing color components
        int red = (existingColor >> 16) & 0xFF;
        int green = (existingColor >> 8) & 0xFF;
        int blue = existingColor & 0xFF;

        // Modify the alpha channel based on the stencil data (preserving color)
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    };


    private static StencilTextures createStencilShape(byte[] shape) {
        return new StencilTextures(
                createStencilTexture(shape, stencilTextureId, "textures/item/stencil_", metalBlender),
                createStencilTexture(shape, baseTextureId, "textures/block/traffic_sign_overlay_", plankBlender)
        );
    }

    public static StencilTextures addStencilShape(byte[] shape) {
        return getStencilShape(shape);
    }

    public static StencilTextures getStencilShape(byte[] shape) {
        if (shape == null || shape.length != SIZE) return null;
        StencilTextures textures = identifiers.get(ByteBuffer.wrap(shape));
        if (textures == null) {
            byte[] key = shape.clone(); // the stored key must never be mutated by the caller
            textures = createStencilShape(key);
            identifiers.put(ByteBuffer.wrap(key), textures);
        }
        return textures;
    }

    public static void clearCache() {
        identifiers.values().forEach(StencilResourceManager::destroy);
        identifiers.clear();
    }

    private static void destroy(StencilTextures textures) {
        if (textures == null) return;
        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        if (textures.metalStencil() != null) textureManager.destroyTexture(textures.metalStencil());
        if (textures.plankShape() != null) textureManager.destroyTexture(textures.plankShape());
    }

    private static Identifier createStencilTexture(byte[] stencilData, Identifier baseTexture, String suffix, ColorBlender colorBlender) {
        // Load the base texture from the texture manager
        AtomicReference<NativeImage> baseImage = new AtomicReference<>();
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(baseTexture);

        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream stream = resource.get().getInputStream()) {
            baseImage.set(NativeImage.read(stream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Make sure the base texture's width and height match your desired dimensions
        if (baseImage.get().getWidth() != WIDTH || baseImage.get().getHeight() != HEIGHT) {
            baseImage.get().close();
            throw new IllegalArgumentException("Base texture size does not match stencil size");
        }

        // Apply the stencil data to the base image
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                int index = x * WIDTH + y;
                int stencilValue = stencilData[index]; // 1 for opaque, 0 for transparent

                // Get the existing pixel from the base image
                int existingColor = baseImage.get().getColorArgb(x, y);

                // Call the passed color blending function to modify the color
                int newColor = colorBlender.blend(stencilValue, existingColor);

                // Set the new color
                baseImage.get().setColorArgb(x, y, newColor);
            }
        }

        // Register the new dynamic texture with the modified base texture
        return MinecraftClient.getInstance().getTextureManager()
                .registerDynamicTexture(suffix + byteArrayToBase36String(stencilData),
                        new NativeImageBackedTexture(baseImage.get()));
    }
    @FunctionalInterface
    public interface ColorBlender {
        int blend(int stencilValue, int existingColor);
    }


    public static String byteArrayToBase36String(byte[] byteArray) {
        StringBuilder base36String = new StringBuilder();

        for (byte b : byteArray) {
            // Convert byte to a base-36 string representation
            base36String.append(Integer.toString(b & 0xFF, 36));
        }

        return base36String.toString();
    }

    public record StencilTextures(Identifier metalStencil, Identifier plankShape) {}

}

