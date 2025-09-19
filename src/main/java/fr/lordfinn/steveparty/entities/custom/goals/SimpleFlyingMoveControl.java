package fr.lordfinn.steveparty.entities.custom.goals;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.mob.MobEntity;

public class SimpleFlyingMoveControl extends MoveControl {

    private final float maxTurn;

    public SimpleFlyingMoveControl(MobEntity entity, float maxTurn) {
        super(entity);
        this.maxTurn = maxTurn;
    }

    @Override
    public void tick() {
        if (this.state == MoveControl.State.MOVE_TO) {
            double dx = targetX - entity.getX();
            double dy = targetY - entity.getY();
            double dz = targetZ - entity.getZ();
            double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

            if (distance < 0.01) {
                this.state = MoveControl.State.WAIT;
                entity.setVelocity(entity.getVelocity().multiply(0.5, 0.5, 0.5));
                return;
            }

            // Normalize direction
            dx /= distance;
            dy /= distance;
            dz /= distance;

            // Apply movement
            double motionX = dx * speed;
            double motionY = dy * speed;
            double motionZ = dz * speed;

            entity.setVelocity(motionX, motionY, motionZ);

            // Optional: rotate entity smoothly toward target
            float targetYaw = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
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

