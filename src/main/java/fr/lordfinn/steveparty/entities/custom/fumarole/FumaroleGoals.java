package fr.lordfinn.steveparty.entities.custom.fumarole;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** The Fumarole's AI: its territory, its revenge, the turret blast, pumping lava, walking to its target. */
public final class FumaroleGoals {
    private FumaroleGoals() {
    }

    /** Whether it has calmed down about {@code target}: no longer angry, they keep out of its territory. */
    static boolean calm(FumaroleEntity fumarole, @Nullable LivingEntity target) {
        return target != null && !fumarole.isAngry()
                && fumarole.squaredDistanceTo(target) > FumaroleEntity.TERRITORY * FumaroleEntity.TERRITORY;
    }

    /** A player coming within {@link FumaroleEntity#TERRITORY} blocks becomes its target, until it calms down. */
    static final class Territory extends ActiveTargetGoal<PlayerEntity> {
        private final FumaroleEntity fumarole;

        Territory(FumaroleEntity fumarole) {
            super(fumarole, PlayerEntity.class, 10, true, false,
                    player -> player.squaredDistanceTo(fumarole) <= FumaroleEntity.TERRITORY * FumaroleEntity.TERRITORY);
            this.fumarole = fumarole;
        }

        @Override
        public boolean shouldContinue() {
            return !calm(fumarole, fumarole.getTarget()) && super.shouldContinue();
        }
    }

    /** Whoever hurts it (a player or a mob) becomes its target, until it calms down. */
    static final class Revenge extends RevengeGoal {
        private final FumaroleEntity fumarole;

        Revenge(FumaroleEntity fumarole) {
            super(fumarole, FumaroleEntity.class);
            this.fumarole = fumarole;
        }

        @Override
        public boolean shouldContinue() {
            return !calm(fumarole, fumarole.getTarget()) && super.shouldContinue();
        }
    }

    /**
     * The turret blast: its neck aims at its target, a second of warning (vent charging, a hiss, the spit animation
     * drawing the neck back), then the steam leaves as the neck whips forward (FumaroleEntity#blast). Then a rest of
     * {@link #COOLDOWN_MIN} to {@link #COOLDOWN_MIN} + {@link #COOLDOWN_SPREAD} ticks.
     */
    static final class Blast extends Goal {
        /** The warning second, then the spitting vent this long. */
        static final int CHARGE_TICKS = 20, SPIT_TICKS = 12;
        static final int COOLDOWN_MIN = 80, COOLDOWN_SPREAD = 40;
        private final FumaroleEntity fumarole;
        private @Nullable LivingEntity target;
        private int ticks;
        private long nextShot;

        Blast(FumaroleEntity fumarole) {
            this.fumarole = fumarole;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            LivingEntity candidate = fumarole.getTarget();
            if (candidate == null || !candidate.isAlive() || fumarole.getWorld().getTime() < nextShot) return false;
            if (!fumarole.getVisibilityCache().canSee(candidate) || !fumarole.inRange(candidate)) return false;
            target = candidate;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            if (ticks >= CHARGE_TICKS + SPIT_TICKS) return false;
            return ticks >= CHARGE_TICKS || (target != null && target.isAlive() && fumarole.getTarget() == target);
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void start() {
            ticks = 0;
            fumarole.getNavigation().stop();
            fumarole.setVent(FumaroleEntity.VENT_CHARGING);
            fumarole.playSound(fr.lordfinn.steveparty.sounds.ModSounds.FUMAROLE_CHARGE, 2.0f, 1.0f);
            fumarole.playSpit();
        }

        @Override
        public void tick() {
            if (target != null) aim(fumarole, target);
            ticks++;
            if (ticks == CHARGE_TICKS && target != null) {
                fumarole.setVent(FumaroleEntity.VENT_SPITTING);
                fumarole.blast(target);
            }
        }

        @Override
        public void stop() {
            fumarole.setVent(FumaroleEntity.VENT_IDLE);
            nextShot = fumarole.getWorld().getTime() + COOLDOWN_MIN + fumarole.getRandom().nextInt(COOLDOWN_SPREAD + 1);
            target = null;
        }
    }

    /** Turns its neck (head yaw and pitch) so that the nozzle, not its eyes, points at the target. */
    static void aim(FumaroleEntity fumarole, LivingEntity target) {
        Vec3d look = fumarole.getEyePos().add(FumaroleEntity.aimPoint(target).subtract(fumarole.nozzle()));
        fumarole.getLookControl().lookAt(look.x, look.y, look.z, fumarole.getMaxLookYawChange(), 40);
    }

