package fr.lordfinn.steveparty.client.renderer;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.utils.DynamicTextureCache;
import fr.lordfinn.steveparty.client.utils.TileColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Function;

/**
 * The mod's light beam ("halo") marking an important place from afar, in any colour: an airy column of light, wider
 * and paler than a beacon beam, with no opaque core: what stands in it (a pawn, an entity) or behind it stays visible.
 * <p>
 * Two see-through shells turning slowly, their texture rising along them: an inner one blended over what is behind
 * it, an outer one added to it (light only adds: it never greys the sky behind it, unlike vanilla's translucent glow).
 * Neither writes depth nor is culled. The texture (textures/misc/halo_beam.png) is greys only: like the tiles and
 * chevrons ({@link TileColors#shade}), each grey level is mapped onto a ramp of the colour, its shadows hue-shifted (a
 * yellow's toward orange) rather than greyed, its lightest pixels a highlight. One small texture per colour, made on
 * first use (the least recently used freed past {@link #MAX_CACHED}), remade after a resource reload.
 * <p>
 * Use: translate the matrices to the centre of the beam's foot, then {@link #render}; drawn in whatever buffers are
 * given (a block entity renderer's, or the world's entity buffers after the entities). A block entity drawing one
 * should be rendered outside its bounding box ({@code rendersOutsideBoundingBox}) and from afar ({@code getRenderDistance}).
 */
@Environment(EnvType.CLIENT)
public final class HaloBeam {
    /** A beam's default half-width, in blocks (a beacon's core is 0.2, its glow 0.25). */
    public static final float DEFAULT_RADIUS = 0.42F;

    private static final Identifier TEXTURE = Steveparty.id("textures/misc/halo_beam.png");
    /** The inner shell's share of the half-width, its alpha; the outer (added) shell's alpha. */
    private static final float INNER_SCALE = 0.62F, INNER_ALPHA = 0.62F, OUTER_ALPHA = 0.24F;
    /** How much of its foot's alpha the beam keeps at its top. */
    private static final float TOP_ALPHA = 0.45F;
    /** Texture heights per block; how fast it rises (per tick); degrees per tick each shell turns (opposite ways). */
    private static final float V_PER_BLOCK = 0.4F, RISE = 0.03F, TURN = 0.6F;
    /** The slow breathing of its light: per tick, and how much of it fades at the low point. */
    private static final float PULSE_SPEED = 0.1F, PULSE_DEPTH = 0.2F;
    /** The colour ramp: how dark its darkest grey gets (TileColors darkness), how light its lightest. */
    private static final float DARKEST = 0.3F, LIGHTEST = -0.4F;

    private static final int MAX_CACHED = 32;
    private static final DynamicTextureCache<Integer> TEXTURES = new DynamicTextureCache<>(MAX_CACHED);
    private static NativeImage source;
    private static boolean failed;

    private HaloBeam() {
    }

    /** Frees the coloured textures and the base read on each resource reload: a resource pack may change it. */
    public static void registerReloadListener() {
        DynamicTextureCache.onResourceReload("halo_beam_textures", () -> {
            TEXTURES.clear();
            if (source != null) source.close();
            source = null;
            failed = false;
        });
    }

