package fr.lordfinn.steveparty.client.flag;

import fr.lordfinn.steveparty.utils.Easing;
import net.minecraft.util.math.MathHelper;

/**
 * Where a goal pole flag is drawn along its pole: at its place, or slid down to the bottom when the goal is met.
 * <p>
 * One instance per flag, render thread only. Every frame it gets the wanted drop (in pixels, 0 or negative); when it
 * changes, a new move starts from wherever the flag is drawn at that moment, so an interrupted slide never jumps.
 * Going down: accelerating like a fall, then a little bounce at the bottom. Going back up: slower, eased in and out.
 */
public final class FlagSlide {
    private static final float DOWN_BASE = 0.45f, DOWN_PER_PIXEL = 0.022f, DOWN_MAX = 1.8f;
    private static final float UP_BASE = 0.9f, UP_PER_PIXEL = 0.035f, UP_MAX = 3.0f;
    /** Share of a slide down spent falling; the rest is the bounce. */
    private static final float FALL = 0.78f;

    private boolean initialized;
    private boolean met;
    private float from, to;
    private double start;
    private float duration = 1f;
    private boolean down;

    /**
     * @param wanted       where the flag should end up (pixels, 0 = its place)
     * @param met          whether the goal is met
     * @param changeSecond when the goal last changed on the server (seconds of world time), to start in sync
     * @param stagger      per-flag delay, seconds
     * @param now          seconds of world time
     * @return true when a slide starts because the goal changed (for the sound)
     */
    public boolean update(float wanted, boolean met, double changeSecond, float stagger, double now) {
        if (!initialized) {
            // First frame (chunk loaded, joined): straight to the right place, no slide
            initialized = true;
            this.met = met;
            from = to = wanted;
            start = Double.NEGATIVE_INFINITY;
            return false;
        }
        if (wanted == to) return false;
        boolean flipped = met != this.met;
        this.met = met;
        from = offset(now);
        to = wanted;
        down = to < from;
        float distance = Math.abs(to - from);
        duration = down ? Math.min(DOWN_MAX, DOWN_BASE + DOWN_PER_PIXEL * distance)
                : Math.min(UP_MAX, UP_BASE + UP_PER_PIXEL * distance);
        // The goal changed: start from the server's moment (every player sees it together), each flag a bit apart.
        // A flag below that moved (a flag resting on it follows) starts now.
        double wantedStart = flipped ? changeSecond + stagger : now;
        start = wantedStart > now + 1 || wantedStart < now - duration - 1 ? now : wantedStart;
        return flipped && now - start < 0.5;
    }

    /** @return the drop to draw the flag at (pixels, 0 or negative). */
    public float offset(double now) {
        double u = (now - start) / duration;
        if (u <= 0) return from;
        if (u >= 1) return to;
        float t = (float) u;
        if (!down) return MathHelper.lerp(Easing.smoothstep(t), from, to);
        if (t < FALL) {
            float fall = t / FALL;
            return MathHelper.lerp(fall * fall, from, to);
        }
        // A little hop back up at the bottom
        float bounce = (t - FALL) / (1f - FALL);
        float height = Math.min(2f, 0.12f * Math.abs(to - from));
        return to + height * MathHelper.sin(bounce * MathHelper.PI);
    }

    public boolean isDown() {
        return down;
    }
}
