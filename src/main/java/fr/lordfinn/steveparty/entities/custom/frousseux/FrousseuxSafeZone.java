package fr.lordfinn.steveparty.entities.custom.frousseux;

import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * The Frousseux's safe zone: monsters (zombies, skeletons, spiders, creepers...) don't come within {@link #RADIUS}
 * blocks of a Frousseux, wild or tamed (not the board's): one inside runs out of it, and that goes before its attack
 * (HostileFrousseuxMixin gives every monster this goal). A monster outside may still shoot in; one chasing a player
 * into the zone turns back at its edge.
 * <p>
 * Cheap: each monster looks for a Frousseux around it once every {@link #CHECK_EVERY} ticks (spread over the ticks),
 * a box search of its surroundings.
 */
public final class FrousseuxSafeZone {
    /** The zone's radius (blocks), and how high it reaches up and down. */
    public static final double RADIUS = 8.0, HEIGHT = 5.0;
    /** Ahead of the monsters' attacks (2 to 4 in vanilla), with their swimming (1, another control). */
    public static final int PRIORITY = 1;
    static final int CHECK_EVERY = 6;
    private static final double SPEED = 1.25;

    private FrousseuxSafeZone() {
    }

    /** The nearest Frousseux whose zone {@code mob} is in, or null. */
    public static @Nullable FrousseuxEntity guard(PathAwareEntity mob) {
        List<FrousseuxEntity> near = mob.getWorld().getEntitiesByClass(FrousseuxEntity.class,
                mob.getBoundingBox().expand(RADIUS, HEIGHT, RADIUS), FrousseuxSafeZone::guards);
        FrousseuxEntity nearest = null;
        double best = RADIUS * RADIUS;
        for (FrousseuxEntity frousseux : near) {
            double distance = frousseux.squaredDistanceTo(mob);
            if (distance <= best) {
                best = distance;
                nearest = frousseux;
            }
        }
        return nearest;
    }

    private static boolean guards(FrousseuxEntity frousseux) {
        return frousseux.isAlive() && !frousseux.isBoardActor() && !frousseux.isBlownOut();
    }

    /**
     * A monster in a zone runs out of it; cornered (nowhere farther to run to), it stays where it is. Either way it
     * holds its legs (the MOVE control) while inside, so its attacks, which all need them (melee, bow, a creeper's
     * fuse, a spider's leap), wait until it is out: it never comes closer.
     */
    public static final class Avoid extends Goal {
        private final PathAwareEntity mob;
        private int nextCheck;
        private @Nullable FrousseuxEntity from;

        public Avoid(PathAwareEntity mob) {
            this.mob = mob;
            this.nextCheck = Math.floorMod(mob.getId(), CHECK_EVERY);
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (--nextCheck > 0) return false;
            nextCheck = CHECK_EVERY;
            from = guard(mob);
            return from != null;
        }

        @Override
        public boolean shouldContinue() {
            return from != null && from.isAlive() && mob.squaredDistanceTo(from) < (RADIUS + 1) * (RADIUS + 1);
        }

        @Override
        public void start() {
            nextCheck = CHECK_EVERY;
            runAway();
        }

        @Override
        public void tick() {
            if (--nextCheck > 0) return;
            nextCheck = CHECK_EVERY;
            FrousseuxEntity nearest = guard(mob);
            if (nearest != null) from = nearest;
            if (mob.getNavigation().isIdle()) runAway();
        }

        private void runAway() {
            if (from == null) return;
            Vec3d away = NoPenaltyTargeting.findFrom(mob, 16, 7, from.getPos());
            Path path = away == null || from.squaredDistanceTo(away) <= from.squaredDistanceTo(mob) ? null
                    : mob.getNavigation().findPathTo(away.x, away.y, away.z, 0);
            if (path == null) { // no wandering spot found (spiders...): straight away from it, out of the zone
                Vec3d out = mob.getPos().subtract(from.getPos()).multiply(1, 0, 1);
                if (out.lengthSquared() < 1.0E-4) out = new Vec3d(1, 0, 0);
                out = mob.getPos().add(out.normalize().multiply(RADIUS + 2 - Math.sqrt(mob.squaredDistanceTo(from))));
                path = mob.getNavigation().findPathTo(out.x, out.y, out.z, 1);
            }
            if (path != null) mob.getNavigation().startMovingAlong(path, SPEED);
            else mob.getNavigation().stop();
        }

        @Override
        public void stop() {
            from = null;
            mob.getNavigation().stop();
            nextCheck = 0; // back inside? seen at once
        }
    }
}
