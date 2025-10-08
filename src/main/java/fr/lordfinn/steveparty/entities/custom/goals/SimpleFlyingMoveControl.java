package fr.lordfinn.steveparty.entities.custom.goals;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class SimpleFlyingMoveControl extends MoveControl {

    private final float maxTurn;

    public SimpleFlyingMoveControl(MobEntity entity, float maxTurn) {
        super(entity);
        this.maxTurn = maxTurn;
    }
    @Override
    public void tick() {
        if (this.state == MoveControl.State.WAIT) {
            entity.setVelocity(entity.getVelocity().multiply(0.5));
            if (entity.getVelocity().length() < 0.01)
                entity.setVelocity(Vec3d.ZERO);
        }
        if (this.state == MoveControl.State.MOVE_TO) {
            double dx = targetX - entity.getX();
            double dy = targetY - entity.getY();
            double dz = targetZ - entity.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance < 0.2) {
                this.state = MoveControl.State.WAIT;
                return;
            }

            // Normalize direction
            dx /= distance;
            dy /= distance;
            dz /= distance;

            // Predict next position
            double nextX = entity.getX() + dx * speed;
            double nextY = entity.getY() + dy * speed;
            double nextZ = entity.getZ() + dz * speed;
            BlockPos nextBlockpos =new BlockPos((int) nextX, (int) nextY, (int) nextZ);
            BlockState nextBlockstate = entity.getWorld().getBlockState(nextBlockpos);
            if (!nextBlockstate.canPathfindThrough(NavigationType.AIR)) {
                dx = dz = 0;
                if (entity.getVelocity().y < 0) dy = 0.1; // gently push up if falling into block
            }

            // Apply movement
            entity.setVelocity(dx * speed, dy * speed, dz * speed);

            // Rotate smoothly toward target
            float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            entity.setYaw(this.wrapDegrees(entity.getYaw(), targetYaw, maxTurn));
        }
    }

    @Override
    protected float wrapDegrees(float current, float target, float maxTurn) {
        float f = target - current;
        while (f < -180.0F) f += 360.0F;
        while (f >= 180.0F) f -= 360.0F;
        if (f > maxTurn) f = maxTurn;
        if (f < -maxTurn) f = -maxTurn;
        return current + f;
    }
}

