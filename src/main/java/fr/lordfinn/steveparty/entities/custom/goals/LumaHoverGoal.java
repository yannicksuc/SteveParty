package fr.lordfinn.steveparty.entities.custom.goals;

import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.Random;

public class LumaHoverGoal extends Goal {
    private final MulaEntity entity;
    private final double speed;
    private final double minHeight;
    private final double maxHeight;
    private Vec3d target;
    private int changeCooldown = 0;
    private final Random random = new Random();

    public LumaHoverGoal(MulaEntity entity, double speed, double minHeight, double maxHeight) {
        this.entity = entity;
        this.speed = speed;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        // Idle hovering when untamed or has no owner
        return !entity.isTamed() || entity.getOwner() == null;
    }

    @Override
    public boolean shouldContinue() {
        return true;
    }

    @Override
    public void tick() {
        // Only pick a new target occasionally
        if (target == null || changeCooldown-- <= 0) {
            pickNewTarget();
            changeCooldown = 40 + random.nextInt(40); // change every 2–4 seconds (20 ticks = 1 sec)
        }

        // Smooth movement toward target
        Vec3d direction = target.subtract(entity.getPos());
        if (direction.length() > 0.01) {
            Vec3d velocity = direction.normalize().multiply(speed * 0.15); // small incremental motion
            entity.setVelocity(entity.getVelocity().multiply(0.95).add(velocity)); // slight damping
        }

        Vec3d motion = entity.getVelocity();
        if (motion.lengthSquared() > 0.001) {
            float targetYaw = (float)(Math.atan2(motion.z, motion.x) * 180.0 / Math.PI) - 90.0f;
            entity.setYaw(smoothYaw(entity.getYaw(), targetYaw, 5.0f)); // max 5° per tick rotation
        }
    }

    private float smoothYaw(float current, float target, float maxTurn) {
        float delta = target - current;
        while (delta < -180.0F) delta += 360.0F;
        while (delta >= 180.0F) delta -= 360.0F;
        if (delta > maxTurn) delta = maxTurn;
        if (delta < -maxTurn) delta = -maxTurn;
        return current + delta;
    }

    private void pickNewTarget() {
        double targetX = entity.getX() + (random.nextDouble() - 0.5) * 6; // drift left/right
        double targetZ = entity.getZ() + (random.nextDouble() - 0.5) * 6;

        // Clamp height above ground
        BlockPos pos = entity.getBlockPos().down();
        while (entity.getWorld().isAir(pos) && pos.getY() > entity.getWorld().getBottomY()) {
            pos = pos.down();
        }
        double groundY = pos.getY() + 1.0;
        double targetY = groundY + minHeight + random.nextDouble() * (maxHeight - minHeight);

        target = new Vec3d(targetX, targetY, targetZ);
    }
}
