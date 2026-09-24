package fr.lordfinn.steveparty.entities.custom.goals;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.minecraft.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A tamed Mula floats after its owner, 2 blocks above them, like a Luma trailing behind Mario: gently when close,
 * faster the farther it lags (it still catches up with a sprinting player), and teleports beyond the follow range as
 * before. Within {@value #DIRECT_RANGE} blocks it floats straight to its place above the owner's head (the move control
 * steers and slows it); farther, it follows a path recomputed every {@value #REPATH_TICKS} ticks (it was every tick).
 */
public class FollowOwnerWhileFlyingGoal extends Goal {
    /** Ticks between two path computations (vanilla pets use the same). */
    private static final int REPATH_TICKS = 10;
    /** Flight speed (blocks/tick, times the goal's speed): at the stop distance, and at most. */
    private static final double MIN_SPEED = 0.12, MAX_SPEED = 0.55;
    /** Extra speed per block of distance beyond the stop distance. */
    private static final double SPEED_PER_BLOCK = 0.05;
    /** A dancing Mula whose owner is closer than this (blocks) keeps dancing. */
    private static final double DANCE_WITH_OWNER = 14;
    /** Closer than this (blocks), it flies straight to its place instead of following a path. */
    private static final double DIRECT_RANGE = 8;

    private final MulaEntity entity;
    private PlayerEntity owner;
    private final double speed;
    private final float maxDistance;
    private final float minDistance;
    private int repathTicks;

    public FollowOwnerWhileFlyingGoal(MulaEntity entity, double speed, float minDistance, float maxDistance) {
        this.entity = entity;
        this.speed = speed;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!entity.isTamed() || entity.cannotFollowOwner() || !(entity.getOwner() instanceof PlayerEntity player)
                || player.isSpectator()) {
            return false;
        }
        this.owner = player;
        // dancing round a Dice Forge with its owner close by: it keeps dancing
        if (entity.isDancing() && entity.squaredDistanceTo(owner) < DANCE_WITH_OWNER * DANCE_WITH_OWNER) return false;
        return !(entity.squaredDistanceTo(owner) < (double)(minDistance * minDistance));
    }

    @Override
    public boolean shouldContinue() {
        return owner != null && owner.isAlive() && !owner.isSpectator() && !entity.cannotFollowOwner()
                && entity.squaredDistanceTo(owner) > (double)(minDistance * minDistance);
    }

    @Override
    public void start() {
        repathTicks = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        entity.getNavigation().stop();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (owner == null) return;

        // Look at the owner
        entity.getLookControl().lookAt(owner, 10.0f, 10.0f);

        // Too far to path (beyond the follow range): catch up like vanilla pets do
        double distanceSq = entity.squaredDistanceTo(owner);
        if (distanceSq > (double)(maxDistance * maxDistance)) {
            entity.tryTeleportToOwner();
            repathTicks = 0;
            return;
        }
        boolean close = distanceSq < DIRECT_RANGE * DIRECT_RANGE;
        if (!close && --repathTicks > 0 && !entity.getNavigation().isIdle()) return;
        repathTicks = REPATH_TICKS;

        // Target 2 blocks above player
        double targetX = owner.getX();
        double targetY = owner.getY() + 2.0;
        double targetZ = owner.getZ();

        if (close) {
            // close by: floats straight to its place above the owner's head (no path: path nodes sit on the floor,
            // which made it creep along the ground), the move control steering round and slowing into it
            entity.getNavigation().stop();
            entity.getMoveControl().moveTo(targetX, targetY, targetZ, flightSpeed(distanceSq));
            return;
        }

        BlockPos pos = entity.getBlockPos().down();
        while (entity.getWorld().isAir(pos) && pos.getY() > entity.getWorld().getBottomY()) {
            pos = pos.down();
        }
        double groundY = pos.getY() + 1.0;
        targetY = Math.max(groundY + 1.0, Math.min(targetY, groundY + 5.0));

        // Move the entity using navigation (MoveControl handles velocity)
        entity.getNavigation().startMovingTo(targetX, targetY, targetZ, flightSpeed(distanceSq));
    }

    /** Gently when close, faster the farther it lags. */
    private double flightSpeed(double distanceSq) {
        double distance = Math.sqrt(distanceSq);
        return speed * MathHelper.clamp(MIN_SPEED + (distance - minDistance) * SPEED_PER_BLOCK, MIN_SPEED, MAX_SPEED);
    }
}
