package fr.lordfinn.steveparty.entities.custom.bubble;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * The numbers of a Bubbler's bubbles ({@link BubbleEntity}), and how its riders steer one together.
 * <ul>
 *     <li><b>The launch</b> ({@link #launchHeight}): a bubble rises from its Bubbler as many blocks as the redstone
 *     signal the Bubbler gets (1 to 15), 15 with none; then it hovers there, bobbing.</li>
 *     <li><b>Drifting</b> ({@link #drift}): the players inside steer it with their movement keys, slowly and with
 *     inertia: every rider's keys count, averaged over all its riders (opposite keys cancel, a mob inside is dead
 *     weight).</li>
 *     <li><b>The puff</b> ({@link #puff}): holding jump charges it, up to {@link #CHARGE_MAX} ticks (a horse's jump
 *     bar); on release a puff of air propels the bubble where the rider looks, divided by how many ride it (a big bubble
 *     is heavy). Again after {@link #PUFF_COOLDOWN} ticks. Puffs chained too fast strain the bubble, and a strained
 *     puff may pop it ({@link #strainPopChance}).</li>
 *     <li><b>Its size</b> ({@link #side}): a cube around its largest rider, growing with the cube root of how many ride
 *     it (two bubbles touching merge, agar-like), {@link #MAX_RIDERS} at most.</li>
 * </ul>
 */
public final class BubbleRiding {
    /** The most riders a bubble holds: bubbles that would hold more together don't merge. */
    public static final int MAX_RIDERS = 8;
    /** How high a bubble rises over its Bubbler with no signal, and at most (blocks). */
    public static final int DEFAULT_HEIGHT = 15, MAX_HEIGHT = 15;
    /**
     * A bubble left still pops by itself: after this many ticks (15 s) in which it never got this far (blocks) from
     * where it was, its hovering bob not counting (it stays within {@link #BOB} of its height). Never while it travels.
     */
    public static final int IDLE_TICKS = 300;
    public static final double IDLE_MOVE = 0.5;
    /**
     * A bubble bumping into a block's collision shape or touching a mob, a player, a boat... pops, but in its first
     * ticks (this many: it grows around its rider and rises from its Bubbler, maybe among other mobs). How near a block
     * counts as touching it (blocks).
     */
    public static final int LAUNCH_GRACE = 10;
    public static final double TOUCH = 0.01;
    /**
     * A Bubbler's column (see BubbleEntity#inColumn): how far its bubble's centre may stray from its axis (blocks), and
     * how far under the Bubbler and over the height it hovers at the column goes (blocks).
     */
    public static final double COLUMN_RADIUS = 1.0, COLUMN_MARGIN = 1.0;
    /** A bubble about to burst by itself shows it this many ticks before (3 s): see BubbleEntity#weaken. */
    public static final int WARNING_TICKS = 60;
    /** How high a bubble blown by the Bubbled effect rises from where its rider was (blocks). */
    public static final double EFFECT_RISE = 3;
    /** Its largest rider and this much room around it (blocks): the side of a one-rider bubble. */
    public static final float MARGIN = 0.5f;
    /** The side of a bubble holding an item (blocks). */
    public static final float ITEM_SIDE = 0.75f;
    /** The biggest mob a Bubbler encloses (blocks): wider or taller, it walks over it. */
    public static final float MAX_WIDTH = 1.5f, MAX_HEIGHT_OF_RIDER = 2.6f;
    /** Rising to its height: at most this fast (blocks a tick), toward it by this share of the distance a tick, eased in by this share. */
    public static final double RISE_MAX = 0.3, HOVER_SPRING = 0.12, HOVER_EASE = 0.3;
    /** Its bob while it hovers: this many blocks up and down, this fast (radians a tick: about 4 s). */
    public static final double BOB = 0.12, BOB_SPEED = 0.08;
    /** Its speed kept a tick (the air holds it back), and how much of its speed a wall sends back. */
    public static final double DRAG = 0.93, BOUNCE = 0.4;
    /** The drift a rider's full movement key adds a tick (blocks a tick): about 2 blocks a second at most, alone. */
    public static final double DRIFT_ACCEL = 0.008;
    /** The puff: charged from {@code CHARGE_MIN} ticks to {@code CHARGE_MAX}, again {@code PUFF_COOLDOWN} ticks after. */
    public static final int CHARGE_MIN = 4, CHARGE_MAX = 24, PUFF_COOLDOWN = 10;
    /** Its push at the least and at a full charge (blocks a tick): about 6 to 17 blocks before the air stops it. */
    public static final double PUFF_MIN = 0.4, PUFF_MAX = 1.2;
    /**
     * Strain ({@link #strainPopChance}): what each puff adds to its bubble's strain (0..1), how much of it the bubble
     * loses a tick (all of it in 5 calm seconds), and the strain under which a puff is always safe. A puff every 2 s
     * never strains it past safe; three quick ones (as fast as the charge and its cooldown allow) get risky.
     */
    public static final float STRAIN_PER_PUFF = 0.4f, STRAIN_DECAY = 0.01f, STRAIN_SAFE = 0.3f;
    /** A hit's push and a snowball's (blocks a tick, before the bubble's weight), and a hit's chance to pop it (never with Silk Touch). */
    public static final double HIT_PUSH = 0.45, SNOWBALL_PUSH = 0.5;
    public static final float HIT_POP_CHANCE = 0.25f;
    /** Popped, its riders fall slowly: at least this long (ticks, 3 s), longer high up to reach the ground safely, at most this long. */
    public static final int SLOW_FALLING_MIN = 60, SLOW_FALLING_MAX = 600;
    /** How fast one falls with Slow Falling at most (blocks a tick), and how far down the ground is looked for. */
    public static final double SLOW_FALL_SPEED = 0.45;
    public static final int GROUND_SEARCH = 96;

    private BubbleRiding() {
    }

    /** How high a bubble rises over its Bubbler for this redstone signal: as many blocks, {@link #DEFAULT_HEIGHT} with none. */
    public static int launchHeight(int power) {
        return power <= 0 ? DEFAULT_HEIGHT : Math.min(power, MAX_HEIGHT);
    }

    /** Whether a Bubbler encloses this entity: a living one small enough (no bubble for a ghast) or a dropped item. */
    public static boolean fits(Entity entity) {
        if (entity instanceof ItemEntity) return true;
        return entity instanceof LivingEntity && entity.getWidth() <= MAX_WIDTH && entity.getHeight() <= MAX_HEIGHT_OF_RIDER;
    }

    /** The side of a bubble whose largest rider is {@code largest} blocks (width or height), holding {@code riders}. */
    public static float side(float largest, int riders) {
        return (largest + MARGIN) * (float) Math.cbrt(MathHelper.clamp(riders, 1, MAX_RIDERS));
    }

    /**
     * What its riders' movement keys add to its speed this tick: each player's keys turned his way (forward: where he
     * looks, on the level), added up and divided by how many ride it, mobs included.
     */
    public static Vec3d drift(List<Entity> riders) {
        if (riders.isEmpty()) return Vec3d.ZERO;
        double x = 0, z = 0;
        for (Entity rider : riders) {
            if (!(rider instanceof PlayerEntity player)) continue;
            Vec3d keys = keys(player.forwardSpeed, player.sidewaysSpeed, player.getYaw());
            x += keys.x;
            z += keys.z;
        }
        return new Vec3d(x, 0, z).multiply(DRIFT_ACCEL / riders.size());
    }

    /** One rider's keys ({@code forward}, {@code sideways}, each -1..1) turned to his {@code yaw}, at most 1 long. */
    public static Vec3d keys(float forward, float sideways, float yaw) {
        forward = MathHelper.clamp(forward, -1, 1);
        sideways = MathHelper.clamp(sideways, -1, 1);
        Vec3d input = new Vec3d(sideways, 0, forward);
        if (input.lengthSquared() > 1) input = input.normalize();
        float sin = MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE), cos = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE);
        return new Vec3d(input.x * cos - input.z * sin, 0, input.z * cos + input.x * sin);
    }

    /** A puff's strength for a charge (ticks): nothing under {@link #CHARGE_MIN}. */
    public static double puffStrength(int charge) {
        if (charge < CHARGE_MIN) return 0;
        double t = MathHelper.clamp((charge - CHARGE_MIN) / (double) (CHARGE_MAX - CHARGE_MIN), 0, 1);
        return PUFF_MIN + (PUFF_MAX - PUFF_MIN) * t;
    }

    /** The push of a puff of this charge along {@code look}, for a bubble of {@code riders}. */
    public static Vec3d puff(int charge, Vec3d look, int riders) {
        return look.normalize().multiply(puffStrength(charge) / Math.max(1, riders));
    }

    /**
     * The chance that a puff pops its bubble, strained {@code strain} (0..1) when it puffs: none up to
     * {@link #STRAIN_SAFE} (a single puff, or puffs a couple of seconds apart, never pop it), then growing fast.
     */
    public static float strainPopChance(float strain) {
        if (strain <= STRAIN_SAFE) return 0;
        return (float) Math.pow(Math.min(1, (strain - STRAIN_SAFE) / (1 - STRAIN_SAFE)), 1.3);
    }

    /** Whether a puff of a bubble strained {@code strain} pops it (drawn from {@code random}). */
    public static boolean strainedPuffPops(float strain, Random random) {
        float chance = strainPopChance(strain);
        return chance > 0 && random.nextFloat() < chance;
    }

    /** Its vertical speed this tick, hovering toward {@code target} from {@code y} at {@code vy}: rising fast, settling softly. */
    public static double hover(double y, double target, double vy) {
        double wanted = MathHelper.clamp((target - y) * HOVER_SPRING, -RISE_MAX / 2, RISE_MAX);
        return vy + (wanted - vy) * HOVER_EASE;
    }

    /** Its bob over its hover height at this age (ticks). */
    public static double bob(int age) {
        return BOB * Math.sin(age * BOB_SPEED);
    }

    /** How long a rider falls slowly once out, {@code drop} blocks above the ground. */
    public static int slowFallingTicks(double drop) {
        return MathHelper.clamp((int) Math.ceil(drop / SLOW_FALL_SPEED) + 20, SLOW_FALLING_MIN, SLOW_FALLING_MAX);
    }
}
