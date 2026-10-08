package fr.lordfinn.steveparty.entities.custom.goals;

import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Floaty flight: the velocity eases towards the wanted one (limited acceleration) instead of jumping to full speed,
 * slows down when arriving at its last target instead of stopping dead, and the yaw eases towards the direction of
 * flight. Curves come naturally from the limited acceleration when the target moves (soft arcs).
 * <p>
 * The velocity is read back each tick (undoing the air drag that {@code LivingEntity#travel} applied after the last
 * move), so what pushed the Mula meanwhile (a hit, a gravity core, a wall) is kept and steered from.
 */
public class SimpleFlyingMoveControl extends MoveControl {
    /** Air drag applied after each move by LivingEntity#travel to a no-gravity mob: horizontal, vertical (on the
     * ground, the horizontal one is multiplied by the block's slipperiness). */
    private static final double DRAG_XZ = 0.91, DRAG_Y = 0.98;
    /** Within this distance of its last target the Mula slows down, never below MIN_ARRIVE_SPEED (blocks/tick). */
    private static final double ARRIVE_RADIUS = 1.5, MIN_ARRIVE_SPEED = 0.03;
    /** Closer than this to the target: arrived. */
    private static final double ARRIVED = 0.2;
    /** Most velocity change in one tick: this share of the wanted speed, at least MIN_ACCEL (blocks/tick^2). */
    private static final double ACCEL_SHARE = 0.1, MIN_ACCEL = 0.02;
    /**
     * Close to its last target the Mula may correct faster (it only has to brake there, which the slow-down keeps
     * gentle): a player or mob standing in it can't push it away from where it hovers or sits.
     */
    private static final double HOLD_ACCEL = 0.1;
    /** Waiting: the velocity left after each tick (about as before: it was halved). */
    private static final double BRAKE = 0.5;
    private static final double STOPPED = 0.003;
    /** Share of the remaining angle turned each tick (the rest of maxTurn caps it). */
    private static final float TURN_SHARE = 0.25f;

    private final float maxTurn;
    /** The target is a point on the way (an arc of FloatHoverGoal): fly through it at full speed. */
    private boolean passThrough;

    public SimpleFlyingMoveControl(MobEntity entity, float maxTurn) {
        super(entity);
        this.maxTurn = maxTurn;
    }

    @Override
    public void moveTo(double x, double y, double z, double speed) {
        super.moveTo(x, y, z, speed);
        this.passThrough = false;
        keepHome();
    }

    /** Like {@link #moveTo}, for a point on the way: no slowing down near it. */
    public void moveThrough(double x, double y, double z, double speed) {
        super.moveTo(x, y, z, speed);
        this.passThrough = true;
        keepHome();
    }

    private final double[] target = new double[3];

    /**
     * A Mula living at a Dice Forge (MulaHome) never flies out of its area, whatever the behaviour asks: the target is
     * brought back inside. Only its owner leading it away may take it out (it is then released).
     */
    private void keepHome() {
        if (!(entity instanceof fr.lordfinn.steveparty.entities.custom.MulaEntity mula)) return;
        BlockPos home = mula.homeForge();
        if (home == null || mula.isLedByOwner() || mula.isLeashed()) return; // led by its owner or on a lead: theirs
        target[0] = targetX;
        target[1] = targetY;
        target[2] = targetZ;
        fr.lordfinn.steveparty.entities.custom.MulaHome.clamp(home, target);
        targetX = target[0];
        targetY = target[1];
        targetZ = target[2];
    }

    /** Where it is flying to (after keepHome), for the tests. */
    public double targetX() { return targetX; }
    public double targetY() { return targetY; }
    public double targetZ() { return targetZ; }

    @Override
    public void tick() {
        Vec3d v = entity.getVelocity();
        // on the ground (e.g. a path node on the floor) travel slows it by the block's slipperiness too
        double dragXZ = entity.isOnGround()
                ? entity.getWorld().getBlockState(entity.getVelocityAffectingPos()).getBlock().getSlipperiness() * DRAG_XZ
                : DRAG_XZ;
        double vx = v.x / dragXZ, vy = v.y / DRAG_Y, vz = v.z / dragXZ;

        if (this.state == MoveControl.State.MOVE_TO) {
            double dx = targetX - entity.getX();
            double dy = targetY - entity.getY();
            double dz = targetZ - entity.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance < ARRIVED) {
                this.state = MoveControl.State.WAIT;
            } else {
                double wanted = speed;
                boolean arriving = !passThrough && distance < ARRIVE_RADIUS && isLastWaypoint();
                if (arriving) {
                    wanted = Math.min(speed, Math.max(MIN_ARRIVE_SPEED, speed * distance / ARRIVE_RADIUS));
                }
                dx /= distance;
                dy /= distance;
                dz /= distance;

                // Blocked just ahead: stop going sideways, gently rise if falling into it
                BlockPos nextBlockpos = BlockPos.ofFloored(entity.getX() + dx * speed, entity.getY() + dy * speed,
                        entity.getZ() + dz * speed);
                BlockState nextBlockstate = entity.getWorld().getBlockState(nextBlockpos);
                if (!nextBlockstate.canPathfindThrough(NavigationType.AIR)) {
                    dx = dz = 0;
                    if (v.y < 0) dy = 0.1;
                }

                double ax = dx * wanted - vx, ay = dy * wanted - vy, az = dz * wanted - vz;
                double change = Math.sqrt(ax * ax + ay * ay + az * az);
                double maxChange = Math.max(arriving ? HOLD_ACCEL : MIN_ACCEL, speed * ACCEL_SHARE);
                if (change > maxChange) {
                    double k = maxChange / change;
                    ax *= k;
                    ay *= k;
                    az *= k;
                }
                vx += ax;
                vy += ay;
                vz += az;
                entity.setVelocity(vx, vy, vz);

                // Face where it flies, easing into turns
                if (vx * vx + vz * vz > 1.0E-4) {
                    float targetYaw = (float) (MathHelper.atan2(vz, vx) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
                    entity.setYaw(this.wrapDegrees(entity.getYaw(), targetYaw, maxTurn));
                }
                return;
            }
        }

        if (this.state == MoveControl.State.WAIT) {
            vx *= BRAKE;
            vy *= BRAKE;
            vz *= BRAKE;
            if (vx * vx + vy * vy + vz * vz < STOPPED * STOPPED) {
                entity.setVelocity(Vec3d.ZERO);
            } else {
                entity.setVelocity(vx, vy, vz);
            }
        }
    }

    /** Heading for the end of its path (or for a lone target): the only point it must slow down at. */
    private boolean isLastWaypoint() {
        Path path = entity.getNavigation().getCurrentPath();
        return path == null || path.isFinished() || path.getCurrentNodeIndex() >= path.getLength() - 1;
    }

    @Override
    protected float wrapDegrees(float current, float target, float maxTurn) {
        float f = MathHelper.wrapDegrees(target - current) * TURN_SHARE;
        return current + MathHelper.clamp(f, -maxTurn, maxTurn);
    }
}
