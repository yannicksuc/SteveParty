package fr.lordfinn.steveparty.client.gui.paint;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.utils.ClientTextures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Pictures painted once, each into a texture of its own (one texture pixel per GUI pixel), then drawn as they are:
 * never painted per frame. Kept by name, at most {@code max}: past that, all are freed and painted again when needed.
 * Render thread only.
 */
public final class PaintedTextures {
    /** A painted picture. */
    public record Tex(Identifier id, int width, int height) {
        /** The whole picture at ({@code x}, {@code y}), with the current shader colour and blending. */
        public void draw(DrawContext context, int x, int y) {
            context.drawTexture(id, x, y, 0, 0, width, height, width, height);
        }
    }

    private final String path;
    private final int max;
    private final Map<String, Tex> textures = new HashMap<>();
    private int serial;

    /** @param path the textures' path in the mod's namespace, before their number */
    public PaintedTextures(String path, int max) {
        this.path = path;
        this.max = max;
    }

    /** The picture named {@code key}, painted by {@code paint} on first use. */
    public Tex get(String key, Supplier<int[][]> paint) {
        Tex known = textures.get(key);
        return known != null ? known : register(key, paint.get());
    }

    private Tex register(String key, int[][] pixels) {
        if (textures.size() >= max) clear();
        int h = pixels.length, w = pixels[0].length;
        NativeImage image = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setColor(x, y, ColorHelper.Abgr.toAbgr(pixels[y][x]));
        Identifier id = Steveparty.id(path + serial++);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
        Tex tex = new Tex(id, w, h);
        textures.put(key, tex);
        return tex;
    }

    /** Frees and forgets every picture (painted again when needed). */
    public void clear() {
        for (Tex tex : textures.values()) ClientTextures.destroy(tex.id());
        textures.clear();
    }
}
