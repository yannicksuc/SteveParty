package fr.lordfinn.steveparty.entities.custom.frousseux;

import fr.lordfinn.steveparty.entities.custom.goals.SimpleFlyingMoveControl;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * How a Frousseux floats about: a little wander near the ground, and its <b>thin walls</b>. It flies straight through
 * blocks (no collision, it never suffocates), but only where {@link #canFly} allows it:
 * <ul>
 *     <li>a run of at most {@link #MAX_WALL} full blocks in a row on the way (a thin wall, to the room behind);</li>
 *     <li>blocks that are not full cubes (panes, bars, signs, doors, slabs...) do not count, it passes them too;</li>
 *     <li>the end of the way is open space, never inside a block: it never goes into thick rock.</li>
 * </ul>
 * Each route is checked when it is chosen, and again whenever it enters another block (the world may have changed);
 * shut in anyway (blocks placed around it), it slips out to the nearest open space ({@link #escape}).
 */
public final class FrousseuxFlight {
    /** The thickest wall it passes through, in full blocks. */
    public static final int MAX_WALL = 2;
    /** Its wander: this far around it, and how high over the floor it floats. */
    static final int WANDER_RANGE = 8;
    static final double MIN_HOVER = 0.6, MAX_HOVER = 2.2;
    /** The floor is at most this far down from where it wanders to (it never floats high in a big cavern). */
    private static final int FLOOR_SEARCH = 6;
    /** Sampling step along a route (blocks): fine enough to see every block it crosses head-on. */
    private static final double STEP = 0.25;

    private FrousseuxFlight() {
    }

    /** A full block, one that counts in a wall's thickness. */
    public static boolean solid(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isFullCube(world, pos);
    }

    /**
     * Whether it may fly straight from {@code from} to {@code to} (its centre): no run of more than {@link #MAX_WALL}
     * full blocks on the way, and {@code to} not in one.
     */
    public static boolean canFly(World world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        int steps = Math.max(1, MathHelper.ceil(delta.length() / STEP));
        BlockPos.Mutable at = new BlockPos.Mutable();
        long last = Long.MIN_VALUE;
        int run = 0;
        for (int i = 0; i <= steps; i++) {
            double k = (double) i / steps;
            at.set(from.x + delta.x * k, from.y + delta.y * k, from.z + delta.z * k);
            long key = at.asLong();
            if (key == last) continue;
            last = key;
            if (!world.isChunkLoaded(at)) return false;
            if (solid(world, at)) {
                if (++run > MAX_WALL) return false;
            } else {
                run = 0;
            }
        }
        return run == 0;
    }

    /** Somewhere to wander to (its feet), or null when nothing fits this time. */
    static @Nullable Vec3d wanderTarget(FrousseuxEntity frousseux, Random random) {
        World world = frousseux.getWorld();
        Vec3d centre = frousseux.getBoundingBox().getCenter();
        double half = frousseux.getHeight() / 2;
        for (int tries = 0; tries < 10; tries++) {
            double x = frousseux.getX() + (random.nextDouble() * 2 - 1) * WANDER_RANGE;
            double z = frousseux.getZ() + (random.nextDouble() * 2 - 1) * WANDER_RANGE;
            BlockPos pos = BlockPos.ofFloored(x, frousseux.getY() + random.nextInt(7) - 3, z);
            if (solid(world, pos)) continue;
            Integer floor = floorBelow(world, pos);
            if (floor == null) continue;
            Vec3d target = new Vec3d(x, floor + MIN_HOVER + random.nextDouble() * (MAX_HOVER - MIN_HOVER), z);
            if (fits(frousseux, target) && canFly(world, centre, target.add(0, half, 0))) return target;
        }
        return null;
    }

    /** A spot {@code min..max} blocks away to slip to (a dodge), or null. */
    static @Nullable Vec3d hopTarget(FrousseuxEntity frousseux, Random random, double min, double max) {
        World world = frousseux.getWorld();
        Vec3d centre = frousseux.getBoundingBox().getCenter();
        double half = frousseux.getHeight() / 2;
        for (int tries = 0; tries < 12; tries++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = min + random.nextDouble() * (max - min);
            Vec3d target = frousseux.getPos().add(Math.cos(angle) * distance, random.nextDouble() - 0.3, Math.sin(angle) * distance);
            if (fits(frousseux, target) && canFly(world, centre, target.add(0, half, 0))) return target;
        }
        return null;
    }

    /**
     * Somewhere to flee to (its feet), away from {@code from} (anywhere if null), 4 to 7 blocks off, over a floor if
     * there is one near, by the thin-walls rule; or null.
     */
    static @Nullable Vec3d fleeTarget(FrousseuxEntity frousseux, @Nullable Entity from, Random random) {
        World world = frousseux.getWorld();
        Vec3d centre = frousseux.getBoundingBox().getCenter();
        double half = frousseux.getHeight() / 2;
        double away = from == null ? random.nextDouble() * Math.PI * 2
                : Math.atan2(frousseux.getZ() - from.getZ(), frousseux.getX() - from.getX());
        for (int tries = 0; tries < 12; tries++) {
            double angle = away + (random.nextDouble() - 0.5) * 1.8;
            double distance = 4 + random.nextDouble() * 3;
            double x = frousseux.getX() + Math.cos(angle) * distance, z = frousseux.getZ() + Math.sin(angle) * distance;
            BlockPos pos = BlockPos.ofFloored(x, frousseux.getY() + random.nextInt(3) - 1, z);
            if (solid(world, pos)) continue;
            Integer floor = floorBelow(world, pos);
            double y = floor == null ? pos.getY() : floor + MIN_HOVER + random.nextDouble() * (MAX_HOVER - MIN_HOVER);
            Vec3d target = new Vec3d(x, y, z);
            if (fits(frousseux, target) && canFly(world, centre, target.add(0, half, 0))) return target;
        }
        return null;
    }

    /** The top of the floor under {@code pos} (a y), within {@link #FLOOR_SEARCH} blocks. */
    private static @Nullable Integer floorBelow(World world, BlockPos pos) {
        BlockPos.Mutable at = pos.mutableCopy();
        for (int i = 0; i < FLOOR_SEARCH; i++) {
            at.move(0, -1, 0);
            if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty()) return at.getY() + 1;
        }
        return null;
    }

    /** Its hitbox fits at {@code feet}, out of every block. */
    private static boolean fits(FrousseuxEntity frousseux, Vec3d feet) {
        return frousseux.getWorld().isSpaceEmpty(frousseux, frousseux.getDimensions(frousseux.getPose()).getBoxAt(feet));
    }

    /** Shut inside full blocks: the nearest open space within 3 blocks (its feet), or null. */
    static @Nullable Vec3d escape(FrousseuxEntity frousseux) {
        World world = frousseux.getWorld();
        BlockPos at = BlockPos.ofFloored(frousseux.getBoundingBox().getCenter());
        if (!solid(world, at)) return null;
        return BlockPos.findClosest(at, 3, 3, pos -> !solid(world, pos) && fits(frousseux, Vec3d.ofBottomCenter(pos)))
                .map(Vec3d::ofBottomCenter).orElse(null);
    }

    /** Its flight: the Mula's floaty one, minus the "blocked ahead" stop (its route was checked by the wall rule). */
    static final class Control extends SimpleFlyingMoveControl {
        Control(FrousseuxEntity frousseux) {
            super(frousseux, 12f);
        }

        @Override
        protected boolean blockedAhead(BlockPos pos) {
            return false;
        }

        boolean isMovingTo() {
            return state == MoveControl.State.MOVE_TO;
        }

        void stop() {
            state = MoveControl.State.WAIT;
        }
    }

    /** A thief on the run: away from whom it robbed, fast, a new way every second (laughing: FrousseuxEntity). */
    static final class Flee extends Goal {
        private static final double SPEED = FrousseuxEntity.FLY_SPEED * 2.6;
        private final FrousseuxEntity frousseux;
        private int repick;

        Flee(FrousseuxEntity frousseux) {
            this.frousseux = frousseux;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return frousseux.isFleeing() && !frousseux.isTamed() && !frousseux.isBoardActor();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            repick = 0;
        }

        @Override
        public void tick() {
            if (--repick > 0 && frousseux.flight().isMovingTo()) return;
            repick = 20;
            Vec3d to = fleeTarget(frousseux, frousseux.fleeFrom(), frousseux.getRandom());
            if (to != null) frousseux.getMoveControl().moveTo(to.x, to.y, to.z, SPEED);
        }

        @Override
        public void stop() {
            frousseux.flight().stop();
        }
    }

    /** Now and then it floats off somewhere near, through a thin wall sometimes; not while it hides its eyes. */
    static final class Wander extends Goal {
        private final FrousseuxEntity frousseux;
        private long lastBlock;
        private @Nullable Vec3d target;

        Wander(FrousseuxEntity frousseux) {
            this.frousseux = frousseux;
            setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (!frousseux.isFree() || frousseux.getRandom().nextInt(50) != 0) return false;
            target = wanderTarget(frousseux, frousseux.getRandom());
            return target != null;
        }

        @Override
        public void start() {
            lastBlock = frousseux.getBlockPos().asLong();
            frousseux.getMoveControl().moveTo(target.x, target.y, target.z, FrousseuxEntity.FLY_SPEED);
        }

        @Override
        public boolean shouldContinue() {
            return target != null && frousseux.isFree() && frousseux.flight().isMovingTo();
        }

        @Override
        public void tick() {
            long block = frousseux.getBlockPos().asLong();
            if (block == lastBlock || target == null) return;
            lastBlock = block;
            // entering another block: the way on must still be allowed (something may have been built meanwhile)
            Vec3d centre = frousseux.getBoundingBox().getCenter();
            if (!canFly(frousseux.getWorld(), centre, target.add(0, frousseux.getHeight() / 2, 0))) {
                target = null;
                frousseux.flight().stop();
            }
        }

        @Override
        public void stop() {
            target = null;
            frousseux.flight().stop();
        }
    }
}
