package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class CustomGravityGoal {
    private final MobEntity mob;
    private LivingEntity target;
    private final double initialVelocity;
    private final double gravityConstant;
    private final double maxDistance;
    private final double heightOffset;

    private Vec3d velocity;

    public CustomGravityGoal(MobEntity mob, LivingEntity target, double initialVelocity, double gravityConstant, double maxDistance, double heightOffset) {
        this.mob = mob;
        this.target = target;
        this.initialVelocity = initialVelocity;
        this.gravityConstant = gravityConstant;
        this.maxDistance = maxDistance;
        this.heightOffset = heightOffset;
        this.velocity = Vec3d.ZERO;

        //this.setControls(EnumSet.of(Control.MOVE));
    }

   // @Override
    public boolean canStart() {
        if (mob == null) return false;
        if (target == null) return false;
        Steveparty.LOGGER.info("Can I start: " + String.valueOf(mob.squaredDistanceTo(target) > 1));
        return target != null && mob.squaredDistanceTo(target) > 1; // Ensure the target is valid and not too close
    }

   // @Override
    public void start() {
        Vec3d direction = target.getPos().add(0, heightOffset, 0).subtract(mob.getPos()).normalize();
        this.velocity = direction.multiply(initialVelocity);
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
        Steveparty.LOGGER.info("Target set to: {}", target == null ? "null" : target.getName().getString());
    }

    //@Override
    public void tick() {
        Steveparty.LOGGER.info("TICK");
        if (target == null || !target.isAlive()) {
            mob.setVelocity(Vec3d.ZERO);
            return;
        }
        Steveparty.LOGGER.info("Target: {}", target == null ? "null" : target.getName().getString());

        Vec3d targetPos = target.getPos().add(0, heightOffset, 0);
        Vec3d mobPos = mob.getPos();
        Steveparty.LOGGER.info("Mob position: {}, Target position: {}", mobPos, targetPos);
        double distance = mobPos.distanceTo(targetPos);
        Steveparty.LOGGER.info("Distance: {}", distance);
        if (distance > maxDistance) {
            Steveparty.LOGGER.info("Too far, teleporting");
            // Teleport back to a safe distance near the target
            mob.teleport(targetPos.x, targetPos.y + 1.5, targetPos.z, true);
            velocity = Vec3d.ZERO;
            return;
        }

        Vec3d direction = targetPos.subtract(mobPos).normalize();
        this.velocity = direction.multiply(velocity);

        double factor = Math.log( distance); // Quadratic relationship to increase strength when farther
        Vec3d attraction = targetPos.subtract(mobPos)
                .normalize()
                .multiply(1 / (factor + 1)); // Avoid division by zero
        velocity = velocity.add(attraction);
        velocity = velocity.rotateY((float) ((Math.random() - 0.5) * 0.1));
        while (Math.abs(velocity.getX()) > 2 || Math.abs(velocity.getY()) > 2 || Math.abs(velocity.getZ()) > 2) {
            velocity = velocity.multiply(0.8); // Scale the velocity vector
        }
        Steveparty.LOGGER.info("Velocity: {}", velocity);

        // Update the mob's position and velocity
        mob.setVelocity(velocity);
        mob.velocityModified = true;

        // Slow down as it approaches the target
        /*if (distance < 2) {
            Steveparty.LOGGER.info("Too close, slowing down");
            velocity = velocity.multiply(0.9);
            if (distance < 0.5) {
                Steveparty.LOGGER.info("Too close, merging");
                // Merge with the target
                mob.teleport(targetPos.x, targetPos.y, targetPos.z, false);
                mob.setVelocity(Vec3d.ZERO);
                stop();
            }
        }*/
    }

    //@Override
    public boolean shouldContinue() {
        return target != null && target.isAlive() && mob.squaredDistanceTo(target) > 1;
    }

    //@Override
    public void stop() {
        mob.setVelocity(Vec3d.ZERO);
    }
}
