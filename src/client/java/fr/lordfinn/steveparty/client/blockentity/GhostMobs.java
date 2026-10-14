package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.client.entity.DeferredGlows;
import fr.lordfinn.steveparty.client.utils.GhostModels;
import fr.lordfinn.steveparty.client.utils.ShaderPacks;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Whole mobs drawn see-through, like holograms: a Spawn Bubble's resident mob (« always visible ») shown where it will
 * stand while no party runs (SpawnBubbleRenderer). Any renderer works: whatever layer it asks for, the same texture is
 * drawn in a layer of ours, the colour faded to {@link Ghost#alpha} ({@link GhostModels#faded}).
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
                    renderer.render(mob, ghost.yaw(), tickDelta, matrices, GhostModels.faded(buffers, depthPass, ghost.alpha(), !behind), ghost.light());
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
}
