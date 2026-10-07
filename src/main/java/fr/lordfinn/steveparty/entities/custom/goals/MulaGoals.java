package fr.lordfinn.steveparty.entities.custom.goals;

import fr.lordfinn.steveparty.entities.custom.MulaDances;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaHome;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * The Mula's behaviours beyond wandering and following, each a goal holding the MOVE control, so they exclude each
 * other by priority (MulaEntity#initGoals):
 * sit 0, {@link Shy} 1, follow owner 2, {@link Dance} / {@link Spectate} 3, {@link OrbitOwner} 4, {@link Curious} 5, {@link Play} 6,
 * {@link Shiny} 6, {@link Sky} 7, wander (flocks) 8.
 * They read what is around from {@link MulaBrain} (refreshed rarely) and steer the move control towards points
 * computed from formulas: no pathfinding, nothing allocated per tick.
 */
public final class MulaGoals {
    private MulaGoals() {
    }

    static void fly(MulaEntity mula, double x, double y, double z, double speed, boolean through) {
        MoveControl move = mula.getMoveControl();
        if (through && move instanceof SimpleFlyingMoveControl flying) {
            flying.moveThrough(x, y, z, speed);
        } else {
            move.moveTo(x, y, z, speed);
        }
    }

    /** Ground height under a point (first solid block within 64 blocks), or NaN over the void. */
    static double groundBelow(World world, double x, double y, double z) {
        BlockPos.Mutable pos = new BlockPos.Mutable(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
        for (int i = 0; i < 64 && pos.getY() > world.getBottomY(); i++) {
            pos.move(0, -1, 0);
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return pos.getY() + 1.0;
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------------------------------ forge dances

    /**
     * Near a Dice Forge (which counts its dancers once a second, DiceForgeBlockEntity#conductMulas) a Mula doesn't
     * leave: it flies to its place in the figure, then is moved by the formula (MulaDances), the same on every side.
     */
    public static final class Dance extends Goal {
        private final MulaEntity mula;
        private final double[] out = new double[4];

        public Dance(MulaEntity mula) {
            this.mula = mula;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private boolean assigned() {
            return mula.isDancing() && mula.getWorld().getTime() - mula.danceAssignedTick() < 60;
        }

        @Override
        public boolean canStart() {
            return assigned() && !mula.isSitting();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            mula.getNavigation().stop();
            joinX = mula.getX();
            joinY = mula.getY();
            joinZ = mula.getZ();
            joinStart = mula.getWorld().getTime();
            joinTicks = -1;
        }

        @Override
        public void stop() {
            if (mula.isDancing()) mula.stopDancing(); // not when it now watches (its turn is over)
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (mula.isDanceLocked()) {
                mula.followDance();
                return;
            }
            // joining: glides from where it was onto its (moving) place in the figure, eased, then locks onto it
            mula.dancePosition(0f, out);
            if (joinTicks < 0) {
                double d = Math.sqrt(mula.squaredDistanceTo(out[0], out[1], out[2]));
                joinTicks = (int) MathHelper.clamp(d * 6, 20, 80);
            }
            double u = (mula.getWorld().getTime() - joinStart) / (double) joinTicks;
            if (u >= 1) {
                mula.lockDance();
                mula.followDance();
                return;
            }
            double e = u * u * (3 - 2 * u);
            double x = MathHelper.lerp(e, joinX, out[0]), z = MathHelper.lerp(e, joinZ, out[2]);
            // a little arc up on the way, like a leap into the dance
            double y = MathHelper.lerp(e, joinY, out[1]) + 0.8 * Math.sin(Math.PI * u);
            double dx = x - mula.getX(), dz = z - mula.getZ();
            if (!mula.getWorld().isSpaceEmpty(mula, mula.getBoundingBox().offset(x - mula.getX(), y - mula.getY(), z - mula.getZ()))) {
                // a block on the way: it flies round it (the move control collides) and glides on from there
                fly(mula, out[0], out[1], out[2], 0.3, true);
                return;
            }
            mula.setPosition(x, y, z);
            mula.setVelocity(Vec3d.ZERO);
            if (dx * dx + dz * dz > 1.0E-4) mula.setYaw((float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f);
        }

        private double joinX, joinY, joinZ;
        private long joinStart;
        private int joinTicks;
    }

    /**
     * Not its turn to dance (more Mulas than dance at once, DiceForgeBlockEntity#conductMulas): it steps back to its
     * spectator spot round the dance (MulaDances#spectatorSpot, just above the ground), hovers there facing the core,
     * cheers once on arrival (happy hop) then on the beat (client, MulaEffects#spectatorTick). No wandering into the
     * dancers meanwhile.
     */
    public static final class Spectate extends Goal {
        private final MulaEntity mula;
        private final double[] spot = new double[3];
        /** The spot (index | count << 16) the target was computed for, and whether it has cheered on arrival. */
        private int spotFor = -1;
        private boolean arrived;

        public Spectate(MulaEntity mula) {
            this.mula = mula;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return mula.isSpectating() && mula.getWorld().getTime() - mula.danceAssignedTick() < 60 && !mula.isSitting();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            mula.getNavigation().stop();
            spotFor = -1;
        }

        @Override
        public void stop() {
            if (mula.isSpectating()) mula.stopDancing(); // not when it now dances (its turn has come)
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            BlockPos forge = mula.danceForge();
            if (forge == null) return;
            int key = mula.danceSlot() | mula.danceCount() << 16;
            if (key != spotFor) {
                spotFor = key;
                arrived = false;
                MulaDances.spectatorSpot(mula.danceSlot(), mula.danceCount(), spot);
                double x = forge.getX() + 0.5 + spot[0], z = forge.getZ() + 0.5 + spot[2];
                // on the ground round the forge (a slope, a step), never below the forge's own level
                double ground = groundBelow(mula.getWorld(), x, forge.getY() + 6, z);
                spot[0] = x;
                spot[1] = (Double.isNaN(ground) ? forge.getY() : Math.max(forge.getY(), ground)) + spot[1];
                spot[2] = z;
                MulaHome.clamp(forge, spot);
            }
            double cx = forge.getX() + 0.5, cy = forge.getY() + 2.4, cz = forge.getZ() + 0.5;
            double d2 = mula.squaredDistanceTo(spot[0], spot[1], spot[2]);
            if (d2 > 0.04) fly(mula, spot[0], spot[1], spot[2], d2 > 4 ? 0.35 : 0.15, false);
            mula.getLookControl().lookAt(cx, cy, cz, 10, 10);
            if (d2 < 0.36) {
                float yaw = (float) (MathHelper.atan2(cz - mula.getZ(), cx - mula.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
                mula.setYaw(yaw);
                mula.setBodyYaw(yaw);
                if (!arrived) {
                    arrived = true;
                    mula.playAnimation("happy_hop");
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ on a lead at night

    /**
     * On a lead at night: it wants the sky, but stays tethered. Carrying its holder (MulaLift): it floats above them,
     * spread with the others round the leads, gently bobbing; not enough lift, or tied to a fence: it hovers a little
     * above the holder or the knot, tugging upwards within the lead's slack (the lead never has to pull it hard).
     */
    public static final class Tethered extends Goal {
        private final MulaEntity mula;
        private int t;

        public Tethered(MulaEntity mula) {
            this.mula = mula;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return mula.isLeashed() && mula.getLeashHolder() != null && mula.getWorld().isNight() && !mula.isSitting();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            mula.getNavigation().stop();
        }

        @Override
        public void stop() {
            mula.setCarrying(false);
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            t++;
            var holder = mula.getLeashHolder();
            if (holder == null) return;
            double a = mula.getId() * 2.39996 + t * 0.01;
            double r = mula.isCarrying() ? 1.0 + (mula.getId() % 3) * 0.45 : 0.7;
            double up = mula.isCarrying() ? 3.4 + (mula.getId() % 2) * 0.5 : 2.6;
            double y = holder.getY() + holder.getHeight() * 0.5 + up + 0.25 * Math.sin(t * 0.06 + mula.getId());
            fly(mula, holder.getX() + Math.cos(a) * r, y, holder.getZ() + Math.sin(a) * r, 0.25, false);
        }
    }

    // ------------------------------------------------------------------------------------------ 6. shy after a hit

    /**
     * Hit: flees a short way (fast) to its hideout (behind its owner, or leaves / a block between it and the threat),
     * waits there, peeks out (look_around), then goes back to what it was doing.
     */
    public static final class Shy extends Goal {
        private final MulaEntity mula;
        private boolean peeked;

        public Shy(MulaEntity mula) {
            this.mula = mula;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return mula.getMulaBrain().isShy() && !mula.isSitting();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            mula.getNavigation().stop();
            peeked = false;
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            MulaBrain brain = mula.getMulaBrain();
            Vec3d target = brain.shyTarget();
            if (target == null) return;
            int t = MulaBrain.SHY_TICKS - brain.shyTicks();
            fly(mula, target.x, target.y, target.z, t < MulaBrain.SHY_FLEE_TICKS ? 0.35 : 0.1, false);
            Vec3d threat = brain.shyThreat();
            if (t >= MulaBrain.SHY_FLEE_TICKS && threat != null) {
                mula.getLookControl().lookAt(threat.x, threat.y, threat.z, 10, 10);
            }
            if (!peeked && t >= MulaBrain.SHY_PEEK_TICKS) {
                peeked = true;
                mula.playAnimation("look_around");
            }
        }
    }

    // ------------------------------------------------------------------------------------------ 1. orbit the owner

    /**
     * The owner has stood still for 3 s: the tamed Mula slowly circles them at head height, bobbing, leaning into the
     * curve (the client banks it), turning back now and then. As soon as the owner moves it stops and follows again.
     */
    public static final class OrbitOwner extends Goal {
        public static final int STILL_TICKS = 60;
        private static final double RADIUS = 1.7, ANGULAR_SPEED = 0.045;
        private final MulaEntity mula;
        private double angle;
        private float direction;
        private int reverseIn;
        private int t;

        public OrbitOwner(MulaEntity mula) {
            this.mula = mula;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private PlayerEntity owner() {
            return mula.getOwner() instanceof PlayerEntity p && !p.isSpectator() ? p : null;
        }

        @Override
        public boolean canStart() {
            PlayerEntity owner = owner();
            return owner != null && mula.isTamed() && !mula.isSitting() && mula.getMulaBrain().isActive()
                    && mula.getMulaBrain().ownerStillTicks() >= STILL_TICKS && owner.squaredDistanceTo(mula) < 36
                    // (one of its followers: the Mulas past the followers' limit stay where they are)
                    && fr.lordfinn.steveparty.entities.custom.MulaEscorts.isFollower(owner.getUuid(), mula);
        }

        @Override
        public boolean shouldContinue() {
            PlayerEntity owner = owner();
            return owner != null && !mula.isSitting() && mula.getMulaBrain().ownerStillTicks() > 0
                    && owner.squaredDistanceTo(mula) < 49;
        }

        @Override
        public void start() {
            PlayerEntity owner = owner();
            angle = Math.atan2(mula.getZ() - owner.getZ(), mula.getX() - owner.getX());
            direction = mula.getRandom().nextBoolean() ? 1 : -1;
            reverseIn = 160 + mula.getRandom().nextInt(160);
            t = 0;
            mula.getNavigation().stop();
            mula.getMulaBrain().orbiting = true;
        }

        @Override
        public void stop() {
            mula.getMulaBrain().orbiting = false;
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            PlayerEntity owner = owner();
            if (owner == null) return;
            t++;
            // eases into its speed, turns back now and then
            if (--reverseIn <= 0) {
                direction = -direction;
                reverseIn = 160 + mula.getRandom().nextInt(160);
            }
            angle += direction * ANGULAR_SPEED * Math.min(1, t / 30.0);
            double y = owner.getY() + owner.getHeight() + 0.35 + 0.3 * Math.sin(angle * 2);
            fly(mula, owner.getX() + Math.cos(angle) * RADIUS, y, owner.getZ() + Math.sin(angle) * RADIUS, 0.2, true);
            if ((t & 31) == 0) mula.getLookControl().lookAt(owner, 10, 10);
        }
    }

    // ------------------------------------------------------------------------------------------ 3. curiosity

    /**
     * A wild Mula comes shyly towards a player holding star fragments or its food: stops at a comfortable distance
     * (within reach to be fed), tilts its head, inches closer if the player stays still, backs off a little if they
     * sprint or rush at it, then comes back.
     */
    public static final class Curious extends Goal {
        private final MulaEntity mula;
        private int startled;
        private boolean tilted;

        public Curious(MulaEntity mula) {
            this.mula = mula;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return !mula.isTamed() && mula.getMulaBrain().isActive() && mula.getMulaBrain().curiousPlayer() != null;
        }

        @Override
        public boolean shouldContinue() {
            PlayerEntity p = mula.getMulaBrain().curiousPlayer();
            return p != null && !mula.isTamed() && p.squaredDistanceTo(mula) < 144;
        }

        @Override
        public void start() {
            tilted = false;
            startled = 0;
            mula.getNavigation().stop();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            MulaBrain brain = mula.getMulaBrain();
            PlayerEntity p = brain.curiousPlayer();
            if (p == null) return;
            double wanted = MulaBrain.curiousDistance(brain.curiousStillTicks(), p.isSprinting(), brain.curiousApproach());
            if (wanted > 3) startled = 30;
            if (startled > 0) {
                startled--;
                wanted = 4.5;
            }
            Vec3d away = MulaBrain.flatAway(mula.getPos(), p.getPos());
            double x = p.getX() + away.x * wanted, z = p.getZ() + away.z * wanted, y = p.getEyeY() + 0.2;
            fly(mula, x, y, z, startled > 0 ? 0.25 : 0.12, false);
            mula.getLookControl().lookAt(p, 10, 10);
            if (!tilted && mula.squaredDistanceTo(x, y, z) < 0.3) {
                tilted = true;
                mula.playAnimation("head_tilt");
            }
        }
    }

    // ------------------------------------------------------------------------------------------ 5. play

    /** Two idle wild Mulas chase each other on a spiral for a few seconds, twirling, then settle. */
    public static final class Play extends Goal {
        private final MulaEntity mula;
        private int t;

        public Play(MulaEntity mula) {
            this.mula = mula;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            MulaBrain brain = mula.getMulaBrain();
            if (mula.isTamed() || !brain.isActive()) return false;
            if (brain.isPlaying()) return true;
            // tried at the brain's refresh rhythm only (1 chance in 40 each second)
            return (mula.age + mula.getId()) % MulaBrain.NEIGHBOUR_TICKS == 0 && brain.tryStartPlay();
        }

        @Override
        public boolean shouldContinue() {
            return mula.getMulaBrain().isPlaying() && !mula.isTamed();
        }

        @Override
        public void start() {
            t = 0;
            mula.playAnimation("twirl");
        }

        @Override
        public void stop() {
            if (mula.getMulaBrain().playTicks() <= 1) mula.playAnimation("happy_hop");
            mula.getMulaBrain().stopPlay();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            MulaBrain b = mula.getMulaBrain();
            t++;
            double a = b.playPhase() + b.playDirection() * t * 0.16;
            double r = 1.3 + 0.35 * Math.sin(t * 0.1);
            double y = b.playCenterY() + 0.6 * Math.sin(t * 0.12 + b.playPhase());
            fly(mula, b.playCenterX() + Math.cos(a) * r, y, b.playCenterZ() + Math.sin(a) * r, 0.3, true);
        }
    }

    // ------------------------------------------------------------------------------------------ 7. shiny things

    /**
     * Drawn to shiny things (its food or star fragments dropped, star fragment blocks, a Dice Forge, a gravity core):
     * it circles them curiously, tilts its head, twirls, then goes on. It never picks anything up.
     */
    public static final class Shiny extends Goal {
        private final MulaEntity mula;
        private int t;
        private double angle;

        public Shiny(MulaEntity mula) {
            this.mula = mula;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return !mula.isTamed() && mula.getMulaBrain().isActive() && mula.getMulaBrain().shiny() != null;
        }

        @Override
        public boolean shouldContinue() {
            return canStart() && t < 150;
        }

        @Override
        public void start() {
            t = 0;
            Vec3d s = mula.getMulaBrain().shiny();
            angle = Math.atan2(mula.getZ() - s.z, mula.getX() - s.x);
        }

        @Override
        public void stop() {
            if (mula.getMulaBrain().shiny() != null) mula.getMulaBrain().doneInspecting();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Vec3d s = mula.getMulaBrain().shiny();
            if (s == null) return;
            t++;
            boolean near = mula.squaredDistanceTo(s) < 4;
            if (near) angle += 0.06;
            fly(mula, s.x + Math.cos(angle) * 1.1, s.y + 0.5 + 0.2 * Math.sin(t * 0.1), s.z + Math.sin(angle) * 1.1,
                    near ? 0.12 : 0.18, near);
            mula.getLookControl().lookAt(s.x, s.y, s.z, 10, 10);
            if (t == 40) mula.playAnimation("head_tilt");
            if (t == 100) mula.playAnimation("twirl");
        }
    }

    // ------------------------------------------------------------------------------------------ 4. day / night

    /**
     * Night: wild Mulas rise 10 to 18 blocks above the ground (never above the build limit, never over the void) and
     * drift there together, twinkling like stars. Morning: they come down and rest on a flower or grass, sleepy.
     */
    public static final class Sky extends Goal {
        private final MulaEntity mula;
        private boolean night;
        private double skyY;
        private BlockPos perch;
        private final BlockPos.Mutable probe = new BlockPos.Mutable();
        private int t;
        /** Deepest a perch is looked for below it (blocks): more than its highest night altitude. */
        private static final int PERCH_SCAN = 24;

        public Sky(MulaEntity mula) {
            this.mula = mula;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (mula.isTamed() || mula.isLeashed() || !mula.getMulaBrain().isActive()) return false;
            if ((mula.age + mula.getId()) % MulaBrain.NEIGHBOUR_TICKS != 0) return false; // looked at once a second
            World world = mula.getWorld();
            BlockPos home = mula.homeForge();
            if (world.isNight()) {
                // at home: it twinkles above its forge
                double ground = home != null ? home.getY() + 1.0 : groundBelow(world, mula.getX(), mula.getY(), mula.getZ());
                if (Double.isNaN(ground)) return false;
                night = true;
                skyY = MulaBrain.nightAltitude(ground, world.getTopY() - 1, mula.getId());
                return true;
            }
            if (MulaBrain.isMorning(world.getTimeOfDay())) {
                BlockPos found = findPerch(world);
                if (found == null) return false;
                night = false;
                perch = found;
                return true;
            }
            return false;
        }

        /**
         * A flower or tuft of grass within 8 blocks around, a few random samples, looked for down to the ground (it spent
         * the night 10 to 18 blocks up: a 10-block scan never reached the flowers, and it stayed in the sky all day).
         */
        private BlockPos findPerch(World world) {
            BlockPos home = mula.homeForge();
            // at home: a flower round its forge
            BlockPos from = home != null ? new BlockPos(home.getX(), mula.getBlockY(), home.getZ()) : mula.getBlockPos();
            return findPerch(world, from, mula.getRandom(), probe);
        }

        public static BlockPos findPerch(World world, BlockPos from, net.minecraft.util.math.random.Random random,
                                         BlockPos.Mutable probe) {
            for (int i = 0; i < 8; i++) {
                probe.set(from.getX() + random.nextBetween(-8, 8), from.getY(), from.getZ() + random.nextBetween(-8, 8));
                if (!world.isChunkLoaded(probe)) continue;
                for (int dy = 0; dy < PERCH_SCAN; dy++) {
                    BlockState state = world.getBlockState(probe);
                    if (state.isIn(BlockTags.FLOWERS) || state.isOf(net.minecraft.block.Blocks.SHORT_GRASS)) {
                        return probe.toImmutable();
                    }
                    if (!state.isAir()) break;
                    probe.move(0, -1, 0);
                }
            }
            return null;
        }

        @Override
        public boolean shouldContinue() {
            World world = mula.getWorld();
            if (mula.isTamed()) return false;
            return night ? world.isNight() : MulaBrain.isMorning(world.getTimeOfDay());
        }

        @Override
        public void start() {
            t = 0;
            mula.getNavigation().stop();
        }

        @Override
        public void stop() {
            mula.setResting(false);
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            t++;
            if (night) {
                // drifting slowly with its flock, like a star
                MulaEntity leader = mula.getMulaBrain().flockLeader();
                BlockPos home = mula.homeForge();
                double cx = leader != null ? leader.getX() : mula.getX(), cz = leader != null ? leader.getZ() : mula.getZ();
                double a = t * 0.015 + mula.getId();
                double r = leader != null ? 1.6 + (mula.getId() % 3) * 0.6 : 0.6;
                if (home != null) {
                    // at home: a slow ring of little stars over its forge
                    cx = home.getX() + 0.5;
                    cz = home.getZ() + 0.5;
                    r = 2 + (mula.getId() % 4) * 0.9;
                }
                fly(mula, cx + Math.cos(a) * r, skyY + 0.4 * Math.sin(t * 0.04 + mula.getId()), cz + Math.sin(a) * r,
                        mula.getY() < skyY - 2 ? 0.1 : 0.06, true);
            } else {
                double x = perch.getX() + 0.5, y = perch.getY() + 0.45, z = perch.getZ() + 0.5;
                fly(mula, x, y, z, 0.1, false);
                if (!mula.isResting() && mula.squaredDistanceTo(x, y, z) < 0.2) mula.setResting(true);
            }
        }
    }
}
