package fr.lordfinn.steveparty.client.utils;

import fr.lordfinn.steveparty.client.entity.DeferredGlows;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ghosts of blocks and mobs: a real model drawn see-through, like a hologram (the Tile Linker Brush's suggestions, a
 * Spawn Bubble's resident mob, see GhostMobs).
 * <p>
 * Rendering rules (minecraft-rendering skill): never a model straight on a translucent layer, its inside, its far side
 * and its overlapping cubes would show through it. Two passes ({@link #request}): its depth alone, then its colour,
 * faded, where that depth is its own (LEQUAL, no depth written): only its nearest faces show. Drawn in
 * {@code AFTER_TRANSLUCENT} into the translucent target; seen through water or stained glass, right before the
 * translucent terrain into the main target instead (WorldRendererDeferredGlowsMixin): the water or the glass is
 * blended over it, it is never hidden behind it. With a shader pack, always there. A model still looking wrong so
 * (coplanar faces of overlapping cubes, inner faces a texture hides when opaque) gets a {@link Hologram}: its fewest
 * cubes, no inner face, no two faces in one plane, back faces culled ({@link #CHEST}).
 * <p>
 * Whatever layer the model asks for, the same texture is drawn in a layer of ours (the texture parsed from the layer's
 * description: any renderer works).
 */
public final class GhostModels {
    /**
     * The ghost chest: the vanilla chest's box as one 14 x 14 x 14 pixel cube (its base cut where its lid starts, the
     * faces between them left out) and its latch (its back against the box left out), its texture's own regions.
     */
    public static final Hologram CHEST = new Hologram(Identifier.ofVanilla("textures/entity/chest/normal.png"), () -> {
        ModelPart chest = MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.CHEST);
        float lidBottom = 9 / 16f;
        List<Quad> quads = new ArrayList<>();
        for (Quad quad : Quad.of(chest.getChild("bottom"))) {
            if (quad.facing() == Direction.UP) continue;
            quads.add(quad.facing().getAxis().isHorizontal() ? quad.cutAbove(lidBottom) : quad);
        }
        for (Quad quad : Quad.of(chest.getChild("lid"))) if (quad.facing() != Direction.DOWN) quads.add(quad);
        for (Quad quad : Quad.of(chest.getChild("lock"))) if (quad.facing() != Direction.NORTH) quads.add(quad);
        return quads;
    });

    private static final Pattern TEXTURE = Pattern.compile("texture\\[(?:Optional\\[)?([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)]?\\(blur=");
    /** Each layer's texture, as its description names it (empty: none found, the layer is left out). */
    private static final Map<RenderLayer, Identifier> TEXTURES = new IdentityHashMap<>();
    private static final Identifier NONE = Identifier.of("steveparty", "none");
    /** The ghosts asked for this frame. */
    private static final List<Ghost> FRAME = new ArrayList<>();
    private static VertexConsumerProvider.Immediate buffers;

    /** Draws a ghost's model, the matrices' origin where it was asked for (camera relative). */
    @FunctionalInterface
    public interface Model {
        void draw(MatrixStack matrices, VertexConsumerProvider buffers);
    }

    /** A ghost to draw this frame: where (world), what is behind translucent blocks, its opacity, its models. */
    private record Ghost(Vec3d origin, boolean behindTranslucent, float alpha, boolean cull, Model solid, @Nullable Model film) {
    }

    private GhostModels() {
    }

    public static void initialize() {
        WorldRenderEvents.START.register(context -> FRAME.clear());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(GhostModels::draw);
    }

    /**
     * Asks for a ghost this frame (before the translucent terrain: AFTER_SETUP, an entity or block entity renderer):
     * {@code solid} drawn in two passes, its depth then its colour faded to {@code alpha} (back faces culled if
     * {@code cull}), then {@code film} over it (a see-through film, a Spawn Bubble's: colour alone, far faces first).
     * {@code origin} is the matrices' origin (world), {@code middle} its middle, which tells whether it is seen through
     * a translucent block.
     */
    public static void request(Vec3d origin, Vec3d middle, float alpha, boolean cull, Model solid, @Nullable Model film) {
        if (alpha <= 0.01f || ShaderPacks.renderingShadows()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        // A shader pack lights and fogs what is drawn after the translucent terrain its own way: a ghost there came out
        // washed out, almost gone in the shade (Complementary); before it, it reads as with the vanilla graphics
        boolean behind = ShaderPacks.inUse() || client.world != null && DeferredGlows.behindTranslucent(client.world, camera, middle);
        FRAME.add(new Ghost(origin, behind, MathHelper.clamp(alpha, 0, 1), cull, solid, film));
    }

    /** Right before the translucent terrain (WorldRendererDeferredGlowsMixin): the ghosts seen through it. */
    public static void drawBeforeTranslucentTerrain() {
        if (FRAME.isEmpty()) return;
        draw(new MatrixStack(), MinecraftClient.getInstance().gameRenderer.getCamera().getPos(), true);
    }

    private static void draw(WorldRenderContext context) {
        if (FRAME.isEmpty()) return;
        MatrixStack matrices = context.matrixStack();
        if (matrices != null) draw(matrices, context.camera().getPos(), false);
        draw(new MatrixStack(), context.camera().getPos(), true); // the hook before the translucent terrain was missed
        FRAME.clear();
    }

    /**
     * Draws the ghosts seen through translucent blocks ({@code behind}: into the main target, the blocks drawn over
     * them) or the others (after them, into the translucent target), and forgets them.
     */
    private static void draw(MatrixStack matrices, Vec3d camera, boolean behind) {
        VertexConsumerProvider.Immediate buffers = buffers();
        for (Ghost ghost : FRAME) {
            if (ghost.behindTranslucent() != behind) continue;
            matrices.push();
            matrices.translate(ghost.origin().x - camera.x, ghost.origin().y - camera.y, ghost.origin().z - camera.z);
            ghost.solid().draw(matrices, faded(buffers, true, ghost.alpha(), !behind, ghost.cull()));
            buffers.draw();
            ghost.solid().draw(matrices, faded(buffers, false, ghost.alpha(), !behind, ghost.cull()));
            buffers.draw();
            if (ghost.film() != null) {
                ghost.film().draw(matrices, faded(buffers, false, ghost.alpha(), !behind, false));
                buffers.draw();
            }
            matrices.pop();
        }
        FRAME.removeIf(ghost -> ghost.behindTranslucent() == behind);
    }

    /**
     * The layers a ghost is drawn through: every layer asked for becomes ours for its texture, its depth alone
     * ({@code depth}) or its colour faded to {@code alpha}, into the translucent target or the main one.
     */
    public static VertexConsumerProvider faded(VertexConsumerProvider.Immediate buffers, boolean depth, float alpha, boolean translucentTarget) {
        return faded(buffers, depth, alpha, translucentTarget, false);
    }

    private static VertexConsumerProvider faded(VertexConsumerProvider.Immediate buffers, boolean depth, float alpha, boolean translucentTarget,
                                                boolean cull) {
        return layer -> {
            Identifier texture = textureOf(layer);
            if (texture == null) return DISCARD;
            if (depth) return buffers.getBuffer(GhostLayers.of(texture, true, translucentTarget, cull));
            return new AlphaConsumer(buffers.getBuffer(GhostLayers.of(texture, false, translucentTarget, cull)), alpha);
        };
    }

    private static VertexConsumerProvider.Immediate buffers() {
        if (buffers == null) buffers = VertexConsumerProvider.immediate(new BufferAllocator(RenderLayer.DEFAULT_BUFFER_SIZE));
        return buffers;
    }

    /** The texture {@code layer} draws (from its description: the same with any mapping), or null. */
    private static @Nullable Identifier textureOf(RenderLayer layer) {
        Identifier texture = TEXTURES.computeIfAbsent(layer, l -> {
            Matcher found = TEXTURE.matcher(l.toString());
            return found.find() ? Identifier.tryParse(found.group(1)) : NONE;
        });
        return texture == NONE || texture == null ? null : texture;
    }

    /**
     * A model simplified for its ghost: quads (block units, wound outward), to draw back faces culled. Made from the
     * real model's parts the first time it is drawn (its texture's regions kept), again after a resource reload.
     */
    public static final class Hologram {
        private final Identifier texture;
        private final Supplier<List<Quad>> build;
        private @Nullable List<Quad> quads;
        private @Nullable Object builtWith;

        public Hologram(Identifier texture, Supplier<List<Quad>> build) {
            this.texture = texture;
            this.build = build;
        }

        /** Draws it (in a {@link Model}), tinted {@code rgb}. */
        public void draw(MatrixStack matrices, VertexConsumerProvider buffers, int rgb, int light) {
            Object loader = MinecraftClient.getInstance().getEntityModelLoader();
            if (quads == null || builtWith != loader) {
                quads = build.get();
                builtWith = loader;
            }
            VertexConsumer consumer = buffers.getBuffer(RenderLayer.getEntityTranslucentCull(texture));
            for (Quad quad : quads) quad.emit(matrices.peek(), consumer, 0xFF000000 | rgb, light);
        }
    }

    /** A quad of a model: its 4 corners (x, y, z, u, v; blocks), its facing. */
    public record Quad(float[][] corners, Direction facing) {
        /** The quads of a part (and its children), as it is posed now, in block units. */
        public static List<Quad> of(ModelPart part) {
            Recorder recorder = new Recorder();
            part.render(new MatrixStack(), recorder, 0, OverlayTexture.DEFAULT_UV, -1);
            return recorder.quads;
        }

        /** This side quad cut at the height {@code top} (its texture cut as much), if it goes above. */
        Quad cutAbove(float top) {
            float[][] cut = new float[4][];
            for (int i = 0; i < 4; i++) {
                float[] corner = corners[i].clone();
                if (corner[1] > top + 1.0E-4f) {
                    float[] low = lowestUnder(corner);
                    float t = (corner[1] - top) / (corner[1] - low[1]);
                    corner[4] = corner[4] + (low[4] - corner[4]) * t;
                    corner[1] = top;
                }
                cut[i] = corner;
            }
            return new Quad(cut, facing);
        }

        private float[] lowestUnder(float[] corner) {
            Predicate<float[]> under = c -> Math.abs(c[0] - corner[0]) < 1.0E-4f && Math.abs(c[2] - corner[2]) < 1.0E-4f && c[1] < corner[1];
            for (float[] c : corners) if (under.test(c)) return c;
            return corner;
        }

        void emit(MatrixStack.Entry entry, VertexConsumer consumer, int argb, int light) {
            Direction.Axis axis = facing.getAxis();
            float nx = axis == Direction.Axis.X ? facing.getDirection().offset() : 0;
            float ny = axis == Direction.Axis.Y ? facing.getDirection().offset() : 0;
            float nz = axis == Direction.Axis.Z ? facing.getDirection().offset() : 0;
            for (float[] c : corners) {
                consumer.vertex(entry, c[0], c[1], c[2]).color(argb).texture(c[3], c[4]).overlay(OverlayTexture.DEFAULT_UV)
                        .light(light).normal(entry, nx, ny, nz);
            }
        }
    }

    /** Records the quads a model part draws. */
    private static final class Recorder implements VertexConsumer {
        final List<Quad> quads = new ArrayList<>();
        private final float[][] corners = new float[4][];
        private int count;

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            corners[count] = new float[]{x, y, z, 0, 0};
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            corners[count][3] = u;
            corners[count][4] = v;
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            if (++count == 4) {
                quads.add(new Quad(corners.clone(), Direction.getFacing(x, y, z)));
                count = 0;
            }
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
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
    }

    /** Fades the colour written through it. */
    private record AlphaConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int a) {
            delegate.color(red, green, blue, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }
    }

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

    /** Our layers, per texture: the entity shaders (cut out on the texture's alpha), its depth alone or its colour. */
    private static final class GhostLayers extends RenderLayer {
        private record Key(Identifier texture, boolean depth, boolean translucentTarget, boolean cull) {
        }

        private static final Function<Key, RenderLayer> LAYERS = Util.memoize(GhostLayers::create);

        private GhostLayers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling,
                            boolean translucent, Runnable begin, Runnable end) {
            super(name, format, mode, size, crumbling, translucent, begin, end);
        }

        static RenderLayer of(Identifier texture, boolean depth, boolean translucentTarget, boolean cull) {
            return LAYERS.apply(new Key(texture, depth, translucentTarget, cull));
        }

        private static RenderLayer create(Key key) {
            RenderPhase.WriteMaskState mask = key.depth() ? DEPTH_MASK : COLOR_MASK;
            String name = "steveparty_ghost_" + (key.depth() ? "depth" : "color") + (key.translucentTarget() ? "" : "_main")
                    + (key.cull() ? "_cull" : "");
            return RenderLayer.of(name, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS,
                    1536, true, !key.depth(), MultiPhaseParameters.builder()
                            .program(ENTITY_TRANSLUCENT_PROGRAM)
                            .texture(new Texture(key.texture(), false, false))
                            .transparency(TRANSLUCENT_TRANSPARENCY)
                            .cull(key.cull() ? ENABLE_CULLING : DISABLE_CULLING)
                            .lightmap(ENABLE_LIGHTMAP)
                            .overlay(ENABLE_OVERLAY_COLOR)
                            .depthTest(LEQUAL_DEPTH_TEST)
                            .target(key.translucentTarget() ? TRANSLUCENT_TARGET : MAIN_TARGET)
                            .writeMaskState(mask)
                            .build(false));
        }
    }
}
