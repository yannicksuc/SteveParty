package fr.lordfinn.steveparty.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

/**
 * The depth of the mod's HUD (party HUD, tool HUD, tile panel): each of its draws a step in front of the one before,
 * the whole of it in front of the vanilla HUD and of other mods' HUD. Stacked by depth, not by drawing order: HUD
 * batching mods (ImmediatelyFast) draw each texture in the order it was first used in the frame, which put a plate
 * drawn later over an icon or a face; and the vanilla HUD draws its layers 200 apart in depth (LayeredDrawer), so the
 * scoreboard sidebar or a tooltip overlay drawn before ours still came out in front of it.
 * <p>
 * Depth restarts at {@link #BASE} every frame (a frame: a new {@link DrawContext}), and nested draws add up right
 * (each draw is placed at its own depth, whatever its enclosing draws already moved).
 */
public final class HudDepth {
    /** Where the mod's HUD starts: in front of the vanilla HUD layers (the sidebar, the chat…) and other mods' HUD. */
    public static final float BASE = 3000;
    /** Between two draws: a shape, a picture, a line of text (its shadow fits in it). */
    private static final float STEP = 1;
    /** Room for an item: drawn about 150 in front of where it is put, its slot text at 200. */
    private static final float ITEM = 250;

    private static DrawContext frame;
    /** The next draw's depth. */
    private static float next;
    /** The depth already applied by the enclosing draws. */
    private static float applied;

    private HudDepth() {
    }

    /** Runs {@code draw} in front of everything the mod's HUD drew before it this frame. */
    public static void onTop(DrawContext context, Runnable draw) {
        at(context, STEP, draw);
    }

    /**
     * Runs a draw holding items (their depth, their count text) in front of everything drawn before it this frame; the
     * mod's HUD draws it nests (a plate) come right in front of it, its items in front of them.
     */
    public static void item(DrawContext context, Runnable draw) {
        at(context, ITEM, draw);
    }

    /**
     * Where the next draw goes; for a screen that draws over a preview of the HUD (its own things at least this far
     * in front).
     */
    public static float top(DrawContext context) {
        start(context);
        return next;
    }

    /** Restarts the depth at {@code base} for what comes next this frame (a screen's preview: under its widgets). */
    public static void restart(DrawContext context, float base) {
        frame = context;
        next = base;
        applied = 0;
    }

    private static void start(DrawContext context) {
        if (context != frame) restart(context, BASE);
    }

    private static void at(DrawContext context, float room, Runnable draw) {
        start(context);
        float depth = next;
        next = depth + STEP; // what it nests comes right in front of it (an item's own depth goes past them)
        float outer = applied;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, depth - outer);
        applied = depth;
        try {
            draw.run();
        } finally {
            applied = outer;
            matrices.pop();
            next = Math.max(next, depth + room);
        }
    }
}
