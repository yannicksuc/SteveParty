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
        changeCooldown = 40 + random.nextInt(150);
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
        if (target == null) {
            this.target = entity.getPos();
        }
        if (entity.getMoveControl().isMoving()) return;
        double distance = entity.getPos().distanceTo(target);
        if (distance > 0.5) {
            entity.getMoveControl().moveTo(target.x, target.y, target.z, speed);
        } else if (changeCooldown-- <= 0) {
            pickNewTarget();
            changeCooldown = 40 + random.nextInt(150);
        }
    }
    private void pickNewTarget() {
        double targetX = entity.getX() + (random.nextDouble() - 0.5) * 10;
        double targetZ = entity.getZ() + (random.nextDouble() - 0.5) * 10;

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
