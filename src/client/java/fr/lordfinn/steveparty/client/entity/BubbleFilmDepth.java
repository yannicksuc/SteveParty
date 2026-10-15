package fr.lordfinn.steveparty.client.entity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.jetbrains.annotations.Nullable;

/**
 * The depth the bubbles' film is tested against: the world's depth <b>before</b> the translucent terrain (the opaque
 * terrain, the entities with the bubbles' riders, the block entities), not after it.
 * <p>
 * The film writes no depth and is drawn after the translucent terrain (so that the water and the glass behind it never
 * paint over it). Tested against the depth after the translucent terrain, every bit of it behind water or glass was
 * cut: a bubble half under water lost its lower half, one seen through a window vanished. Tested against the depth
 * before it, it is drawn over the water and the glass wherever nothing solid hides it, so it always shows (in front of
 * them, untinted by them, where it is behind them: a faint film, the lesser evil).
 * <ul>
 *     <li>Fancy / Fast graphics: the translucent terrain writes the main depth. Its depth before is saved in
 *     BEFORE_DEBUG_RENDER (after the entities and the block entities, before the translucent terrain), swapped
 *     in for the film ({@link #begin}), and the depth with the water put back after it ({@link #end}).</li>
 *     <li>Fabulous: the translucent terrain writes the translucent target's depth only (copied from the main one just
 *     before it); the main depth is the depth before it. The film goes into the translucent target, tested against the
 *     main depth swapped in, then the translucent depth is put back and the film written into it (a depth-only pass,
 *     BubbleEntityRenderer), so that the composite puts it in front of what lies behind it in the other targets (the
 *     items, the particles).</li>
 * </ul>
 * Only when a bubble is drawn this frame; nothing is copied otherwise.
 */
public final class BubbleFilmDepth {
    /** The main depth before the translucent terrain (Fancy), and the target's depth kept aside while the film is drawn. */
    private static @Nullable SimpleFramebuffer before, kept;
    private static boolean saved;
    private static @Nullable Framebuffer swapped;

    private BubbleFilmDepth() {
    }

    public static void initialize() {
        WorldRenderEvents.START.register(context -> saved = false);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            if (!BubbleEntityRenderer.hasFilms() || MinecraftClient.isFabulousGraphicsOrBetter()) return;
            // The entities still waiting in their buffers drawn first: a rider's skin (a player's is translucent) left
            // there is drawn after this copy, missing from the depth the film is tested against, and the film's far
            // faces were painted over him (he looked behind the bubble, washed out)
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
            Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
            before = sized(before, main);
            before.copyDepthFrom(main);
            main.beginWrite(false);
            saved = true;
        });
    }

    /**
     * Swaps the depth before the translucent terrain into the film's target (the main one, or the translucent one with
     * the Fabulous graphics), keeping its own aside. False if that depth isn't known (nothing swapped).
     */
    static boolean begin() {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        Framebuffer target, source;
        if (MinecraftClient.isFabulousGraphicsOrBetter()) {
            target = client.worldRenderer.getTranslucentFramebuffer();
            source = main;
        } else {
            target = main;
            source = saved ? before : null;
        }
        if (target == null || source == null) return false;
        kept = sized(kept, target);
        kept.copyDepthFrom(target);
        target.copyDepthFrom(source);
        main.beginWrite(false);
        swapped = target;
        return true;
    }

    /** Puts the target's own depth back. */
    static void end() {
        if (swapped == null || kept == null) return;
        swapped.copyDepthFrom(kept);
        swapped = null;
        MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
    }

    /** {@code buffer}, made (with a depth) or resized to {@code like}'s size. */
    private static SimpleFramebuffer sized(@Nullable SimpleFramebuffer buffer, Framebuffer like) {
        if (buffer == null) return new SimpleFramebuffer(like.textureWidth, like.textureHeight, true, MinecraftClient.IS_SYSTEM_MAC);
        if (buffer.textureWidth != like.textureWidth || buffer.textureHeight != like.textureHeight) {
            buffer.resize(like.textureWidth, like.textureHeight, MinecraftClient.IS_SYSTEM_MAC);
        }
        return buffer;
    }
}
