package fr.lordfinn.steveparty.entities.custom.goals;

import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.Random;

/**
 * Idle wandering of a Mula without an owner: it rests (hovering in place, the float being visual), then drifts to a
 * new spot a few blocks away along a soft arc (a quadratic curve bulging sideways and a little upwards, like a small
 * hop through the air) instead of a straight line, and rests again. Same targets, speed and pauses as before.
 * Cheap: no pathfinding, one point of the curve computed per tick while travelling.
 */
public class LumaHoverGoal extends Goal {
    /** How far ahead on the curve the Mula aims (blocks), and the step the aim moves by along it. */
    private static final double LOOKAHEAD = 1.0, CURVE_STEP = 0.05;
    /** A trip that takes longer than this (blocked) is given up. */
    private static final int MAX_TRAVEL_TICKS = 200;
    /** Rest between two trips: 80 to 380 ticks (this goal now runs every tick; it counted every other tick before). */
    private static final int REST_MIN_TICKS = 80, REST_RANDOM_TICKS = 300;

    private final MulaEntity entity;
    private final double speed;
    private final double minHeight;
    private final double maxHeight;
    private final Random random = new Random();
    private Vec3d start, control, target;
    private double progress;
    /** Point of the curve the Mula aims at (kept in fields: nothing allocated per tick). */
    private double aimX, aimY, aimZ;
    private int travelTicks;
    private int changeCooldown;

    public LumaHoverGoal(MulaEntity entity, double speed, double minHeight, double maxHeight) {
        this.entity = entity;
        this.speed = speed;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.setControls(EnumSet.of(Control.MOVE));
        changeCooldown = REST_MIN_TICKS + random.nextInt(REST_RANDOM_TICKS);
    }

    @Override
    public boolean canStart() {
        // Idle hovering when untamed or has no owner (e.g. offline), unless ordered to sit
        return !entity.isTamed() || (entity.getOwner() == null && !entity.isSitting());
    }

    @Override
    public boolean shouldContinue() {
        // Yield as soon as the entity has an owner, so FollowOwnerWhileFlyingGoal can run
        return canStart();
    }

    @Override
    public void stop() {
        target = null;
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null) {
            if (changeCooldown-- > 0) return;
            pickNewTarget();
        }
        travelTicks++;
        // the aim slides along the curve as the Mula follows it
        aimAt(progress);
        while (progress < 1 && entity.squaredDistanceTo(aimX, aimY, aimZ) < LOOKAHEAD * LOOKAHEAD) {
            progress = Math.min(1, progress + CURVE_STEP);
            aimAt(progress);
        }
        MoveControl move = entity.getMoveControl();
        if (progress < 1) {
            if (move instanceof SimpleFlyingMoveControl flying) {
                flying.moveThrough(aimX, aimY, aimZ, speed);
            } else {
                move.moveTo(aimX, aimY, aimZ, speed);
            }
            return;
        }
        if (entity.squaredDistanceTo(target) > 0.5 * 0.5 && travelTicks < MAX_TRAVEL_TICKS) {
            move.moveTo(target.x, target.y, target.z, speed); // last stretch: slows down into the stop
            return;
        }
        // arrived: rest a while
        target = null;
        changeCooldown = REST_MIN_TICKS + random.nextInt(REST_RANDOM_TICKS);
    }

    private void aimAt(double t) {
        double u = 1 - t;
        aimX = u * u * start.x + 2 * u * t * control.x + t * t * target.x;
        aimY = u * u * start.y + 2 * u * t * control.y + t * t * target.y;
        aimZ = u * u * start.z + 2 * u * t * control.z + t * t * target.z;
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

        start = entity.getPos();
        target = new Vec3d(targetX, targetY, targetZ);
        // bulge: sideways (either side) and a little upwards, proportional to the trip
        double dx = target.x - start.x, dz = target.z - start.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double side = (random.nextDouble() - 0.5) * 0.8 * length;
        double sideX = length > 1.0E-3 ? -dz / length : 0, sideZ = length > 1.0E-3 ? dx / length : 0;
        control = new Vec3d(
                (start.x + target.x) / 2 + sideX * side,
                Math.max(start.y, target.y) + 0.3 + random.nextDouble() * 0.2 * length,
                (start.z + target.z) / 2 + sideZ * side);
        progress = 0;
        travelTicks = 0;
    }
}
