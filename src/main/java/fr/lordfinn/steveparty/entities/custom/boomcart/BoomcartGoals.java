package fr.lordfinn.steveparty.entities.custom.boomcart;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * What a Boomcart does off the rails (on rails, BoomcartRails moves it and these goals wait).
 * <ul>
 *     <li>{@link Wander}: it rolls about a little, now and then, unlit.</li>
 *     <li>{@link Panic}: lit, it rushes in a zigzag at its target, a player who isn't the one who lit it (or passed it
 *     on); without one, it zigzags about.</li>
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

    static final class Panic extends Goal {
        /** Its rush, against its walking speed, and its zigzag: this far to the side, switching every so many ticks. */
        private static final double SPEED = 1.5, SWERVE = 1.6, AHEAD = 3.0;
        private static final int SWITCH = 12, REPATH = 4;
        private final BoomcartEntity boomcart;
        private int ticks;

        Panic(BoomcartEntity boomcart) {
            this.boomcart = boomcart;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return boomcart.isLit() && !boomcart.isOnRails();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            boomcart.getNavigation().stop();
        }

        @Override
        public void tick() {
            ticks++;
            if (ticks % REPATH != 0) return;
            PlayerEntity target = boomcart.getPanicTarget();
            Vec3d pos = boomcart.getPos();
            Vec3d toward;
            if (target != null) {
                boomcart.getLookControl().lookAt(target, 30, 30);
                toward = target.getPos().subtract(pos).multiply(1, 0, 1);
                if (toward.lengthSquared() < 2.5 * 2.5) {
                    boomcart.getNavigation().startMovingTo(target, SPEED); // close: straight at them
                    return;
                }
            } else {
                float yaw = boomcart.getYaw() * ((float) Math.PI / 180f);
                toward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
            }
            toward = toward.normalize();
            Vec3d side = new Vec3d(-toward.z, 0, toward.x).multiply((ticks / SWITCH) % 2 == 0 ? SWERVE : -SWERVE);
            Vec3d to = pos.add(toward.multiply(AHEAD)).add(side);
            boomcart.getNavigation().startMovingTo(to.x, pos.y, to.z, SPEED);
        }
    }
}
