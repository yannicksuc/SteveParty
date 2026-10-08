package fr.lordfinn.steveparty.entities.custom.glandouille;

import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/** The Glandouille's own goals (the bottom one of a tower only: the others ride, their AI off). */
final class GlandouilleGoals {
    private GlandouilleGoals() {
    }

    /** Without its cap it is shy: it keeps away from players. */
    static final class FleeWithoutHat extends FleeEntityGoal<PlayerEntity> {
        private final GlandouilleEntity glandouille;

        FleeWithoutHat(GlandouilleEntity glandouille) {
            super(glandouille, PlayerEntity.class, 8.0f, 1.0, 1.3);
            this.glandouille = glandouille;
        }

        @Override
        public boolean canStart() {
            return !glandouille.hasHat() && glandouille.isFree() && super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return !glandouille.hasHat() && glandouille.isFree() && super.shouldContinue();
        }
    }

    /** Without its cap, it looks for one on the ground (an Acorn Hat item) and puts it back on. */
    static final class SeekHat extends Goal {
        private static final double RANGE = 16;
        private final GlandouilleEntity glandouille;
        private @Nullable ItemEntity hat;
        private int ticks;

        SeekHat(GlandouilleEntity glandouille) {
            this.glandouille = glandouille;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (glandouille.hasHat() || !glandouille.isFree() || glandouille.getRandom().nextInt(10) != 0) return false;
            List<ItemEntity> hats = glandouille.getWorld().getEntitiesByClass(ItemEntity.class,
                    glandouille.getBoundingBox().expand(RANGE), item -> item.getStack().isOf(ModItems.ACORN_HAT));
            hat = null;
            double best = Double.MAX_VALUE;
            for (ItemEntity item : hats) {
                double d = item.squaredDistanceTo(glandouille);
                if (d < best) {
                    best = d;
                    hat = item;
                }
            }
            return hat != null;
        }

        @Override
        public void start() {
            ticks = 0;
            if (hat != null) glandouille.getNavigation().startMovingTo(hat, 1.2);
        }

        @Override
        public boolean shouldContinue() {
            return hat != null && hat.isAlive() && !glandouille.hasHat() && glandouille.isFree() && ticks < 300;
        }

        @Override
        public void tick() {
            ticks++;
            if (hat == null) return;
            glandouille.getLookControl().lookAt(hat);
            // not while it still flies off its head (its pickup delay): it is within reach all along
            if (!hat.cannotPickup() && glandouille.squaredDistanceTo(hat) < 1.6 * 1.6) {
                hat.getStack().decrement(1);
                if (hat.getStack().isEmpty()) hat.discard();
                glandouille.putHatOn();
                hat = null;
            } else if (ticks % 20 == 0) {
                glandouille.getNavigation().startMovingTo(hat, 1.2);
            }
        }

        @Override
        public void stop() {
            hat = null;
            glandouille.getNavigation().stop();
        }
    }

    /**
     * At night, in the rain, or now and then: it walks to the nearest leaves at ground level (leaves standing on the
     * ground or at its feet; else the foot of a tree) and naps there, one eye half open.
     */
    static final class Nap extends Goal {
        private static final int RANGE = 8;
        private final GlandouilleEntity glandouille;
        private @Nullable BlockPos spot;
        private int ticks;

        Nap(GlandouilleEntity glandouille) {
            this.glandouille = glandouille;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!glandouille.canNap() || !glandouille.hasHat()) return false;
            World world = glandouille.getWorld();
            boolean drowsy = world.isNight() || world.isRaining();
            if (glandouille.getRandom().nextInt(drowsy ? 200 : 4000) != 0) return false;
            spot = findSpot();
            return true;
        }

        private @Nullable BlockPos findSpot() {
            World world = glandouille.getWorld();
            BlockPos feet = glandouille.getBlockPos();
            BlockPos best = null, log = null;
            double bestD = Double.MAX_VALUE, logD = Double.MAX_VALUE;
            BlockPos.Mutable at = new BlockPos.Mutable();
            for (int dx = -RANGE; dx <= RANGE; dx++) {
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        at.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                        double d = at.getSquaredDistance(feet);
                        if (world.getBlockState(at).isIn(BlockTags.LEAVES)) {
                            if (d < bestD) {
                                bestD = d;
                                best = at.toImmutable();
                            }
                        } else if (log == null || d < logD) {
                            if (world.getBlockState(at).isIn(BlockTags.LOGS)) {
                                logD = d;
                                log = at.toImmutable();
                            }
                        }
                    }
                }
            }
            return best != null ? best : log;
        }

        @Override
        public void start() {
            ticks = 0;
            if (spot != null) glandouille.getNavigation().startMovingTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.8);
        }

        @Override
        public boolean shouldContinue() {
            return glandouille.isFree() && ticks < 200 && glandouille.getMood() == GlandouilleEntity.Mood.CALM;
        }

        @Override
        public void tick() {
            ticks++;
            boolean there = spot == null || glandouille.getBlockPos().getSquaredDistance(spot) < 2.5 * 2.5
                    || (ticks > 20 && glandouille.getNavigation().isIdle());
            if (there) glandouille.fallAsleep(600 + glandouille.getRandom().nextInt(900));
        }

        @Override
        public void stop() {
            spot = null;
        }
    }

    /** Two calm Glandouilles meeting: one may climb on the other (a tower of at most {@link GlandouilleTowers#SPONTANEOUS_MAX}). */
    static final class Climb extends Goal {
        private final GlandouilleEntity glandouille;
        private @Nullable GlandouilleEntity target;
        private int ticks;

        Climb(GlandouilleEntity glandouille) {
            this.glandouille = glandouille;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!glandouille.isFree() || !glandouille.hasHat() || GlandouilleTowers.hasRider(glandouille)
                    || glandouille.getRandom().nextInt(80) != 0) return false;
            target = null;
            double best = Double.MAX_VALUE;
            for (GlandouilleEntity other : glandouille.getWorld().getEntitiesByClass(GlandouilleEntity.class,
                    glandouille.getBoundingBox().expand(5), other -> other != glandouille)) {
                GlandouilleEntity bottom = GlandouilleTowers.bottom(other);
                if (bottom.getVehicle() != null || bottom.isBoardActor() || bottom.getMood() != GlandouilleEntity.Mood.CALM
                        || GlandouilleTowers.height(bottom) >= GlandouilleTowers.SPONTANEOUS_MAX) continue;
                double d = bottom.squaredDistanceTo(glandouille);
                if (d < best) {
                    best = d;
                    target = bottom;
                }
            }
            return target != null;
        }

        @Override
        public void start() {
            ticks = 0;
            if (target != null) glandouille.getNavigation().startMovingTo(target, 1.0);
        }

        @Override
        public boolean shouldContinue() {
            return target != null && target.isAlive() && glandouille.isFree() && ticks < 100;
        }

        @Override
        public void tick() {
            ticks++;
            if (target == null) return;
            glandouille.getLookControl().lookAt(target);
            double dx = target.getX() - glandouille.getX(), dz = target.getZ() - glandouille.getZ();
            if (dx * dx + dz * dz < 1.1 * 1.1) {
                GlandouilleTowers.climb(glandouille, target, true);
                target = null;
            } else if (ticks % 20 == 0) {
                glandouille.getNavigation().startMovingTo(target, 1.0);
            }
        }

        @Override
        public void stop() {
            target = null;
        }
    }
}
