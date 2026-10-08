package fr.lordfinn.steveparty.entities.custom.boomcart;

import net.minecraft.entity.ai.goal.WanderAroundGoal;

/**
 * What a Boomcart does off the rails (on rails, BoomcartRails moves it and these goals wait).
 * <ul>
 *     <li>{@link Wander}: it rolls about a little, now and then, unlit.</li>
 * </ul>
 */
final class BoomcartGoals {
    private BoomcartGoals() {
    }

    static final class Wander extends WanderAroundGoal {
        private final BoomcartEntity boomcart;

        Wander(BoomcartEntity boomcart) {
            super(boomcart, 0.8, 160);
            this.boomcart = boomcart;
        }

        private boolean free() {
            return !boomcart.isLit() && !boomcart.isHungry() && !boomcart.isOnRails();
        }

        @Override
        public boolean canStart() {
            return free() && super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return free() && super.shouldContinue();
        }
    }
}
