package fr.lordfinn.steveparty.entities.custom.trichaudron;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * Riding a tamed, saddled Trichaudron: up to three riders, the first in the middle of the tank, the others on its left
 * and right rims, each holding the reins of
 * one head (the first the centre one, then the left, then the right).
 * <ul>
 *     <li><b>Steering together</b> ({@link #combine}): every rider's keys count, added up. Opposite keys cancel (left +
 *     right: no turn; forward + back: no move); agreeing keys add up: two or three riders pushing forward go much
 *     faster than one ({@link #speedFor}), three turning together turn very fast. One rider alone already goes
 *     {@link #RIDE_SPEED}, about twice its wild crawl.</li>
 *     <li><b>Its head</b>: a rider's head aims where he looks; his attack or use key fires it (a bucket a blast, the
 *     head resting {@link #FIRE_COOLDOWN} ticks).</li>
 *     <li><b>The charged jump</b>: holding jump (any rider) charges it, up to {@link #CHARGE_MAX} ticks; on release
 *     the three heads turn into thrusters, steam jets down, and it leaps ({@link #jumpVelocity}): a bucket, two for a
 *     near full charge. It needs ground or lava under it; from the lava it leaps higher and farther
 *     ({@link #LAVA_LEAP_UP}, {@link #LAVA_LEAP_FORWARD}) to climb out onto a bank.</li>
 *     <li><b>Its fuel</b>: lava. Ridden, it goes as fast as its tank is full ({@link #fuelFactor}): empty, it can't
 *     move or turn at all. A rider's click with a lava bucket pours it in instead of firing.</li>
 *     <li><b>Climbing</b> ({@link #findLedge}): in the air after a jump, a click makes the heads reach for the wall in
 *     front (for a second and a half, until one comes within {@link #GRAB_REACH} blocks) and hoist it, riders and all,
 *     onto the ledge above (up to {@link #CLIMB_MAX} blocks higher).</li>
 *     <li><b>Swimming</b>: in deep lava it floats, its tank above the surface, and is steered as on land, a little
 *     slower.</li>
 * </ul>
 */
public final class TrichaudronRiding {
    public static final int MAX_RIDERS = 3;
    /** One rider's speed (movement attribute units; the wild turtle crawls at 0.12), and each extra agreeing rider's share. */
    public static final float RIDE_SPEED = 0.25f, EXTRA_RIDER_SPEED = 0.6f;
    /** Degrees a tick per rider turning. */
    public static final float TURN_PER_RIDER = 2.5f;
    public static final int FIRE_COOLDOWN = 30;
    public static final int CHARGE_MIN = 8, CHARGE_MAX = 40;
    /** The leap: upward speed at the least and at a full charge, forward push at a full charge. */
    public static final double JUMP_UP_MIN = 0.7, JUMP_UP_MAX = 1.8, JUMP_FORWARD = 0.6;
    /** A charge from this much on costs a second bucket. */
    public static final int CHARGE_COSTLY = 30;
    /** Climbing: how far ahead of its shell it looks for a wall, how high a ledge may be, how fast it hoists itself. */
    public static final double GRAB_REACH = 4.0;
    public static final int CLIMB_MAX = 10;
    public static final double CLIMB_SPEED = 0.35, CLIMB_OVER_SPEED = 0.25;
    public static final int CLIMB_TIMEOUT = 100;
    /**
     * In lava it floats this deep (blocks of lava over its feet: the surface clear of its shell's underside, its legs'
     * tops and its knees, no face of its model flush with it), slower by this much.
     */
    public static final double SWIM_DEPTH = 2.0, SWIM_MIN_DEPTH = 0.6;
    /** Standing on the bottom of shallow lava, it is drawn (and its riders sat) this much lower: no knee flush with it. */
    public static final float SHALLOW_SINK = 0.5f;
    public static final float SWIM_FACTOR = 0.8f;

    private TrichaudronRiding() {
    }

    /** The riders' keys added up: forward (positive: ahead) and turn (positive: to the left), each in -3..3. */
    public record Steer(float forward, float turn, boolean jumping) {
        public boolean moving() {
            return Math.abs(forward) > 0.01f;
        }
    }

    /** Adds up the riders' keys (each clamped to -1..1, as the client sends them). */
    public static Steer combine(List<? extends Entity> riders, Predicate<LivingEntity> jumping) {
        float forward = 0, turn = 0;
        boolean jump = false;
        for (Entity rider : riders) {
            if (!(rider instanceof LivingEntity living)) continue;
            forward += MathHelper.clamp(living.forwardSpeed, -1, 1);
            turn += MathHelper.clamp(living.sidewaysSpeed, -1, 1);
            jump |= jumping.test(living);
        }
        return new Steer(forward, turn, jump);
    }

    /** The speed for this much forward key: one rider's {@link #RIDE_SPEED}, more with every agreeing one; back slower. */
    public static float speedFor(float forward) {
        float amount = Math.abs(forward);
        if (amount < 0.01f) return 0;
        float speed = amount <= 1 ? RIDE_SPEED * amount : RIDE_SPEED * (1 + (amount - 1) * EXTRA_RIDER_SPEED);
        return forward < 0 ? speed * 0.5f : speed;
    }

    /** How much it turns this tick (degrees, positive: to the left, the yaw going down). */
    public static float turnFor(float turn) {
        return turn * TURN_PER_RIDER;
    }

    /** The leap's speed for a charge (ticks), along {@code yaw}. */
    public static Vec3d jumpVelocity(int charge, float yaw) {
        double t = MathHelper.clamp((charge - CHARGE_MIN) / (double) (CHARGE_MAX - CHARGE_MIN), 0, 1);
        Vec3d ahead = Vec3d.fromPolar(0, yaw).multiply(JUMP_FORWARD * t);
        return new Vec3d(ahead.x, JUMP_UP_MIN + (JUMP_UP_MAX - JUMP_UP_MIN) * t, ahead.z);
    }

    /** From the lava, a leap gets this much more up (blocks a tick) and this much more ahead (factor). */
    public static final double LAVA_LEAP_UP = 0.45, LAVA_LEAP_FORWARD = 1.6;

    /** A leap from the lava: higher, farther. */
    public static Vec3d lavaLeap(Vec3d leap) {
        return new Vec3d(leap.x * LAVA_LEAP_FORWARD, leap.y + LAVA_LEAP_UP, leap.z * LAVA_LEAP_FORWARD);
    }

    /** Its riding speed and turn rate as a share of the full ones, for this many buckets in its tank: 0 empty, 1 full. */
    public static float fuelFactor(int tank) {
        return MathHelper.clamp(tank / (float) TrichaudronEntity.TANK_MAX, 0, 1);
    }

    /** Buckets a leap of this charge costs. */
    public static int jumpCost(int charge) {
        return charge >= CHARGE_COSTLY ? 2 : 1;
    }

    /** How far from the tank's middle its side rims are (blocks: the middle of its 6 px walls, the model at 1.1). */
    public static final double SIDE_RIM = 1.1;

    /**
     * The seat of the rider holding this head, as a vehicle attachment (local, y up): the first (centre head) in the
     * middle of the tank, the second (left head) on its left rim, the third (right head) on its right rim.
     */
    public static Vec3d seat(int index, double rimHeight) {
        TrichaudronHead head = TrichaudronEntity.HEADS[Math.min(index, TrichaudronEntity.HEADS.length - 1)];
        if (index == 0) return new Vec3d(0, rimHeight, 0);
        // local x points to the turtle's left (at yaw 0, east): its right is -x; a head's seat sign gives its side
        return new Vec3d(-Math.signum(head.seat()) * SIDE_RIM, rimHeight, 0);
    }

    /**
     * Where the heads can pull it up to: a wall within {@link #GRAB_REACH} blocks in front of its shell, its top at
     * least 2 blocks above {@code baseY} (where it jumped from) and at most {@link #CLIMB_MAX} above its feet, with room
     * on it ({@code height} blocks of air). In the air above the wall's top already, the top still counts. Returns the
     * ledge's foot position (the turtle's feet once over it), or null.
     */
    public static @Nullable Vec3d findLedge(World world, Vec3d feet, float yaw, double halfWidth, double height, double baseY) {
        Vec3d ahead = Vec3d.fromPolar(0, yaw);
        int room = (int) Math.ceil(height);
        int from = (int) Math.floor(Math.max(baseY + 1, feet.y - 6)), to = (int) Math.floor(feet.y) + CLIMB_MAX;
        for (double d = 0.5; d <= GRAB_REACH; d += 0.5) {
            Vec3d front = feet.add(ahead.multiply(halfWidth + d));
            for (int y = from; y <= to; y++) {
                BlockPos top = BlockPos.ofFloored(front.x, y, front.z);
                if (!solid(world, top)) continue;
                boolean free = true;
                for (int k = 1; k <= room && free; k++) free = !solid(world, top.up(k));
                if (!free) continue;
                if (top.getY() + 1 < baseY + 2) break; // a step, not a wall
                Vec3d over = feet.add(ahead.multiply(halfWidth + d + halfWidth + 0.6));
                return new Vec3d(over.x, top.getY() + 1, over.z);
            }
        }
        return null;
    }

    private static boolean solid(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isSolidBlock(world, pos);
    }
}
