package fr.lordfinn.steveparty.entities.custom.goals;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.minecraft.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A tamed Mula floats after its owner, 2 blocks above them, like a little star trailing behind its owner: gently when close,
 * faster the farther it lags (it still catches up with a sprinting player), and teleports beyond the follow range as
 * before. Within {@value #DIRECT_RANGE} blocks it floats straight to its place above the owner's head (the move control
 * steers and slows it); farther, it flies straight too while nothing stands between them (one ray every
 * {@value #REPATH_TICKS} ticks), and only follows a path round what is in the way, recomputed every
 * {@value #REPATH_TICKS} ticks and only when its place moved by more than {@value #REPATH_MOVED} block (a path search
 * for every follower of a running player was most of the Mulas' server time); stuck on a path, it flies straight
 * until the next look. A teleport that failed is tried again
 * {@value #REPATH_TICKS} ticks later, like vanilla pets (it was every tick).
 * <p>
 * At most {@code mulaMaxFollowers} Mulas follow one player (MulaEscorts); the others stay where they are.
 * <p>
 * Several Mulas following the same player keep {@value #SPACING} blocks apart: each one's place above the owner is
 * pushed away from its nearest mates, and Mulas resting too close to one another drift apart.
 */
public class FollowOwnerWhileFlyingGoal extends Goal {
    /** Ticks between two path computations (vanilla pets use the same). */
    private static final int REPATH_TICKS = 10;
    /** A path is recomputed when its goal moved farther than this (blocks), or when it ended. */
    private static final double REPATH_MOVED = 1.0;
    /** Deepest the ground is looked for under it (blocks), for the height of a path's goal. */
    private static final int GROUND_SCAN = 24;
    /** A Mula refused by a full escort asks again after this many ticks. */
    private static final int ESCORT_RETRY_TICKS = 20;
    /** Flight speed (blocks/tick, times the goal's speed): at the stop distance, and at most. */
    private static final double MIN_SPEED = 0.12, MAX_SPEED = 0.55;
    /** Extra speed per block of distance beyond the stop distance. */
    private static final double SPEED_PER_BLOCK = 0.05;
    /** A Mula at home by a forge (or dancing) whose owner is closer than this (blocks) stays there. */
    private static final double DANCE_WITH_OWNER = 14;
    /** Closer than this (blocks), it flies straight to its place instead of following a path. */
    private static final double DIRECT_RANGE = 8;
    /** Mulas keep at least this far apart (blocks, centre to centre). */
    private static final double SPACING = 1.3;

    private final MulaEntity entity;
    private PlayerEntity owner;
    private final double speed;
    private final float maxDistance;
    private final float minDistance;
    private int repathTicks;
    /** Goal of the current path (NaN when none), and where the Mula was at the last look. */
    private double pathX = Double.NaN, pathY, pathZ, lastLookX, lastLookY, lastLookZ;
    /** Moved less than this (blocks) since the last look while on a path: stuck, it flies straight for a while. */
    private static final double STUCK = 0.5;
    private int escortRetryTicks;

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
        // a full escort: it stays where it is (asks again now and then)
        if (escortRetryTicks > 0) {
            escortRetryTicks--;
            return false;
        }
        if (entity.getWorld() instanceof net.minecraft.server.world.ServerWorld world
                && !fr.lordfinn.steveparty.entities.custom.MulaEscorts.join(world, player.getUuid(), entity)) {
            escortRetryTicks = ESCORT_RETRY_TICKS;
            return false;
        }
        // at home by a Dice Forge (dancing or not) with its owner close by: it stays; it follows them if they leave
        if ((entity.isDancing() || entity.homeForge() != null)
                && entity.squaredDistanceTo(owner) < DANCE_WITH_OWNER * DANCE_WITH_OWNER) return false;
        return !(entity.squaredDistanceTo(owner) < (double)(minDistance * minDistance)) || crowding() != null;
    }

    @Override
    public boolean shouldContinue() {
        return owner != null && owner.isAlive() && !owner.isSpectator() && !entity.cannotFollowOwner()
                && (entity.squaredDistanceTo(owner) > (double)(minDistance * minDistance) || crowding() != null);
    }

    /** The first of its nearest Mulas (refreshed by its brain) closer than {@link #SPACING}, or null. */
    private MulaEntity crowding() {
        MulaBrain brain = entity.getMulaBrain();
        for (int i = 0; i < brain.neighbourCount(); i++) {
            MulaEntity other = brain.neighbour(i);
            if (other != null && other != entity && other.isAlive() && other.squaredDistanceTo(entity) < SPACING * SPACING) return other;
        }
        return null;
    }

    /**
     * {@code [x, y, z]} pushed sideways out of the {@link #SPACING} of its nearest Mulas (in place). Two Mulas exactly
     * on top of each other part along a direction of their own (from the entity id).
     */
    private void spreadOut(double[] target) {
        MulaBrain brain = entity.getMulaBrain();
        for (int i = 0; i < brain.neighbourCount(); i++) {
            MulaEntity other = brain.neighbour(i);
            if (other == null || other == entity || !other.isAlive()) continue;
            double dx = target[0] - other.getX(), dz = target[2] - other.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance >= SPACING || Math.abs(target[1] - other.getY()) >= SPACING) continue;
            if (distance < 1.0E-3) {
                double angle = entity.getId() * 2.399963; // golden angle: neighbours' ids part different ways
                dx = Math.cos(angle);
                dz = Math.sin(angle);
                distance = 1;
            }
            target[0] += dx / distance * (SPACING - distance);
            target[2] += dz / distance * (SPACING - distance);
        }
    }

    @Override
    public void start() {
        repathTicks = 0;
        direct = false;
        pathX = Double.NaN;
        entity.setLedByOwner(true);
    }

    @Override
    public void stop() {
        entity.setLedByOwner(false);
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

        // Already close to the owner: only drifting out of another Mula
        if (entity.squaredDistanceTo(owner) <= (double)(minDistance * minDistance)) {
            entity.getNavigation().stop();
            double[] place = {entity.getX(), entity.getY(), entity.getZ()};
            spreadOut(place);
            entity.getMoveControl().moveTo(place[0], place[1], place[2], speed * MIN_SPEED);
            return;
        }

        // Too far to path (beyond the follow range): catch up like vanilla pets do
        double distanceSq = entity.squaredDistanceTo(owner);
        if (distanceSq > (double)(maxDistance * maxDistance)) {
            // (a failed try waits: an owner in unloaded or unsafe places made every follower try every tick)
            if (--repathTicks > 0) return;
            repathTicks = REPATH_TICKS;
            entity.tryTeleportToOwner();
            if (entity.squaredDistanceTo(owner) <= (double)(maxDistance * maxDistance)) repathTicks = 0;
            pathX = Double.NaN;
            return;
        }
        boolean close = distanceSq < DIRECT_RANGE * DIRECT_RANGE;
        // time to look again (a ray, maybe a path): every REPATH_TICKS ticks, or when its path ended
        boolean look = !close && (--repathTicks <= 0
                || (!direct && entity.getNavigation().isIdle() && repathTicks < REPATH_TICKS - 2));
        if (!close && !look && !direct) return; // following its path
        if (look) repathTicks = REPATH_TICKS;

        // Target 2 blocks above player, out of the way of the other Mulas
        double[] place = {owner.getX(), owner.getY() + 2.0, owner.getZ()};
        spreadOut(place);
        double targetX = place[0];
        double targetY = place[1];
        double targetZ = place[2];

        if (look) {
            // stuck on its path since the last look (a path through the air that leads nowhere): straight on, the move
            // control sliding along what is in the way, until the next look
            boolean stuck = !Double.isNaN(pathX) && entity.squaredDistanceTo(lastLookX, lastLookY, lastLookZ) < STUCK * STUCK;
            lastLookX = entity.getX();
            lastLookY = entity.getY();
            lastLookZ = entity.getZ();
            direct = stuck || inSight(targetX, targetY, targetZ);
        }
        if (close || direct) {
            // close by, or nothing in the way: floats straight to its place above the owner's head (no path: path
            // nodes sit on the floor, which made it creep along the ground), the move control steering round and
            // slowing into it
            if (close) direct = false;
            entity.getNavigation().stop();
            if (look || close) pathX = Double.NaN;
            entity.getMoveControl().moveTo(targetX, targetY, targetZ, flightSpeed(distanceSq));
            return;
        }

        BlockPos.Mutable pos = entity.getBlockPos().mutableCopy().move(0, -1, 0);
        int bottom = Math.max(entity.getWorld().getBottomY(), pos.getY() - GROUND_SCAN);
        while (entity.getWorld().isAir(pos) && pos.getY() > bottom) {
            pos.move(0, -1, 0);
        }
        double groundY = pos.getY() + 1.0;
        targetY = Math.max(groundY + 1.0, Math.min(targetY, groundY + 5.0));

        // the same goal as the path it follows: no new search
        if (!entity.getNavigation().isIdle() && !Double.isNaN(pathX)
                && sq(targetX - pathX) + sq(targetY - pathY) + sq(targetZ - pathZ) < REPATH_MOVED * REPATH_MOVED) return;
        pathX = targetX;
        pathY = targetY;
        pathZ = targetZ;
        // Move the entity using navigation (MoveControl handles velocity)
        entity.getNavigation().startMovingTo(targetX, targetY, targetZ, flightSpeed(distanceSq));
    }

    /** Beyond the direct range, nothing was in the way at the last look: it flies straight. */
    private boolean direct;

    /** Nothing solid between its body and that place (one ray). */
    private boolean inSight(double x, double y, double z) {
        net.minecraft.util.math.Vec3d from = entity.getPos().add(0, entity.getHeight() * 0.5, 0);
        return entity.getWorld().raycast(new net.minecraft.world.RaycastContext(from, new net.minecraft.util.math.Vec3d(x, y, z),
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE,
                entity)).getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Gently when close, faster the farther it lags. */
    private double flightSpeed(double distanceSq) {
        double distance = Math.sqrt(distanceSq);
        return speed * MathHelper.clamp(MIN_SPEED + (distance - minDistance) * SPEED_PER_BLOCK, MIN_SPEED, MAX_SPEED);
    }
}
