package fr.lordfinn.steveparty.client.renderer;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.utils.DynamicTextureCache;
import fr.lordfinn.steveparty.client.utils.ShaderPacks;
import fr.lordfinn.steveparty.client.utils.TileColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
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
import org.joml.Vector3f;

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
 * Seen through the clouds: they are drawn after it and, as it writes no depth, they covered it, both where it rises
 * in front of them and above them seen from the ground. So right before the clouds ({@link #maskClouds}), each beam
 * drawn in the frame leaves its silhouette in the depth buffer, just in front of the cloud layer: they are not drawn
 * there, and the beam shows through them, a landmark.
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
    /** How fast the outer shell's texture rises, as a share of the inner one's. */
    private static final float OUTER_RISE = 0.7F;
    /** The slow breathing of its light: per tick, and how much of it fades at the low point. */
    private static final float PULSE_SPEED = 0.1F, PULSE_DEPTH = 0.2F;
    /** The colour ramp: how dark its darkest grey gets (TileColors darkness), how light its lightest. */
    private static final float DARKEST = 0.3F, LIGHTEST = -0.4F;

    /**
     * The cloud layer, from the dimension's cloud height (vanilla clouds are 4 blocks thick, drawn a third of a block
     * above it): its thickness, and how far in front of it the mask lies, in blocks. Nearer than this to the camera's
     * height, the beam is seen almost level: the clouds behind it are too far to be drawn.
     */
    private static final float CLOUD_THICKNESS = 4.33F, CLOUD_MARGIN = 1F, LEVEL = 1F;
    /** The beams drawn in the frame, for {@link #maskClouds}: their foot (camera-relative), half-width, height, turn. */
    private static final int MAX_MASKED = 64, MASK_STRIDE = 6;
    private static final float[] MASKED = new float[MAX_MASKED * MASK_STRIDE];
    private static int maskedCount;
    private static final Vector3f FOOT = new Vector3f();

    private static final int MAX_CACHED = 32;
    private static final DynamicTextureCache<Integer> TEXTURES = new DynamicTextureCache<>(MAX_CACHED);
    private static NativeImage source;
    private static boolean failed;

    private HaloBeam() {
    }

    /**
     * Frees the coloured textures and the base read on each resource reload (a resource pack may change it), and
     * forgets the beams of the last frame when a new one starts (the clouds may be off: nothing then used them).
     */
    public static void initialize() {
        WorldRenderEvents.START.register(context -> maskedCount = 0);
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
        // The outer shell rises slower: its own wrap (a share of the inner one's would jump when that one wraps)
        float outerV0 = -MathHelper.fractionalPart(time * RISE * OUTER_RISE) + 0.5F;
        // Wrapped below 360°, so that the angle keeps its precision on a long-running world
        float turn = (time * TURN) % 360F;
        remember(matrices.peek(), radius, height, 45F - turn);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(turn));
        shell(consumers.getBuffer(Layers.INNER.apply(texture)), matrices.peek(), radius * INNER_SCALE, height, v0,
                INNER_ALPHA * pulse);
        matrices.pop();

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45F - turn));
        shell(consumers.getBuffer(Layers.OUTER.apply(texture)), matrices.peek(), radius, height, outerV0,
                OUTER_ALPHA * pulse);
        matrices.pop();
    }

    /**
     * Keeps a beam drawn in the frame for {@link #maskClouds}: its foot in camera-relative world space (the matrices
     * are, as for any block entity or the world's entity buffers; the camera's turn is not in them). Not during a
     * shader pack's shadow pass (seen from the sun), whose clouds are its own.
     */
    private static void remember(MatrixStack.Entry entry, float radius, float height, float outerTurn) {
        if (maskedCount >= MAX_MASKED || ShaderPacks.renderingShadows()) return;
        entry.getPositionMatrix().transformPosition(0, 0, 0, FOOT);
        int i = maskedCount++ * MASK_STRIDE;
        MASKED[i] = FOOT.x;
        MASKED[i + 1] = FOOT.y;
        MASKED[i + 2] = FOOT.z;
        MASKED[i + 3] = radius;
        MASKED[i + 4] = height;
        MASKED[i + 5] = outerTurn * MathHelper.RADIANS_PER_DEGREE;
    }

    /**
     * Right before the clouds are drawn (WorldRendererCloudMaskMixin): each beam drawn in the frame written in the depth
     * buffer only, its outer shell's square column, so that the clouds are not drawn over it.
     * <p>
     * Written at its own depth, the part of it beyond the clouds (above them seen from below, below them seen from above)
     * would still be behind them. So each of its points is moved along its line of sight (the camera's eye to it) onto
     * a plane just in front of the cloud layer: on screen the column covers the very same pixels, but at the depth of
     * that plane, in front of every cloud, behind anything nearer (the terrain in front of the beam keeps hiding it;
     * the rain and the world border, drawn later, are never cut by it). The part at the camera's height or on the other
     * side (seen level or looking away from the clouds) has no cloud behind it: left out. With the Fabulous graphics,
     * the clouds are drawn into their own target, bound by then: the mask goes there (the main depth untouched), and
     * the composite finds no cloud in the beam. None with a shader pack, which draws its own clouds. One column per
     * beam, nothing allocated.
     */
    public static void maskClouds() {
        int count = maskedCount;
        maskedCount = 0;
        if (count == 0 || ShaderPacks.inUse()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        float cloudHeight = client.world.getDimensionEffects().getCloudsHeight();
        if (Float.isNaN(cloudHeight)) return;
        double cameraY = client.gameRenderer.getCamera().getPos().y;
        // The planes just under and just over the cloud layer, camera-relative
        float under = (float) (cloudHeight - CLOUD_MARGIN - cameraY);
        float over = (float) (cloudHeight + CLOUD_THICKNESS + CLOUD_MARGIN - cameraY);
        // In the clouds: they are all around, nothing to see through
        if (under <= 0 && over >= 0) return;
        float plane = under > 0 ? under : over;

        RenderLayer layer = Layers.CLOUD_MASK;
        BufferBuilder builder = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
        for (int b = 0; b < count; b++) {
            int i = b * MASK_STRIDE;
            float x = MASKED[i], foot = MASKED[i + 1], z = MASKED[i + 2], r = MASKED[i + 3];
            float top = foot + MASKED[i + 4], angle = MASKED[i + 5];
            // The part above the camera's eye seen from under the clouds, below it seen from over them
            float from = under > 0 ? Math.max(foot, LEVEL) : foot;
            float to = under > 0 ? top : Math.min(top, -LEVEL);
            if (to <= from) continue;
            // The outer shell's corners, turned like it (RotationAxis.POSITIVE_Y: x' = x cos + z sin, z' = z cos - x sin)
            float cos = MathHelper.cos(angle), sin = MathHelper.sin(angle);
            float ax = -r * cos - r * sin, az = -r * cos + r * sin;
            float bx = r * cos - r * sin, bz = -r * cos - r * sin;
            float[] cx = {ax, bx, -ax, -bx}, cz = {az, bz, -az, -bz};
            for (int c = 0; c < 4; c++) {
                int n = (c + 1) % 4;
                maskVertex(builder, plane, x + cx[c], to, z + cz[c]);
                maskVertex(builder, plane, x + cx[n], to, z + cz[n]);
                maskVertex(builder, plane, x + cx[n], from, z + cz[n]);
                maskVertex(builder, plane, x + cx[c], from, z + cz[c]);
            }
        }
        BuiltBuffer built = builder.endNullable();
        if (built != null) layer.draw(built);
    }

    /**
     * A point of the column (camera-relative) moved along its line of sight onto the plane at height {@code plane}: the
     * same place on screen. A quad's edges stay on the same lines on screen, so the column covers the same pixels.
     */
    private static void maskVertex(BufferBuilder builder, float plane, float x, float y, float z) {
        float scale = plane / y;
        builder.vertex(x * scale, plane, z * scale);
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

        /** Depth only, both sides: the beam's silhouette kept free of clouds ({@link #maskClouds}). */
        static final RenderLayer CLOUD_MASK = RenderLayer.of("steveparty_halo_beam_cloud_mask", VertexFormats.POSITION,
                VertexFormat.DrawMode.QUADS, 256, false, false, MultiPhaseParameters.builder()
                        .program(POSITION_PROGRAM)
                        .cull(DISABLE_CULLING)
                        .writeMaskState(DEPTH_MASK)
                        .build(false));
    }
}
