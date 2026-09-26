package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.client.utils.ShaderPacks;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Glows (the Mulas' halos and inner lights) drawn late, after the translucent terrain, when a shader pack is in use.
 * <p>
 * Shader packs (Iris + Complementary) composite the clouds after the entities, over everything that wrote no depth:
 * glows drawn with the entities, which must not write depth (they are soft, see-through), ended up behind the clouds.
 * Writing depth instead made the translucent blocks behind them (stained glass, water, star fragment blocks)
 * disappear inside the glow's quad. Drawn here, after the translucent terrain (and after the clouds' composite), with
 * the same translucent emissive layer, depth-tested but not depth-writing, they are both in front of the clouds and
 * over the translucent blocks behind them.
 * <p>
 * The glow's vertices are recorded, already in camera-relative world space, while the entity renders
 * ({@link #buffer}), then replayed once per frame. Arrays are reused: nothing allocated per frame once warm. Render
 * thread only.
 */
public final class DeferredGlows {
    /** Per texture: x, y, z, u, v per vertex, and its colour. */
    private static final Map<Identifier, Recorder> RECORDERS = new LinkedHashMap<>();

    private DeferredGlows() {
    }

    public static void initialize() {
        WorldRenderEvents.START.register(context -> RECORDERS.values().forEach(Recorder::clear));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            for (Map.Entry<Identifier, Recorder> entry : RECORDERS.entrySet()) {
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
        });
    }

    /**
     * Where to write a glow's quads (camera-relative, like any entity vertex) to have them drawn late. During the
     * shader pack's shadow pass (entities seen from the sun) they are dropped: glows cast no shadow, and replayed from
     * the main camera they would land elsewhere.
     */
    public static VertexConsumer buffer(Identifier texture) {
        if (ShaderPacks.renderingShadows()) return DISCARD;
        return RECORDERS.computeIfAbsent(texture, id -> new Recorder());
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
                        .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0);
            }
        }
    }
}
