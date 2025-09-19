package fr.lordfinn.steveparty.entities.custom.goals;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;

import java.util.EnumSet;

public class FollowOwnerWhileFlyingGoal extends Goal {
    private final MulaEntity entity;
    private PlayerEntity owner;
    private final double speed;
    private final float maxDistance;
    private final float minDistance;

    public FollowOwnerWhileFlyingGoal(MulaEntity entity, double speed, float minDistance, float maxDistance) {
        this.entity = entity;
        this.speed = speed;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!entity.isTamed() || entity.getOwner() == null) {
            return false;
        }
        this.owner = (PlayerEntity) entity.getOwner();
        return !(entity.squaredDistanceTo(owner) < (double)(minDistance * minDistance));
    }

    @Override
    public boolean shouldContinue() {
        return owner != null && entity.squaredDistanceTo(owner) > (double)(minDistance * minDistance) && entity.squaredDistanceTo(owner) < (double)(maxDistance * maxDistance);
    }

    @Override
    public void tick() {
        if (owner == null) return;

        // Target 2 blocks above player
        double targetX = owner.getX();
        double targetY = owner.getY() + 2.0;
        double targetZ = owner.getZ();

        BlockPos pos = entity.getBlockPos().down();
        while (entity.getWorld().isAir(pos) && pos.getY() > entity.getWorld().getBottomY()) {
            pos = pos.down();
        }
        double groundY = pos.getY() + 1.0;
        targetY = Math.max(groundY + 1.0, Math.min(targetY, groundY + 5.0));

        // Move the entity using navigation (MoveControl handles velocity)
        entity.getNavigation().startMovingTo(targetX, targetY, targetZ, speed);

        // Look at the owner
        entity.getLookControl().lookAt(owner, 10.0f, 10.0f);
    }
}
