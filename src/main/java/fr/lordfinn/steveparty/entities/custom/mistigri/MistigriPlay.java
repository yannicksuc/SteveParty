package fr.lordfinn.steveparty.entities.custom.mistigri;

import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleTowers;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity.Action;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity.PlayPose;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The Mistigri at play, an old tom still a kitten at heart. Server side, cheap: he looks around every second or two
 * only, and paths again every half second at most.
 * <ul>
 *     <li>An acorn on the ground (thrown, dropped) within {@link #ACORN_RANGE} blocks distracts him from anything (even
 *     angry, even knocking something off): he runs to it and plays with it like a ball of wool for 30 to 60 s: paw taps
 *     that send it rolling and he chases it, tosses in the air, lying in wait then pouncing on it, rolled on his back
 *     holding it up in his paws. He never takes it: anyone can pick it up, which ends the game.</li>
 *     <li>Cat and mouse with a Glandouille: he stalks it, pounces, it runs away scared and he chases it, playing, a
 *     while; he never hurts it. Never a board's Glandouille (invulnerable, untouchable), nor one in a player's hands,
 *     nor while either of them is the board's.</li>
 * </ul>
 */
public final class MistigriPlay {
    /** How far an acorn catches his eye (blocks). */
    public static final double ACORN_RANGE = 12;
    /** How far he spots a Glandouille to play with (blocks). */
    public static final double PREY_RANGE = 10;
    /** A game with an acorn: 30 to 60 s (ticks). */
    static final int ACORN_GAME_MIN = 600, ACORN_GAME_MAX = 1200;
    /** Cat and mouse: 15 to 25 s (ticks). */
    static final int CHASE_MIN = 300, CHASE_MAX = 500;
    /** After a game, a while without one (ticks). */
    static final int REST_MIN = 600, REST_MAX = 1800;

    private MistigriPlay() {
    }

