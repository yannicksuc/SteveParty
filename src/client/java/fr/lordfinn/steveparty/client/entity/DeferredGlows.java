package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.client.utils.ShaderPacks;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Glows (the Mulas' halos and inner lights) drawn apart from their entity when a shader pack is in use.
 * <p>
 * Shader packs (Iris + Complementary) draw the entities, then their deferred passes (the clouds, over everything that
 * wrote no depth), then the translucent terrain, which writes depth (and they move the plain glass and the glass
 * panes to it). A soft, see-through glow must not write depth (the translucent blocks behind it would disappear
 * inside its quad), so drawn with its entity it ends up behind the clouds. Two moments work, one per situation:
 * <ul>
 * <li>after the translucent terrain, the usual one: in front of the clouds, and over the water or the glass behind
 * the glow. But a glow <em>behind</em> a translucent block fails the depth test against it there;</li>
 * <li>so a glow seen through a translucent surface is drawn right before the translucent terrain (after the deferred
 * passes: still in front of the clouds), and that surface is blended over it, as over anything else behind it.</li>
 * </ul>
 * Either way with the same translucent emissive layer, depth-tested (never seen through an opaque wall) but not
 * depth-writing.
 * <p>
 * The glow's vertices are recorded, already in camera-relative world space, while the entity renders
 * ({@link #buffer}), then replayed once per frame. Arrays are reused: nothing allocated per frame once warm. Render
 * thread only.
 */
public final class DeferredGlows {
    /** Per texture: x, y, z, u, v per vertex, and its colour. Drawn before / after the translucent terrain. */
    private static final Map<Identifier, Recorder> BEFORE_TRANSLUCENT = new LinkedHashMap<>(),
            AFTER_TRANSLUCENT = new LinkedHashMap<>();

    private DeferredGlows() {
    }

    public static void initialize() {
        WorldRenderEvents.START.register(context -> {
            BEFORE_TRANSLUCENT.values().forEach(Recorder::clear);
            AFTER_TRANSLUCENT.values().forEach(Recorder::clear);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            // (the first ones only if nothing drew them before the translucent terrain: better late than never)
            draw(BEFORE_TRANSLUCENT);
            draw(AFTER_TRANSLUCENT);
        });
    }

    /** Right before the translucent terrain is drawn (WorldRendererDeferredGlowsMixin). */
    public static void drawBeforeTranslucentTerrain() {
        draw(BEFORE_TRANSLUCENT);
    }

    private static void draw(Map<Identifier, Recorder> recorders) {
        for (Map.Entry<Identifier, Recorder> entry : recorders.entrySet()) {
            Recorder recorder = entry.getValue();
            if (recorder.vertices == 0) continue;
            // Our own buffer, drawn at once with the layer's state (not through the world's buffered consumers)
            RenderLayer layer = RenderLayer.getEntityTranslucentEmissive(entry.getKey(), false);
            BufferBuilder builder = Tessellator.getInstance().begin(layer.getDrawMode(), layer.getVertexFormat());
            recorder.replay(builder);
            BuiltBuffer built = builder.endNullable();
            if (built != null) layer.draw(built);
            recorder.clear();
        }
    }

    /**
     * Where to write a glow's quads (camera-relative, like any entity vertex) to have them drawn apart.
     * {@code behindTranslucent}: whether it is seen through a translucent surface ({@link #behindTranslucent}). During
     * the shader pack's shadow pass (entities seen from the sun) they are dropped: glows cast no shadow, and replayed
     * from the main camera they would land elsewhere.
     */
    public static VertexConsumer buffer(Identifier texture, boolean behindTranslucent) {
        if (ShaderPacks.renderingShadows()) return DISCARD;
        return (behindTranslucent ? BEFORE_TRANSLUCENT : AFTER_TRANSLUCENT).computeIfAbsent(texture, id -> new Recorder());
    }

    /**
     * Whether a translucent surface lies between the camera and a glow's centre: along the line, the translucent
     * matter changes (air to stained glass, water to air...; from a block to the same one there is no face: both in
     * the same water, nothing lies between). The blocks' layers are the ones in use, a shader pack's own included
     * (plain glass and panes made translucent). One short walk through the blocks, nothing kept.
     */
    public static boolean behindTranslucent(BlockView world, Vec3d camera, Vec3d glow) {
        SurfaceWalk walk = SURFACE_WALK;
        walk.world = world;
        walk.first = true;
        walk.matter = null;
        boolean crossed = BlockView.raycast(camera, glow, walk, SurfaceWalk::step, missed -> Boolean.FALSE);
        walk.world = null;
        return crossed;
    }

    private static final SurfaceWalk SURFACE_WALK = new SurfaceWalk();

    private static final class SurfaceWalk {
        private BlockView world;
        private boolean first;
        /** The translucent block or fluid of the previous block crossed, null if it had none. */
        @Nullable
        private Object matter;

        /** True once the translucent matter changes, null to walk on. */
        @Nullable
        private static Boolean step(SurfaceWalk walk, BlockPos pos) {
            Object matter = translucentMatter(walk.world.getBlockState(pos));
            if (!walk.first && matter != walk.matter) return Boolean.TRUE;
            walk.first = false;
            walk.matter = matter;
            return null;
        }

        @Nullable
        private static Object translucentMatter(BlockState state) {
            if (state.isAir()) return null;
            if (RenderLayers.getBlockLayer(state) == RenderLayer.getTranslucent()) return state.getBlock();
            FluidState fluid = state.getFluidState();
            if (fluid.isEmpty() || RenderLayers.getFluidLayer(fluid) != RenderLayer.getTranslucent()) return null;
            Fluid kind = fluid.getFluid();
            return kind instanceof FlowableFluid flowable ? flowable.getStill() : kind;
        }
    }

    /** Takes vertices and keeps nothing. */
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }
    };

    /** Records the vertices written to it (position, colour, texture coordinates; the rest is fixed). */
    private static final class Recorder implements VertexConsumer {
        private float[] positions = new float[5 * 64];
        private int[] colors = new int[64];
        private int vertices;

        void clear() {
            vertices = 0;
        }

        private int current() {
            return vertices - 1;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            if (vertices == colors.length) {
                positions = Arrays.copyOf(positions, positions.length * 2);
                colors = Arrays.copyOf(colors, colors.length * 2);
            }
            int i = vertices++ * 5;
            positions[i] = x;
            positions[i + 1] = y;
            positions[i + 2] = z;
            colors[current()] = 0xFFFFFFFF;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            colors[current()] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            int i = current() * 5;
            positions[i + 3] = u;
            positions[i + 4] = v;
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        void replay(VertexConsumer out) {
            for (int v = 0; v < vertices; v++) {
                int i = v * 5;
                int c = colors[v];
                out.vertex(positions[i], positions[i + 1], positions[i + 2])
                        .color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF)
                        .texture(positions[i + 3], positions[i + 4])
                        .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0, 1, 0);
            }
        }
    }
}
