package fr.lordfinn.steveparty.client.flip;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-side, per-entity storage of the goal-pole flip animation.
 * <p>
 * The progress is advanced once per entity tick (FPS independent) and interpolated with tickDelta
 * when rendering. Two independent tracks exist: one for the entity model (follows
 * {@code LivingEntityRenderer.shouldFlipUpsideDown}) and one for the camera roll (goal pole only).
 * Only accessed from the render thread.
 */
public final class GoalPoleFlipTracker {
    /** Fraction of the remaining distance covered each tick (~0.1 per frame at 60 FPS). */
    private static final float SPEED_PER_TICK = 0.27F;
    private static final int MAX_CATCH_UP_TICKS = 20;

    private static final Map<LivingEntity, Progress> MODEL = new WeakHashMap<>();
    private static final Map<LivingEntity, Progress> CAMERA = new WeakHashMap<>();

    private GoalPoleFlipTracker() {}

    public static boolean isOnGoalPole(LivingEntity entity) {
        if (!(entity instanceof PlayerEntity player) || player.isClimbing()) return false;
        World world = player.getWorld();
        BlockPos under = player.getBlockPos().down();
        return world.getBlockState(under).isOf(ModBlocks.GOAL_POLE)
                || world.getBlockState(under.down()).isOf(ModBlocks.GOAL_POLE);
    }

    /** Flip progress for the entity model, in [0, 1]. */
    public static float getModelProgress(LivingEntity entity, boolean flipped, float tickDelta) {
        return MODEL.computeIfAbsent(entity, e -> new Progress()).get(entity.age, flipped, tickDelta);
    }

    /** Camera roll progress for the given (camera) entity, in [0, 1]. */
    public static float getCameraProgress(LivingEntity entity, float tickDelta) {
        return CAMERA.computeIfAbsent(entity, e -> new Progress()).get(entity.age, isOnGoalPole(entity), tickDelta);
    }

    public static void clear() {
        MODEL.clear();
        CAMERA.clear();
    }

    private static final class Progress {
        private float prev;
        private float current;
        private int lastAge = Integer.MIN_VALUE;

        float get(int age, boolean flipped, float tickDelta) {
            float target = flipped ? 1.0F : 0.0F;
            if (lastAge == Integer.MIN_VALUE || age < lastAge) {
                lastAge = age;
            }
            int steps = Math.min(age - lastAge, MAX_CATCH_UP_TICKS);
            for (int i = 0; i < steps; i++) {
                prev = current;
                current += (target - current) * SPEED_PER_TICK;
                if (Math.abs(target - current) < 0.001F) current = target;
            }
            lastAge = age;
            return MathHelper.lerp(tickDelta, prev, current);
        }
    }
}