    /** The nearest acorn lying about (an item entity) within reach of his eye, or null. */
    public static @Nullable ItemEntity findAcorn(ServerWorld world, MistigriEntity mistigri) {
        ItemEntity best = null;
        double bestDistance = ACORN_RANGE * ACORN_RANGE;
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, mistigri.getBoundingBox().expand(ACORN_RANGE, 4, ACORN_RANGE),
                item -> item.isAlive() && item.getStack().isOf(ModItems.ACORN))) {
            double distance = item.squaredDistanceTo(mistigri);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        return best;
    }

    /** A Glandouille he may play cat and mouse with: free, not a board's, not invulnerable, not in anyone's hands. */
    public static boolean isPrey(GlandouilleEntity glandouille) {
        return glandouille.isAlive() && glandouille.isFree() && !glandouille.isInvulnerable() && !glandouille.isBoardActor()
                && !(GlandouilleTowers.bottom(glandouille).getVehicle() instanceof PlayerEntity);
    }

    /** The nearest Glandouille to play with, or null. */
    public static @Nullable GlandouilleEntity findPrey(ServerWorld world, MistigriEntity mistigri) {
        GlandouilleEntity best = null;
        double bestDistance = PREY_RANGE * PREY_RANGE;
        for (GlandouilleEntity glandouille : world.getEntitiesByClass(GlandouilleEntity.class,
                mistigri.getBoundingBox().expand(PREY_RANGE, 3, PREY_RANGE), MistigriPlay::isPrey)) {
            double distance = glandouille.squaredDistanceTo(mistigri);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = glandouille;
            }
        }
        return best;
    }

    /** Free to start a game: not the board's, not told to sit, not on a leash, not asleep on a chest, not swimming. */
    static boolean mayPlay(MistigriEntity mistigri) {
        return mistigri.isAlive() && !mistigri.isBoardActor() && !mistigri.isSitting() && !mistigri.isLeashed()
                && !mistigri.isAsleepOnChest() && !mistigri.isTouchingWater();
    }

    /**
     * Looks around only once every {@code period} ticks, each Mistigri at his own moment (a 2-tick window: the goal
     * selector looks for new goals every other tick).
     */
    private static boolean looksNow(MistigriEntity mistigri, int period) {
        return (mistigri.getWorld().getTime() + mistigri.getId()) % period < 2;
    }

    /**
     * Heads for {@code target}: along a path, or straight at it when no path comes (a wide cat starting against an
     * edge); false when neither (too far, another height: no way to it).
     */
    private static boolean goTo(MistigriEntity mistigri, Entity target, double speed) {
        if (mistigri.getNavigation().startMovingTo(target, speed)) {
            var path = mistigri.getNavigation().getCurrentPath();
            if (path != null && path.getLength() > 1) return true;
        }
        if (mistigri.squaredDistanceTo(target) < 8 * 8 && Math.abs(target.getY() - mistigri.getY()) < 1.0) {
            mistigri.getNavigation().stop();
            mistigri.getMoveControl().moveTo(target.getX(), target.getY(), target.getZ(), speed);
            return true;
        }
        return false;
    }

    private static void rest(MistigriEntity mistigri) {
        mistigri.nextPlayTime = mistigri.getWorld().getTime()
                + MathHelper.nextInt(mistigri.getRandom(), REST_MIN, REST_MAX);
    }

    private static void face(MistigriEntity mistigri, Vec3d at) {
        double dx = at.x - mistigri.getX(), dz = at.z - mistigri.getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        mistigri.setYaw(yaw);
        mistigri.setBodyYaw(yaw);
        mistigri.setHeadYaw(yaw);
    }

    /** A spring at {@code at}: forward and up, at most {@code reach} blocks. */
    private static void spring(MistigriEntity mistigri, Vec3d at, double reach) {
        Vec3d to = at.subtract(mistigri.getPos()).multiply(1, 0, 1);
        double length = Math.min(reach, to.length());
        Vec3d dir = to.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : to.normalize().multiply(length * 0.22);
        mistigri.setVelocity(dir.x, 0.36, dir.z);
        mistigri.velocityDirty = true;
    }

    // ---------------------------------------------------------------- the acorn

    /** What he does with the acorn just now. */
    enum Game {
        RUN, CHASE, BAT, TOSS, STALK, POUNCE, ON_BACK
    }

    /** Plays with an acorn lying about (see the class). */
    public static final class PlayWithAcorn extends Goal {
        private final MistigriEntity mistigri;
        private @Nullable ItemEntity acorn;
        private Game game = Game.RUN;
        private int ticks, length, step, stepLength, stuck;
        private double lastDistance = Double.MAX_VALUE;

        public PlayWithAcorn(MistigriEntity mistigri) {
            this.mistigri = mistigri;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            if (!looksNow(mistigri, 20) || !mayPlay(mistigri)) return false;
            if (!(mistigri.getWorld() instanceof ServerWorld world) || world.getTime() < mistigri.nextPlayTime) return false;
            acorn = findAcorn(world, mistigri);
            return acorn != null;
        }

        @Override
        public boolean shouldContinue() {
            return acorn != null && acorn.isAlive() && ticks < length && stuck < 4 && mayPlay(mistigri)
                    && acorn.squaredDistanceTo(mistigri) < 16 * 16;
        }

        @Override
        public void start() {
            ticks = 0;
            stuck = 0;
            length = MathHelper.nextInt(mistigri.getRandom(), ACORN_GAME_MIN, ACORN_GAME_MAX);
            mistigri.distract();
            mistigri.setPlaying(true);
            to(Game.RUN);
        }

        @Override
        public void stop() {
            // played out, or no way to it: a while before the next game; the acorn picked up: free at once
            boolean playedOut = ticks >= length || stuck >= 4;
            if (acorn != null && acorn.isAlive() && game == Game.ON_BACK) release();
            mistigri.setPlaying(false);
            mistigri.getNavigation().stop();
            acorn = null;
            if (playedOut) rest(mistigri);
        }

        private void to(Game next) {
            game = next;
            step = 0;
            stepLength = 0;
            lastDistance = Double.MAX_VALUE;
            mistigri.setPlayPose(PlayPose.NONE);
            if (acorn == null) return;
            switch (next) {
                case RUN, CHASE -> goTo(mistigri, acorn, next == Game.RUN ? 1.5 : 1.3);
                case BAT -> {
                    face(mistigri, acorn.getPos());
                    mistigri.act(Action.BAT);
                    stepLength = Action.BAT.ticks;
                }
                case TOSS -> {
                    face(mistigri, acorn.getPos());
                    mistigri.act(Action.TOSS);
                    stepLength = Action.TOSS.ticks + 30; // then he watches it land
                }
                case STALK -> {
                    mistigri.getNavigation().stop();
                    mistigri.setPlayPose(PlayPose.STALK);
                    stepLength = MathHelper.nextInt(mistigri.getRandom(), 30, 60);
                }
                case POUNCE -> {
                    face(mistigri, acorn.getPos());
                    mistigri.act(Action.POUNCE);
                    stepLength = Action.POUNCE.ticks;
                }
                case ON_BACK -> {
                    mistigri.getNavigation().stop();
                    mistigri.setPlayPose(PlayPose.ON_BACK);
                    stepLength = MathHelper.nextInt(mistigri.getRandom(), 60, 100);
                }
            }
        }

        /** The next little game, near the acorn: a paw tap (and a chase), a toss, a pounce, or on his back with it. */
        private void next() {
            if (acorn == null) return;
            if (acorn.squaredDistanceTo(mistigri) > 2.2 * 2.2) {
                to(Game.CHASE);
                return;
            }
            int roll = mistigri.getRandom().nextInt(8);
            if (roll < 3) to(Game.BAT);
            else if (roll < 5) to(Game.TOSS);
            else if (roll < 7) to(Game.STALK);
            else to(acorn.isOnGround() ? Game.ON_BACK : Game.BAT);
        }

        /** Lets go of the acorn he held up in his paws: it drops by his side with a little flick. */
        private void release() {
            if (acorn == null) return;
            acorn.setVelocity((mistigri.getRandom().nextDouble() - 0.5) * 0.2, 0.25, (mistigri.getRandom().nextDouble() - 0.5) * 0.2);
            acorn.velocityDirty = true;
        }

        private void push(double horizontal, double up, double spread) {
            if (acorn == null) return;
            Vec3d dir = acorn.getPos().subtract(mistigri.getPos()).multiply(1, 0, 1);
            if (dir.lengthSquared() < 1.0E-4) dir = Vec3d.fromPolar(0, mistigri.getYaw());
            double angle = (mistigri.getRandom().nextDouble() - 0.5) * spread;
            dir = dir.normalize().rotateY((float) angle);
            acorn.setVelocity(dir.x * horizontal, up, dir.z * horizontal);
            acorn.velocityDirty = true;
        }

        @Override
        public void tick() {
            ticks++;
            step++;
            if (acorn == null) return;
            if (game != Game.ON_BACK) mistigri.getLookControl().lookAt(acorn.getX(), acorn.getY(), acorn.getZ());
            double distance = acorn.squaredDistanceTo(mistigri);
            switch (game) {
                case RUN, CHASE -> {
                    if (distance < 1.6 * 1.6 && acorn.isOnGround()) {
                        stuck = 0;
                        next();
                    } else if (step % 10 == 0) {
                        // no way to it, or no nearer for a while (a wall, out of reach): he soon gives up
                        boolean going = goTo(mistigri, acorn, game == Game.RUN ? 1.5 : 1.3);
                        if (!going || Math.sqrt(distance) > Math.sqrt(lastDistance) - 0.3) stuck++;
                        else stuck = 0;
                        lastDistance = distance;
                    }
                    if (game == Game.CHASE && step > 120) next(); // lost it: something else
                }
                case BAT -> {
                    if (step == 8) push(0.32, 0.12, 1.2); // the paw lands: it rolls away
                    if (step >= stepLength) to(Game.CHASE);
                }
                case TOSS -> {
                    if (step == 11) push(0.08, 0.5, 2.0); // up it goes
                    if (step > Action.TOSS.ticks && acorn.isOnGround()) to(Game.STALK);
                    else if (step >= stepLength) next();
                }
                case STALK -> {
                    if (distance > 3.5 * 3.5 && step % 10 == 1) goTo(mistigri, acorn, 0.45);
                    else if (distance <= 3.5 * 3.5) mistigri.getNavigation().stop();
                    if (step >= stepLength) to(Game.POUNCE);
                }
                case POUNCE -> {
                    if (step == 14) spring(mistigri, acorn.getPos(), 3.0); // the spring, mid-animation
                    if (step == 22) push(0.18, 0.2, 3.0);                 // landed on it: it skitters off
                    if (step >= stepLength) to(Game.CHASE);
                }
                case ON_BACK -> {
                    // held up in his paws, over his belly
                    Vec3d paws = mistigri.getPos().add(0, 1.05, 0);
                    acorn.setPosition(paws.x, paws.y, paws.z);
                    acorn.setVelocity(Vec3d.ZERO);
                    acorn.velocityDirty = true;
                    if (step >= stepLength) {
                        release();
                        to(Game.CHASE);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- cat and mouse

    /** Cat and mouse with a Glandouille (see the class). */
    public static final class CatAndMouse extends Goal {
        private final MistigriEntity mistigri;
        private @Nullable GlandouilleEntity prey;
        private boolean chasing;
        private int ticks, length, still, pounceAt, lastPounce;

        public CatAndMouse(MistigriEntity mistigri) {
            this.mistigri = mistigri;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        @Override
        public boolean canStart() {
            if (!looksNow(mistigri, 40) || !mistigri.isFree()) return false;
            if (!(mistigri.getWorld() instanceof ServerWorld world) || world.getTime() < mistigri.nextPlayTime) return false;
            if (mistigri.getRandom().nextInt(3) != 0) return false;
            prey = findPrey(world, mistigri);
            return prey != null;
        }

        @Override
        public boolean shouldContinue() {
            return prey != null && isPrey(prey) && ticks < length && mayPlay(mistigri) && !mistigri.isAngry()
                    && prey.squaredDistanceTo(mistigri) < 20 * 20;
        }

        @Override
        public void start() {
            ticks = 0;
            still = 0;
            pounceAt = 0;
            lastPounce = -100;
            chasing = false;
            length = MathHelper.nextInt(mistigri.getRandom(), CHASE_MIN, CHASE_MAX);
            mistigri.setPlaying(true);
            mistigri.setLoafing(false);
            mistigri.setStaring(false);
            mistigri.setPlayPose(PlayPose.STALK);
        }

        @Override
        public void stop() {
            mistigri.setPlaying(false);
            mistigri.getNavigation().stop();
            prey = null;
            rest(mistigri);
        }

        /** The current prey (tests). */
        public @Nullable Entity prey() {
            return prey;
        }

        private void pounce() {
            if (prey == null) return;
            mistigri.setPlayPose(PlayPose.NONE);
            face(mistigri, prey.getPos());
            mistigri.act(Action.POUNCE);
            pounceAt = ticks;
            lastPounce = ticks;
        }

        @Override
        public void tick() {
            ticks++;
            if (prey == null) return;
            mistigri.getLookControl().lookAt(prey, 30f, 30f);
            double distance = prey.squaredDistanceTo(mistigri);
            if (pounceAt > 0 && ticks - pounceAt == 14) {
                spring(mistigri, prey.getPos(), 3.0);
                prey.scare(mistigri, 120); // it bolts, he chases it
                chasing = true;
            }
            if (pounceAt > 0 && ticks - pounceAt < Action.POUNCE.ticks) return;
            pounceAt = 0;
            if (!chasing) {
                // lying in wait: creeping up to a pounce's length, then still a moment
                if (distance > 3.5 * 3.5) {
                    if (ticks % 10 == 1) goTo(mistigri, prey, 0.45);
                } else {
                    mistigri.getNavigation().stop();
                    if (++still > 40) pounce();
                }
                return;
            }
            // the chase: after it at a trot, a playful pounce whenever close
            if (ticks % 20 == 0) prey.scare(mistigri, 120);
            if (distance < 2.5 * 2.5 && ticks - lastPounce > 50 && mistigri.isOnGround()) pounce();
            else if (ticks % 10 == 0) goTo(mistigri, prey, 1.25);
        }
    }
}
