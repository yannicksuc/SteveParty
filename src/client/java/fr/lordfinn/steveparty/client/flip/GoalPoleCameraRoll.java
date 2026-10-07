package fr.lordfinn.steveparty.client.flip;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * The view of a player standing on a goal pole turns upside down (playtest of 2026-10-06, #74): not at once, it rolls
 * over in {@link #TICKS} ticks, eased at both ends, with a short zoom out half way; and back the same way once off the
 * pole. Client side, the local player only; advanced once per player tick (FPS independent), interpolated with the
 * frame's tick delta. Applied to the view by {@code GameRendererGoalPoleRollMixin} (the roll) and
 * {@code GameRendererFovMixin} (the zoom).
 */
public final class GoalPoleCameraRoll {
    /** Ticks the roll takes, one way. */
    public static final int TICKS = 12;
    /** How much wider the view gets half way through the roll. */
    private static final float ZOOM_OUT = 0.12F;

    private static float prev, current;
    private static int lastAge = Integer.MIN_VALUE;

    private GoalPoleCameraRoll() {}

    /** Progress of the roll at this frame, 0 (upright) to 1 (upside down), not eased. */
    private static float progress(float tickDelta) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            prev = current = 0;
            lastAge = Integer.MIN_VALUE;
            return 0;
        }
        float target = GoalPoleFlipTracker.isOnGoalPole(player) ? 1 : 0;
        int age = player.age;
        if (lastAge == Integer.MIN_VALUE || age < lastAge) lastAge = age;
        int steps = Math.min(age - lastAge, TICKS);
        for (int i = 0; i < steps; i++) {
            prev = current;
            current = target > current ? Math.min(target, current + 1F / TICKS) : Math.max(target, current - 1F / TICKS);
        }
        lastAge = age;
        return MathHelper.clamp(MathHelper.lerp(tickDelta, prev, current), 0, 1);
    }

    /** Smoothstep: slow start, slow end. */
    private static float eased(float t) {
        return t * t * (3 - 2 * t);
    }

    /** The roll of the view, degrees (0 upright, 180 upside down). */
    public static float rollDegrees(float tickDelta) {
        return 180F * eased(progress(tickDelta));
    }

    /** The factor the field of view is multiplied by: a little wider half way through the roll. */
    public static float fovFactor(float tickDelta) {
        float t = progress(tickDelta);
        return 1F + ZOOM_OUT * MathHelper.sin(MathHelper.PI * t) * MathHelper.sin(MathHelper.PI * t);
    }

    public static void clear() {
        prev = current = 0;
        lastAge = Integer.MIN_VALUE;
    }
}
