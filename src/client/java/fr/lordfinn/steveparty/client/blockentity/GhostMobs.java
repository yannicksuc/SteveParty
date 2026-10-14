package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.client.entity.DeferredGlows;
import fr.lordfinn.steveparty.client.utils.ShaderPacks;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whole mobs drawn see-through, like holograms: a Spawn Bubble's resident mob (« always visible ») shown where it will
 * stand while no party runs (SpawnBubbleRenderer). Any renderer works: whatever layer it asks for, the same texture is
 * drawn in a layer of ours, the colour faded to {@link Ghost#alpha}.
 * <p>
 * Rendering rules (minecraft-rendering skill): a see-through thing, so asked for by the block entity renderer and drawn
 * later, in {@code AFTER_TRANSLUCENT} (after the translucent terrain and the block entities, before the clouds), into the
 * translucent target. Two passes: its depth alone first, then its colour where that depth is its own (writing no
 * depth): only its nearest faces show, never its inside nor its far side through itself. Seen through water or
 * stained glass (DeferredGlows#behindTranslucent), it is drawn right before the translucent terrain instead
 * (WorldRendererDeferredGlowsMixin), into the main target: the water or the glass is blended over it.
 */
public final class GhostMobs {
    /** A ghost to draw this frame: the mob, where (world), its yaw, its light and its opacity. */
    private record Ghost(Entity mob, Vec3d at, float yaw, int light, float alpha, boolean behindTranslucent) {
    }

    private static final List<Ghost> FRAME = new ArrayList<>();
    private static final Pattern TEXTURE = Pattern.compile("texture\\[(?:Optional\\[)?([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)]?\\(blur=");
    /** Each layer's texture, as its description names it (empty: none found, the layer is left out). */
    private static final Map<RenderLayer, Identifier> TEXTURES = new IdentityHashMap<>();
    private static final Identifier NONE = Identifier.of("steveparty", "none");
    private static VertexConsumerProvider.Immediate buffers;
    /** While a ghost is drawn: its opacity, and whether this is its depth pass (for renderers drawing on their own). */
    private static float drawingAlpha = -1;
    private static boolean depthPass;

    private GhostMobs() {
    }

    public static void initialize() {
        WorldRenderEvents.START.register(context -> FRAME.clear());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(GhostMobs::draw);
    }

    /** Asks for {@code mob} drawn see-through this frame, standing at {@code at} (world), facing {@code yaw}. */
    public static void request(Entity mob, Vec3d at, float yaw, int light, float alpha) {
        if (alpha <= 0.01f || ShaderPacks.renderingShadows()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        boolean behind = client.world != null
                && DeferredGlows.behindTranslucent(client.world, camera, at.add(0, mob.getHeight() / 2, 0));
        FRAME.add(new Ghost(mob, at, yaw, light, MathHelper.clamp(alpha, 0, 1), behind));
    }

    /** The opacity of the ghost being drawn now, or -1 if none (a renderer drawing outside the buffers fades itself). */
    public static float drawingAlpha() {
        return drawingAlpha;
    }

    /** Whether the ghost being drawn is in its depth pass (a renderer drawing outside the buffers draws nothing then). */
    public static boolean isDepthPass() {
        return drawingAlpha >= 0 && depthPass;
    }

    /** Right before the translucent terrain (WorldRendererDeferredGlowsMixin): the ghosts seen through it. */
    public static void drawBeforeTranslucentTerrain() {
        if (FRAME.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        draw(new MatrixStack(), client.gameRenderer.getCamera().getPos(), client.getRenderTickCounter().getTickDelta(false), true);
    }

    private static void draw(WorldRenderContext context) {
        if (FRAME.isEmpty()) return;
        MatrixStack matrices = context.matrixStack();
        float tickDelta = context.tickCounter().getTickDelta(false);
        if (matrices != null) draw(matrices, context.camera().getPos(), tickDelta, false);
        draw(new MatrixStack(), context.camera().getPos(), tickDelta, true); // the hook before the translucent terrain was missed
        FRAME.clear();
    }

    /** Draws the ghosts seen through translucent blocks ({@code behind}) or the others, and forgets them. */
    private static void draw(MatrixStack matrices, Vec3d camera, float tickDelta, boolean behind) {
        if (buffers == null) buffers = VertexConsumerProvider.immediate(new BufferAllocator(RenderLayer.DEFAULT_BUFFER_SIZE));
        MinecraftClient client = MinecraftClient.getInstance();
        for (Ghost ghost : FRAME) {
            if (ghost.behindTranslucent() != behind) continue;
            EntityRenderer<? super Entity> renderer = client.getEntityRenderDispatcher().getRenderer(ghost.mob());
            if (renderer == null) continue;
            Entity mob = ghost.mob();
            mob.setPos(ghost.at().x, ghost.at().y, ghost.at().z);
            mob.setYaw(ghost.yaw());
            mob.prevYaw = ghost.yaw();
            if (mob instanceof LivingEntity living) {
                living.bodyYaw = living.prevBodyYaw = ghost.yaw();
                living.headYaw = living.prevHeadYaw = ghost.yaw();
            }
            for (int pass = 0; pass < 2; pass++) {
                depthPass = pass == 0;
                drawingAlpha = ghost.alpha();
                matrices.push();
                matrices.translate(ghost.at().x - camera.x, ghost.at().y - camera.y, ghost.at().z - camera.z);
                try {
                    renderer.render(mob, ghost.yaw(), tickDelta, matrices, new Faded(buffers, depthPass, ghost.alpha(), !behind), ghost.light());
                } catch (RuntimeException ignored) {
                    // a renderer that needs a mob in the world: no ghost
                } finally {
                    matrices.pop();
                    buffers.draw();
                    drawingAlpha = -1;
                }
            }
        }
        FRAME.removeIf(ghost -> ghost.behindTranslucent() == behind);
    }

    /** The texture {@code layer} draws (from its description: the same with any mapping), or null. */
    private static @Nullable Identifier textureOf(RenderLayer layer) {
        Identifier texture = TEXTURES.computeIfAbsent(layer, l -> {
            Matcher found = TEXTURE.matcher(l.toString());
            return found.find() ? Identifier.tryParse(found.group(1)) : NONE;
        });
        return texture == NONE || texture == null ? null : texture;
    }

    /** Every layer asked for, drawn as the same texture in our depth or colour layer, the colour faded. */
    private record Faded(VertexConsumerProvider.Immediate buffers, boolean depth, float alpha, boolean translucentTarget)
            implements VertexConsumerProvider {
        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            Identifier texture = textureOf(layer);
            if (texture == null) return DISCARD;
            if (depth) return buffers.getBuffer((translucentTarget ? GhostLayers.DEPTH : GhostLayers.DEPTH_MAIN).apply(texture));
            return new AlphaConsumer(buffers.getBuffer((translucentTarget ? GhostLayers.COLOR : GhostLayers.COLOR_MAIN).apply(texture)), alpha);
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

    /** Our two layers, per texture: into the translucent target, the entity shaders (cut out on the texture's alpha). */
    private static final class GhostLayers extends RenderLayer {
        /** Its depth alone, nearest faces only. */
        static final Function<Identifier, RenderLayer> DEPTH = Util.memoize(texture -> of("steveparty_ghost_depth", texture, DEPTH_MASK, true));
        /** Its colour, faded, where its depth is (no depth written). */
        static final Function<Identifier, RenderLayer> COLOR = Util.memoize(texture -> of("steveparty_ghost_color", texture, COLOR_MASK, true));
        /** The same into the main target (seen through translucent blocks, drawn before them). */
        static final Function<Identifier, RenderLayer> DEPTH_MAIN = Util.memoize(texture -> of("steveparty_ghost_depth_main", texture, DEPTH_MASK, false));
        static final Function<Identifier, RenderLayer> COLOR_MAIN = Util.memoize(texture -> of("steveparty_ghost_color_main", texture, COLOR_MASK, false));

        private GhostLayers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling,
                            boolean translucent, Runnable begin, Runnable end) {
            super(name, format, mode, size, crumbling, translucent, begin, end);
        }

        private static RenderLayer of(String name, Identifier texture, RenderPhase.WriteMaskState mask, boolean translucentTarget) {
            return RenderLayer.of(name, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS,
                    1536, true, mask == COLOR_MASK, MultiPhaseParameters.builder()
                            .program(ENTITY_TRANSLUCENT_PROGRAM)
                            .texture(new Texture(texture, false, false))
                            .transparency(TRANSLUCENT_TRANSPARENCY)
                            .cull(DISABLE_CULLING)
                            .lightmap(ENABLE_LIGHTMAP)
                            .overlay(ENABLE_OVERLAY_COLOR)
                            .depthTest(LEQUAL_DEPTH_TEST)
                            .target(translucentTarget ? TRANSLUCENT_TARGET : MAIN_TARGET)
                            .writeMaskState(mask)
                            .build(false));
        }
    }
}
