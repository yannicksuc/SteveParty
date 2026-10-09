package fr.lordfinn.steveparty.entities.custom.fumarole;

import fr.lordfinn.steveparty.sounds.ModSounds;
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
                    player -> !fumarole.isTamed() && !fumarole.hasPassenger(player)
                            && !(player instanceof PlayerEntity p && fumarole.trustedByAll(p))
                            && player.squaredDistanceTo(fumarole) <= FumaroleEntity.TERRITORY * FumaroleEntity.TERRITORY);
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
        public boolean canStart() {
            LivingEntity attacker = fumarole.getAttacker();
            if (attacker instanceof PlayerEntity player && (fumarole.isOwner(player) || fumarole.trustedByAll(player))) return false;
            return !fumarole.isSteered() && super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return !calm(fumarole, fumarole.getTarget()) && super.shouldContinue();
        }
    }

    /**
     * One blast at a time, the heads taking turns: the next head in turn that can reach an enemy turns to it, a second
     * of warning (its vent charging, a hiss, its spit animation drawing the neck back), then the steam leaves as the
     * neck whips forward (FumaroleEntity#blast, a bucket). The heads share out the enemies about (its target first,
     * whoever last hurt it, players in its territory: head n prefers the n-th), with one enemy they all focus it. Then
     * one shared rest of {@link #COOLDOWN_MIN} to {@link #COOLDOWN_MIN} + {@link #COOLDOWN_SPREAD} ticks: three heads
     * shoot no more often than one would.
     */
    static final class Blast extends Goal {
        /** A head's warning second, then its vent spitting this long. */
        static final int CHARGE_TICKS = 20, SPIT_TICKS = 12;
        /** A head turns to its target this many ticks before its warning. */
        static final int TURN_TICKS = 10;
        static final int COOLDOWN_MIN = 80, COOLDOWN_SPREAD = 40;
        private final FumaroleEntity fumarole;
        private final LivingEntity[] targets = new LivingEntity[FumaroleEntity.HEADS.length];
        /** When each head fires in this volley (ticks from its start), -1: it sits this one out. */
        private final int[] fireAt = new int[FumaroleEntity.HEADS.length];
        private int ticks, end;
        /** The head whose turn comes next. */
        private int nextHead;
        private long nextVolley;

        Blast(FumaroleEntity fumarole) {
            this.fumarole = fumarole;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        /** The enemies about, its target first. */
        private List<LivingEntity> enemies() {
            List<LivingEntity> enemies = new ArrayList<>();
            LivingEntity target = fumarole.getTarget();
            if (target != null && target.isAlive()) enemies.add(target);
            LivingEntity attacker = fumarole.getAttacker();
            if (attacker != null && attacker.isAlive() && !enemies.contains(attacker) && !(attacker instanceof FumaroleEntity)
                    && !fumarole.hasPassenger(attacker)) {
                enemies.add(attacker);
            }
            for (PlayerEntity player : fumarole.getWorld().getPlayers()) {
                if (enemies.size() >= FumaroleEntity.HEADS.length) break;
                if (!fumarole.isTamed() && !enemies.contains(player) && player.isAlive() && !player.isSpectator() && !player.isCreative()
                        && player.squaredDistanceTo(fumarole) <= FumaroleEntity.TERRITORY * FumaroleEntity.TERRITORY) {
                    enemies.add(player);
                }
            }
            return enemies;
        }

        /** Picks the next head in turn that can reach an enemy (its own preferably); false if none can. */
        private boolean plan() {
            List<LivingEntity> enemies = enemies();
            java.util.Arrays.fill(fireAt, -1);
            java.util.Arrays.fill(targets, null);
            if (enemies.isEmpty()) return false;
            for (int k = 0; k < fireAt.length; k++) {
                int head = (nextHead + k) % fireAt.length;
                for (int j = 0; j < enemies.size(); j++) {
                    LivingEntity enemy = enemies.get((head + j) % enemies.size());
                    if (fumarole.mayShoot(head, enemy) && fumarole.getVisibilityCache().canSee(enemy) && fumarole.inRange(head, enemy)) {
                        targets[head] = enemy;
                        fireAt[head] = TURN_TICKS + CHARGE_TICKS;
                        end = fireAt[head] + SPIT_TICKS;
                        nextHead = (head + 1) % fireAt.length;
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean canStart() {
            LivingEntity target = fumarole.getTarget();
            if (fumarole.hasPassengers()) return false;
            if (target == null || !target.isAlive() || fumarole.getWorld().getTime() < nextVolley) return false;
            return plan();
        }

        @Override
        public boolean shouldContinue() {
            return ticks < end;
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void start() {
            ticks = 0;
            fumarole.getNavigation().stop();
        }

        @Override
        public void tick() {
            ticks++;
            LivingEntity primary = fumarole.getTarget();
            if (primary != null) fumarole.getLookControl().lookAt(primary, fumarole.getMaxLookYawChange(), 40);
            for (int head = 0; head < fireAt.length; head++) {
                LivingEntity target = targets[head];
                if (fireAt[head] < 0 || target == null) continue;
                int charge = fireAt[head] - CHARGE_TICKS;
                if (!target.isAlive() && ticks < fireAt[head]) { // gone before its turn: this head rests
                    fireAt[head] = -1;
                    fumarole.setVent(head, FumaroleEntity.VENT_IDLE);
                    fumarole.setHeadTarget(head, null);
                    continue;
                }
                if (ticks >= charge - TURN_TICKS && ticks <= fireAt[head]) fumarole.aimHead(head, target, false);
                if (ticks == charge - TURN_TICKS) fumarole.setHeadTarget(head, target);
                if (ticks == charge) {
                    fumarole.setVent(head, FumaroleEntity.VENT_CHARGING);
                    fumarole.playSound(ModSounds.FUMAROLE_CHARGE, 2.0f, 0.9f + 0.1f * head);
                    fumarole.playSpit(head);
                }
                if (ticks == fireAt[head]) {
                    if (fumarole.inRange(head, target)) {
                        fumarole.setVent(head, FumaroleEntity.VENT_SPITTING);
                        fumarole.blast(head, target);
                    } else {
                        fumarole.setVent(head, FumaroleEntity.VENT_IDLE);
                    }
                }
                if (ticks == fireAt[head] + SPIT_TICKS) fumarole.setVent(head, FumaroleEntity.VENT_IDLE);
            }
        }

        @Override
        public void stop() {
            for (int head = 0; head < fireAt.length; head++) {
                fumarole.setVent(head, FumaroleEntity.VENT_IDLE);
                fumarole.setHeadTarget(head, null);
                fumarole.restHead(head);
                targets[head] = null;
            }
            nextVolley = fumarole.getWorld().getTime() + COOLDOWN_MIN + fumarole.getRandom().nextInt(COOLDOWN_SPREAD + 1);
        }
    }

    /** Turns its body toward the target (slowly: its body follows its look), its centre head watching it. */
    static void aim(FumaroleEntity fumarole, LivingEntity target) {
        fumarole.getLookControl().lookAt(target, fumarole.getMaxLookYawChange(), 40);
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
            return !fumarole.hasPassengers() && fumarole.getTank() < FumaroleEntity.TANK_MAX
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
            return target != null && target.isAlive() && !fumarole.hasPassengers()
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
            fumarole.setHeadTarget(0, target);
            if (--repath <= 0) {
                repath = 20;
                fumarole.getNavigation().startMovingTo(target, 1.0);
            }
        }

        @Override
        public void stop() {
            fumarole.getNavigation().stop();
            fumarole.setHeadTarget(0, null);
        }
    }
}