    /**
     * Draws a beam from the matrices' origin (the centre of its foot) up {@code height} blocks.
     *
     * @param argb   its colour (alpha ignored)
     * @param radius its half-width, in blocks ({@link #DEFAULT_RADIUS})
     * @param alpha  its overall opacity, 0..1 (to fade it in or out)
     * @param time   the world's time plus the tick delta: its turning, rising and breathing
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int argb, float radius, float height,
                              float alpha, float time) {
        if (alpha <= 0 || height <= 0) return;
        Identifier texture = texture(argb & 0xFFFFFF);
        float pulse = alpha * (1 - PULSE_DEPTH * (0.5F + 0.5F * MathHelper.sin(time * PULSE_SPEED)));
        float v0 = -MathHelper.fractionalPart(time * RISE);
        // Wrapped below 360°, so that the angle keeps its precision on a long-running world
        float turn = (time * TURN) % 360F;

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(turn));
        shell(consumers.getBuffer(Layers.INNER.apply(texture)), matrices.peek(), radius * INNER_SCALE, height, v0,
                INNER_ALPHA * pulse);
        matrices.pop();

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45F - turn));
        shell(consumers.getBuffer(Layers.OUTER.apply(texture)), matrices.peek(), radius, height, v0 * 0.7F + 0.5F,
                OUTER_ALPHA * pulse);
        matrices.pop();
    }

    /** The 4 sides of a square column of half-size {@code r}, its alpha fading toward its top. */
    private static void shell(VertexConsumer consumer, MatrixStack.Entry entry, float r, float height, float v0, float alpha) {
        float v1 = v0 + height * V_PER_BLOCK;
        int foot = MathHelper.clamp(Math.round(alpha * 255), 0, 255);
        int top = MathHelper.clamp(Math.round(alpha * TOP_ALPHA * 255), 0, 255);
        float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i], b = corners[(i + 1) % 4];
            float nx = (a[0] + b[0]) * 0.5F / r, nz = (a[1] + b[1]) * 0.5F / r;
            vertex(consumer, entry, a[0], height, a[1], 0, v0, top, nx, nz);
            vertex(consumer, entry, b[0], height, b[1], 1, v0, top, nx, nz);
            vertex(consumer, entry, b[0], 0, b[1], 1, v1, foot, nx, nz);
            vertex(consumer, entry, a[0], 0, a[1], 0, v1, foot, nx, nz);
        }
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, float u, float v,
                               int alpha, float nx, float nz) {
        consumer.vertex(entry, x, y, z).color(255, 255, 255, alpha).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, nx, 0, nz);
    }

    // ---------------------------------------------------------------- coloured textures

    /** The beam's texture in {@code rgb}, or the grey base if it could not be read. */
    private static Identifier texture(int rgb) {
        NativeImage base = source();
        if (base == null) return TEXTURE;
        return TEXTURES.get(rgb, key -> make(base, key));
    }

    private static Identifier make(NativeImage base, int rgb) {
        NativeImage image = new NativeImage(base.getWidth(), base.getHeight(), true);
        float min = 1, max = 0;
        for (int x = 0; x < base.getWidth(); x++) {
            for (int y = 0; y < base.getHeight(); y++) {
                int argb = ColorHelper.Abgr.toAbgr(base.getColor(x, y));
                if ((argb >>> 24) == 0) continue;
                min = Math.min(min, value(argb));
                max = Math.max(max, value(argb));
            }
        }
        for (int x = 0; x < base.getWidth(); x++) {
            for (int y = 0; y < base.getHeight(); y++) {
                int argb = ColorHelper.Abgr.toAbgr(base.getColor(x, y));
                int alpha = argb >>> 24;
                float t = max > min ? (value(argb) - min) / (max - min) : 1;
                float darkness = DARKEST + (LIGHTEST - DARKEST) * t;
                image.setColor(x, y, ColorHelper.Abgr.toAbgr((alpha << 24) | TileColors.shade(rgb, darkness)));
            }
        }
        return MinecraftClient.getInstance().getTextureManager()
                .registerDynamicTexture(Steveparty.MOD_ID + "_halo_beam_" + Integer.toHexString(rgb), new NativeImageBackedTexture(image));
    }

    private static float value(int argb) {
        return Math.max((argb >> 16) & 0xFF, Math.max((argb >> 8) & 0xFF, argb & 0xFF)) / 255f;
    }

    private static NativeImage source() {
        if (source != null || failed) return source;
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(TEXTURE);
        if (resource.isEmpty()) {
            failed = true;
            return null;
        }
        try (InputStream in = resource.get().getInputStream()) {
            source = NativeImage.read(in);
        } catch (IOException e) {
            Steveparty.LOGGER.warn("Could not read the halo beam texture", e);
            failed = true;
        }
        return source;
    }

    /**
     * The beam's render layers: the beacon beam's shader (unlit, fogged), both sides drawn, no depth written (what is
     * drawn later behind it still shows); the inner shell blended over what is behind it, the outer one added to it.
     */
    private static final class Layers extends RenderLayer {
        static final Function<Identifier, RenderLayer> INNER = Util.memoize(texture -> of(
                "steveparty_halo_beam_inner", texture, TRANSLUCENT_TRANSPARENCY));
        static final Function<Identifier, RenderLayer> OUTER = Util.memoize(texture -> of(
                "steveparty_halo_beam_outer", texture, LIGHTNING_TRANSPARENCY));

        private Layers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling,
                       boolean translucent, Runnable begin, Runnable end) {
            super(name, format, mode, size, crumbling, translucent, begin, end);
        }

        private static RenderLayer of(String name, Identifier texture, Transparency transparency) {
            return RenderLayer.of(name, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS,
                    1536, false, true, MultiPhaseParameters.builder()
                            .program(BEACON_BEAM_PROGRAM)
                            .texture(new Texture(texture, false, false))
                            .transparency(transparency)
                            .cull(DISABLE_CULLING)
                            .writeMaskState(COLOR_MASK)
                            .build(false));
        }
    }
}