    /**
     * Pumping: when its tank isn't full (and it has no target, or an empty tank), it looks for a lava source it may
     * drink ({@link FumaroleEntity#canPump}) within {@link #SEARCH} blocks, walks until the source is within its
     * nozzle's reach, turns to face it, dips (the pump animation) and drinks it at the gulp.
     */
    static final class Pump extends Goal {
        static final int SEARCH = 16, SEARCH_DOWN = 6, SEARCH_UP = 1, SEARCH_EVERY = 40, CANDIDATES = 8;
        /** The pump animation's length, the gulp in it, and how long it tries to reach a source. */
        static final int PUMP_TICKS = 40, GULP_TICK = 28, WALK_LIMIT = 400;
        private static final float TURN = 4, FACING = 15;
        private final FumaroleEntity fumarole;
        private @Nullable BlockPos source;
        private int searchCooldown, walkTicks, ticks;
        private boolean dipping, done;

        Pump(FumaroleEntity fumarole) {
            this.fumarole = fumarole;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        private boolean wants() {
            return fumarole.getTank() < FumaroleEntity.TANK_MAX
                    && (fumarole.getTarget() == null || fumarole.getTank() == 0)
                    && fumarole.pumping.rateAllows(fumarole.getWorld().getTime());
        }

        @Override
        public boolean canStart() {
            if (!wants() || --searchCooldown > 0) return false;
            searchCooldown = SEARCH_EVERY;
            source = find(fumarole);
            return source != null;
        }

        @Override
        public boolean shouldContinue() {
            if (done || source == null) return false;
            if (dipping) return true; // finishes its gulp (the pump itself checks the rules again)
            return wants() && FumarolePumping.isSource(fumarole.getWorld(), source) && walkTicks < WALK_LIMIT;
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void start() {
            walkTicks = 0;
            ticks = 0;
            dipping = false;
            done = false;
        }

        @Override
        public void tick() {
            if (source == null) return;
            Vec3d center = Vec3d.ofCenter(source);
            if (dipping) {
                ticks++;
                if (ticks == GULP_TICK && !fumarole.pump(source)) done = true;
                if (ticks >= PUMP_TICKS) done = true;
                return;
            }
            walkTicks++;
            if (fumarole.canReach(source)) {
                fumarole.getNavigation().stop();
                fumarole.getLookControl().lookAt(center.x, center.y, center.z);
                float wanted = (float) (MathHelper.atan2(center.z - fumarole.getZ(), center.x - fumarole.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90;
                float diff = MathHelper.wrapDegrees(wanted - fumarole.getYaw());
                float yaw = fumarole.getYaw() + MathHelper.clamp(diff, -TURN, TURN);
                fumarole.setYaw(yaw);
                fumarole.setBodyYaw(yaw);
                if (Math.abs(diff) < FACING) {
                    dipping = true;
                    ticks = 0;
                    fumarole.setPumping(true);
                }
            } else if (walkTicks % 20 == 1) {
                fumarole.getNavigation().startMovingTo(center.x, center.y, center.z, 1.0);
            }
        }

        @Override
        public void stop() {
            fumarole.setPumping(false);
            fumarole.getNavigation().stop();
            source = null;
            dipping = false;
        }

        /** The nearest source it may drink around it, or null. */
        static @Nullable BlockPos find(FumaroleEntity fumarole) {
            World world = fumarole.getWorld();
            BlockPos at = fumarole.getBlockPos();
            List<BlockPos> sources = new ArrayList<>();
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                    for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
                        pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                        if (FumarolePumping.isSource(world, pos)) sources.add(pos.toImmutable());
                    }
                }
            }
            sources.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(at)));
            int checked = 0;
            for (BlockPos candidate : sources) {
                if (fumarole.canPump(candidate)) return candidate;
                if (++checked >= CANDIDATES) break;
            }
            return null;
        }
    }

    /** With a target out of reach or out of sight, it trudges toward them. */
    static final class Approach extends Goal {
        private final FumaroleEntity fumarole;
        private int repath;

        Approach(FumaroleEntity fumarole) {
            this.fumarole = fumarole;
            setControls(EnumSet.of(Control.MOVE));
        }

        private boolean needed() {
            LivingEntity target = fumarole.getTarget();
            return target != null && target.isAlive()
                    && (!fumarole.inRange(target) || !fumarole.getVisibilityCache().canSee(target));
        }

        @Override
        public boolean canStart() {
            return needed();
        }

        @Override
        public boolean shouldContinue() {
            return needed();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = fumarole.getTarget();
            if (target == null) return;
            aim(fumarole, target);
            if (--repath <= 0) {
                repath = 20;
                fumarole.getNavigation().startMovingTo(target, 1.0);
            }
        }

        @Override
        public void stop() {
            fumarole.getNavigation().stop();
        }
    }
}
