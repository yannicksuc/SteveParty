package fr.lordfinn.steveparty.entities.custom;

import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** AI goals of the Hiding Trader. */
public final class HidingTraderGoals {
    private HidingTraderGoals() {
    }

    /**
     * Looks at the nearest player he cares about: never while hidden, never at spectators nor at players wearing a
     * Bandana (he doesn't care about them, see {@link HidingTraderEntity#isAttentionTarget}).
     */
    public static class LookAtAttentionTargetGoal extends LookAtEntityGoal {
        private final HidingTraderEntity trader;

        public LookAtAttentionTargetGoal(HidingTraderEntity trader, float range) {
            super(trader, PlayerEntity.class, range, 1.0F);
            this.trader = trader;
        }

        @Override
        public boolean canStart() {
            if (trader.isHidden() || this.mob.getRandom().nextFloat() >= this.chance) return false;
            this.target = trader.findAttentionTarget(this.range);
            return this.target != null;
        }

        @Override
        public boolean shouldContinue() {
            return !trader.isHidden() && this.target instanceof PlayerEntity player
                    && HidingTraderEntity.isAttentionTarget(player) && super.shouldContinue();
        }
    }

    /**
     * A merchant not assigned to a shop wanders a little around his home (short trips with pauses, at most
     * {@link HidingTraderEntity#WANDER_RADIUS} blocks away), only while he is out and free (no customer, no little
     * animation playing). Vanilla wander cadence; water and big drops are avoided by his pathfinding penalties.
     */
    public static class WanderNearHomeGoal extends WanderAroundGoal {
        private final HidingTraderEntity trader;

        public WanderNearHomeGoal(HidingTraderEntity trader, double speed, int chance) {
            super(trader, speed, chance);
            this.trader = trader;
        }

        @Override
        public boolean canStart() {
            return trader.canWander() && super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return trader.canWander() && super.shouldContinue();
        }

        @Override
        protected @Nullable Vec3d getWanderTarget() {
            Vec3d target = NoPenaltyTargeting.find(this.mob, 5, 2);
            BlockPos home = trader.getHome();
            if (target == null || home == null) return null;
            double radius = HidingTraderEntity.WANDER_RADIUS;
            return target.squaredDistanceTo(Vec3d.ofBottomCenter(home)) <= radius * radius ? target : null;
        }
    }
}
