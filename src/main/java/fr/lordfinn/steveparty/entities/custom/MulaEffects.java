package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.Objects;

/**
 * Client-side feedback of one Mula: particles and quiet sounds started by its animations (timeline instructions, so
 * every player sees them at the same moment of the same synced animation) or by changes of its synced state (fed,
 * named, leashed, teleported, just spawned), and the food it absorbs (or refuses) as light.
 * <p>
 * Cheap: a few counters per tick; particles only for a Mula drawn in the last few ticks (off screen or too far:
 * nothing), through {@code World#addParticle}, which follows the particle setting and skips far particles. Particle
 * effects are shared constants, nothing is allocated per tick.
 */
public final class MulaEffects {
    /**
     * Absorbing a meal: the item rises and melts (ABSORB_RISE_TICKS), its MOTES leave it one after the other
     * (MOTE_START + MOTE_GAP * k) and spiral in for MOTE_TRAVEL ticks; all is in at ABSORB_TICKS, which is when the
     * synced "celebrate" swells (0.4 s blend + 0.85 s) and when MulaMotion lets its size and inner lights grow.
     */
    public static final int ABSORB_RISE_TICKS = 11, ABSORB_TICKS = 25;
    private static final int MOTES = 4, MOTE_START = 6, MOTE_GAP = 2, MOTE_TRAVEL = 13;
    /** Refusing: the item rises for REFUSE_TOP_TICKS, its motes puff away, it sinks back by REFUSE_TICKS. */
    private static final int REFUSE_TOP_TICKS = 14, REFUSE_TICKS = 36;
    /** Ticks of the orbiting lights (star_orbit) and of the comet tail (comet_loop, same timing as its loop). */
    private static final int ORBIT_TICKS = 44, COMET_TICKS = 20;
    /** Radius of the comet loop, in model pixels (mula_animations.py COMET_R). */
    private static final double COMET_RADIUS_PX = 4.0;
    /** A Mula drawn within this many ticks gets its particles. */
    private static final int VISIBLE_TICKS = 3;

    /** Star bits come in every colour, like a Luma's. */
    private static final MulaSparkleEffect[] RAINBOW_BITS = {
            bit(0xFFE45C), bit(0x7FD4FF), bit(0xFF8AD8), bit(0x8CFF8C), bit(0xC59BFF), bit(0xFFFFFF)};
    private static final MulaSparkleEffect WHITE_TWINKLE = new MulaSparkleEffect(0xFFFFFF, 1f, MulaSparkleEffect.TWINKLE);
    private static final MulaSparkleEffect BIG_WHITE_TWINKLE = new MulaSparkleEffect(0xFFFFFF, 1.6f, MulaSparkleEffect.TWINKLE);
    private static final MulaSparkleEffect SLEEPY_Z = new MulaSparkleEffect(0xDDEBFF, 1f, MulaSparkleEffect.Z);
    /** Pale motes that fail to merge (refused food). */
    private static final MulaSparkleEffect PALE_TWINKLE = new MulaSparkleEffect(0xD8D8E6, 0.8f, MulaSparkleEffect.TWINKLE);

    private final MulaEntity mula;
    /** Last age at which the Mula was drawn (set by the renderer). */
    public int lastRenderAge = -100;

    private int orbitTicks, cometTicks;

    /** The item being absorbed or refused; age -1 when none. Poses are kept in fields: nothing allocated per frame. */
    private ItemStack itemStack = ItemStack.EMPTY;
    private int itemAge = -1;
    private boolean itemRefused;
    private double handX, handY, handZ;
    private double meltX, meltY, meltZ;
    private double itemX, itemY, itemZ;
    private float itemScale, itemSpin;
    private double moteX, moteY, moteZ;
    private float moteSeed;

    // watchers of the synced state
    private boolean first = true;
    private Text lastName;
    private boolean wasLeashed;
    private double lastServerX, lastServerY, lastServerZ;

    MulaEffects(MulaEntity mula) {
        this.mula = mula;
    }

    private static MulaSparkleEffect bit(int color) {
        return new MulaSparkleEffect(color, 1f, MulaSparkleEffect.STAR_BIT);
    }

    private boolean visible() {
        return mula.age - lastRenderAge <= VISIBLE_TICKS;
    }

    // ------------------------------------------------------------------------------------------ per tick

    void tick(double serverX, double serverY, double serverZ) {
        World world = mula.getWorld();
        Random random = mula.getRandom();
        if (first) {
            first = false;
            lastName = mula.getCustomName();
            wasLeashed = mula.isLeashed();
            lastServerX = serverX;
            lastServerY = serverY;
            lastServerZ = serverZ;
            if (mula.isFresh()) {
                // just spawned (egg, summon...): pops in from nothing in a puff of sparkles
                mula.getMotion().popIn();
                sparkleRing(12, 0.07);
                sound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 0.35f, 1.6f);
                chime(1.8f);
            }
            return;
        }

        if (itemAge >= 0) tickItem(world);

        // named with a name tag, or leashed: a little twinkle of acknowledgement (the tools act as before)
        Text name = mula.getCustomName();
        if (!Objects.equals(name, lastName)) {
            lastName = name;
            if (name != null) {
                sparkleSmall();
                chime(1.9f);
            }
        }
        boolean leashed = mula.isLeashed();
        if (leashed && !wasLeashed) sparkleSmall();
        wasLeashed = leashed;

        // caught up with its owner by teleporting: a puff where it left and where it lands
        double jumpSq = (serverX - lastServerX) * (serverX - lastServerX) + (serverY - lastServerY) * (serverY - lastServerY)
                + (serverZ - lastServerZ) * (serverZ - lastServerZ);
        if (jumpSq > 16) {
            poof(lastServerX, lastServerY + mula.getHeight() * MulaEntity.CENTER, lastServerZ);
            poof(serverX, serverY + mula.getHeight() * MulaEntity.CENTER, serverZ);
            chime(1.5f);
        }
        lastServerX = serverX;
        lastServerY = serverY;
        lastServerZ = serverZ;

        if (!visible()) {
            orbitTicks = cometTicks = 0;
            return;
        }
        double cx = mula.getX(), cy = centerY(), cz = mula.getZ();
        float size = mula.getScaleFactor();

        if (orbitTicks > 0) {
            // three little lights circling round it, leaving short trails
            orbitTicks--;
            double a0 = (ORBIT_TICKS - orbitTicks) * 0.28;
            double r = 0.3 + 0.35 * size;
            for (int i = 0; i < 3; i++) {
                double a = a0 + i * MathHelper.TAU / 3;
                world.addParticle(i == 0 ? WHITE_TWINKLE : mula.getVariant().getTwinkle(), cx + Math.cos(a) * r,
                        cy + 0.12 * Math.sin(a * 2) * size, cz + Math.sin(a) * r, 0, 0, 0);
            }
        }
        if (cometTicks > 0) {
            // a comet tail along the loop-the-loop the body draws (same circle and timing as the animation)
            cometTicks--;
            double radius = COMET_RADIUS_PX / 16.0 * size;
            float yaw = mula.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
            double fx = -MathHelper.sin(yaw), fz = MathHelper.cos(yaw);
            for (int half = 0; half < 2; half++) {
                double u = (COMET_TICKS - cometTicks - 0.5 * half) / COMET_TICKS;
                double s = MathHelper.clamp(u, 0, 1);
                double theta = MathHelper.TAU * s * s * (3 - 2 * s);
                double forward = radius * Math.sin(theta), up = radius * (1 - Math.cos(theta));
                world.addParticle(half == 0 ? BIG_WHITE_TWINKLE : mula.getVariant().getTwinkle(),
                        cx + fx * forward, cy + up, cz + fz * forward, 0, 0, 0);
            }
            world.addParticle(mula.starDust(), cx, cy, cz, 0, 0, 0);
        }

        // so full it leaks light: a twinkle now and then, more and more often until it bursts
        float full = mula.getMotion().fullness(1f);
        if (full > 0.7f && random.nextFloat() < (full - 0.7f) * 1.2f) {
            double a = random.nextDouble() * MathHelper.TAU;
            double r = 0.3 * size;
            world.addParticle(mula.getVariant().getTwinkle(), cx + Math.cos(a) * r, cy + (random.nextDouble() - 0.3) * r,
                    cz + Math.sin(a) * r, Math.cos(a) * 0.03, 0.02, Math.sin(a) * 0.03);
        }
    }

    // ------------------------------------------------------------------------------------------ events

    /** Timeline instructions of the animations. */
    void instruction(String instructions) {
        for (String raw : instructions.split(";")) {
            switch (raw.trim()) {
                case "sparkle_small" -> sparkleSmall();
                case "sparkle_ring" -> sparkleRing(10, 0.07);
                case "burst" -> burst();
                case "chime" -> chime(1.35f + mula.getRandom().nextFloat() * 0.4f);
                case "orbit" -> orbitTicks = ORBIT_TICKS;
                case "comet" -> cometTicks = COMET_TICKS;
                case "shower" -> shower(22);
                case "ring" -> ring();
                case "glow" -> mula.getMotion().flare();
                case "z" -> sleepyZ();
                case "refuse" -> refuse();
                case "ouch" -> seeStars();
                case "glow_in" -> shimmer();
                default -> { }
            }
        }
    }

    /**
     * Fed (the meal counter changed): the food rises gently from the feeder's hand, turns slowly, shrinks and melts into
     * motes of light in the Mula's colour that spiral round it and sink into its body. No mouth, no gulp: like a Luma
     * taking in star bits. The body answers with the synced "celebrate" (inhale, shimmer, warm swell), timed so the
     * motes arrive when it swells; its size and inner lights grow at that moment (MulaMotion holds them till then).
     */
    void onFed() {
        ItemStack food = mula.getLastFood();
        if (food.isEmpty()) return;
        startItem(food, false);
        chime(1.9f, 0.2f);
    }

    /** Tamed (the vanilla hearts status): a ring of twinkles and a chime with the hearts. */
    void onTamed() {
        sparkleRing(14, 0.09);
        chime(1.6f);
    }

    /**
     * "No" (not its food, or still taking the last one in): the item held out floats up a little towards it, its motes
     * try to reach the Mula but can't merge and puff away, and the item drifts softly back down to the player's hand.
     */
    private void refuse() {
        PlayerEntity player = mula.getWorld().getClosestPlayer(mula, 8);
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandStack();
        if (held.isEmpty()) {
            sound(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 0.3f, 0.7f);
            return;
        }
        startItem(held.copyWithCount(1), true);
    }

    private void startItem(ItemStack stack, boolean refused) {
        PlayerEntity player = mula.getWorld().getClosestPlayer(mula, 8);
        if (player != null) {
            float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            // roughly the right hand, a bit in front of the player
            handX = player.getX() - MathHelper.sin(yaw) * 0.45 - MathHelper.cos(yaw) * 0.3;
            handY = player.getEyeY() - 0.45;
            handZ = player.getZ() + MathHelper.cos(yaw) * 0.45 - MathHelper.sin(yaw) * 0.3;
        } else {
            handX = mula.getX();
            handY = centerY() + 1;
            handZ = mula.getZ();
        }
        itemStack = stack;
        itemRefused = refused;
        itemAge = 0;
        moteSeed = mula.getRandom().nextFloat() * MathHelper.TAU;
    }

    /** Per tick while the item lives: its dissolving glitter, the spiral of motes, the arrival or the refusal. */
    private void tickItem(World world) {
        itemAge++;
        int end = itemRefused ? REFUSE_TICKS : ABSORB_TICKS;
        if (itemAge > end) {
            itemAge = -1;
            return;
        }
        boolean visible = visible();
        double cx = mula.getX(), cy = centerY(), cz = mula.getZ();
        itemPose(1f, cx, cy, cz);
        if (!itemRefused) {
            // the item melts: glitter falling off it while it rises and shrinks
            if (visible && itemAge <= ABSORB_RISE_TICKS) {
                world.addParticle(mula.getVariant().getTwinkle(), itemX + jitter(0.12), itemY + jitter(0.12),
                        itemZ + jitter(0.12), 0, 0.01, 0);
            }
            // then its motes spiral in, one after the other, leaving short trails (two points per tick)
            if (visible) {
                for (int k = 0; k < MOTES; k++) {
                    for (int half = 0; half < 2; half++) {
                        double u = (itemAge - 0.5 * half - MOTE_START - MOTE_GAP * k) / (double) MOTE_TRAVEL;
                        if (u < 0 || u > 1) continue;
                        motePoint(u, k, cx, cy, cz);
                        world.addParticle(k % 2 == 0 ? mula.getVariant().getTwinkle() : WHITE_TWINKLE, moteX, moteY, moteZ,
                                0, 0, 0);
                    }
                }
            }
            // a soft arpeggio of chimes while the light comes in
            if (itemAge == 9 || itemAge == 13 || itemAge == 17 || itemAge == 21) {
                chime(1.15f + (itemAge - 9) * 0.05f, 0.16f);
            }
            if (itemAge == ABSORB_TICKS) {
                // the light has sunk in: a warm glow spreads from its heart
                mula.getMotion().absorbGlow();
                sound(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 0.35f, 1.5f);
                chime(2.0f, 0.22f);
            }
        } else {
            // its motes try to reach the Mula but bounce off and puff away
            if (visible && itemAge >= 5 && itemAge <= REFUSE_TOP_TICKS) {
                double u = (itemAge - 5) / (double) (REFUSE_TOP_TICKS - 5);
                double px = MathHelper.lerp(u * 0.6, itemX, cx), py = MathHelper.lerp(u * 0.6, itemY, cy);
                double pz = MathHelper.lerp(u * 0.6, itemZ, cz);
                world.addParticle(PALE_TWINKLE, px + jitter(0.1), py + jitter(0.1), pz + jitter(0.1), 0, 0, 0);
            }
            if (itemAge == REFUSE_TOP_TICKS) {
                if (visible) {
                    for (int i = 0; i < 8; i++) {
                        double a = MathHelper.TAU * i / 8 + moteSeed;
                        world.addParticle(PALE_TWINKLE, itemX, itemY, itemZ, Math.cos(a) * 0.09, 0.03 + Math.sin(a) * 0.05,
                                Math.sin(a) * 0.09);
                    }
                }
                sound(SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT, 0.35f, 0.6f);
                chime(0.8f, 0.15f);
            }
        }
    }

    private double jitter(double r) {
        return (mula.getRandom().nextDouble() - 0.5) * 2 * r;
    }

    /**
     * Point of mote k at u (0..1) of its way: from where the item melted to the Mula's heart, swirling round the way on
     * a spiral whose radius opens then closes, turning 1.25 times. Written into moteX/Y/Z (no allocation).
     */
    private void motePoint(double u, int k, double cx, double cy, double cz) {
        double e = u * u * (3 - 2 * u);
        double size = mula.getScaleFactor();
        double sx = MathHelper.lerp(e, meltX, cx), sy = MathHelper.lerp(e, meltY, cy), sz = MathHelper.lerp(e, meltZ, cz);
        double radius = (0.25 + 0.35 * size) * Math.sin(Math.PI * Math.min(1, u * 1.15)) * (1 - 0.35 * u);
        double a = moteSeed + k * MathHelper.TAU / MOTES + u * 2.5 * Math.PI;
        moteX = sx + Math.cos(a) * radius;
        moteY = sy + 0.25 * Math.sin(Math.PI * u) + Math.sin(a * 0.5) * radius * 0.35;
        moteZ = sz + Math.sin(a) * radius;
    }

    /**
     * Pose of the item (absorbed or refused) at partialTick, into itemX/Y/Z, itemScale, itemSpin. Absorbed: it rises
     * and drifts a third of the way, turning slowly, and shrinks to nothing by ABSORB_RISE_TICKS (where it melted is
     * remembered for the motes). Refused: it rises towards the Mula, hesitates, and sinks back to the hand.
     */
    private void itemPose(float partialTick, double cx, double cy, double cz) {
        float age = itemAge - 1 + partialTick;
        if (!itemRefused) {
            double p = MathHelper.clamp(age / ABSORB_RISE_TICKS, 0, 1);
            double e = 1 - (1 - p) * (1 - p);
            itemX = MathHelper.lerp(e * 0.35, handX, cx);
            itemY = MathHelper.lerp(e * 0.35, handY, cy) + 0.45 * e;
            itemZ = MathHelper.lerp(e * 0.35, handZ, cz);
            double shrink = MathHelper.clamp((p - 0.35) / 0.65, 0, 1);
            itemScale = (float) (0.6 * (1 - shrink * shrink * (3 - 2 * shrink)));
            itemSpin = (float) (p * 160);
            if (age <= ABSORB_RISE_TICKS * 0.7) {
                meltX = itemX;
                meltY = itemY;
                meltZ = itemZ;
            }
        } else {
            double up = MathHelper.clamp(age / REFUSE_TOP_TICKS, 0, 1);
            double down = MathHelper.clamp((age - REFUSE_TOP_TICKS - 3) / (REFUSE_TICKS - REFUSE_TOP_TICKS - 3), 0, 1);
            double e = up * up * (3 - 2 * up) * (1 - down * down * (3 - 2 * down));
            itemX = MathHelper.lerp(e * 0.45, handX, cx);
            itemY = MathHelper.lerp(e * 0.45, handY, cy) + 0.5 * e;
            itemZ = MathHelper.lerp(e * 0.45, handZ, cz);
            // a little hesitant wobble at the top, then it fades away into the hand
            itemSpin = (float) (Math.sin(age * 0.5) * 25 * e);
            itemScale = (float) (0.6 * (1 - MathHelper.clamp((down - 0.75) / 0.25, 0, 1)));
        }
    }

    // ------------------------------------------------------------------------------------------ item (renderer)

    /** @return the item being absorbed or refused, or EMPTY. */
    public ItemStack itemStack() {
        return itemAge >= 0 ? itemStack : ItemStack.EMPTY;
    }

    /** Updates the item's pose for this frame; read it with itemX/Y/Z, itemScale, itemSpin. */
    public void updateItemPose(float partialTick) {
        double cx = MathHelper.lerp(partialTick, mula.prevX, mula.getX());
        double cy = MathHelper.lerp(partialTick, mula.prevY, mula.getY()) + mula.getHeight() * MulaEntity.CENTER;
        double cz = MathHelper.lerp(partialTick, mula.prevZ, mula.getZ());
        itemPose(partialTick, cx, cy, cz);
    }

    public double itemX() { return itemX; }
    public double itemY() { return itemY; }
    public double itemZ() { return itemZ; }
    public float itemScale() { return itemScale; }
    public float itemSpin() { return itemSpin; }

    // ------------------------------------------------------------------------------------------ particles

    /** Height of the middle of the model (the model fills its hitbox: its centre is half way up). */
    private double centerY() {
        return mula.getY() + mula.getHeight() * MulaEntity.CENTER;
    }

    void sparkleSmall() {
        World world = mula.getWorld();
        Random random = mula.getRandom();
        double y = centerY();
        for (int i = 0; i < 5; i++) {
            world.addParticle(ParticleTypes.WAX_OFF, mula.getX() + (random.nextDouble() - 0.5) * 0.8,
                    y + random.nextDouble() * 0.5, mula.getZ() + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        }
        world.addParticle(mula.starDust(), mula.getX(), y + 0.3, mula.getZ(), 0, 0.02, 0);
    }

    /** The favourite: little four-pointed twinkles flying out in a ring, star dust between them, glowing motes. */
    void sparkleRing(int count, double speed) {
        World world = mula.getWorld();
        Random random = mula.getRandom();
        double y = centerY();
        for (int i = 0; i < count; i++) {
            double a = MathHelper.TAU * i / count + random.nextDouble() * 0.3;
            double cos = Math.cos(a), sin = Math.sin(a);
            world.addParticle(ParticleTypes.WAX_OFF, mula.getX() + cos * 0.3, y, mula.getZ() + sin * 0.3,
                    cos * speed * 40, 1.0, sin * speed * 40); // WAX_OFF scales its velocity down (x0.005 sideways)
            if (i % 2 == 0) {
                world.addParticle(mula.starDust(), mula.getX() + cos * 0.5, y + 0.1, mula.getZ() + sin * 0.5, 0, 0, 0);
            }
        }
        for (int i = 0; i < 2; i++) {
            world.addParticle(ParticleTypes.END_ROD, mula.getX() + (random.nextDouble() - 0.5) * 0.4, y + 0.2,
                    mula.getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0.03, 0);
        }
    }

    /** Star bits of every colour thrown up, raining down around it. */
    private void shower(int count) {
        World world = mula.getWorld();
        Random random = mula.getRandom();
        double y = mula.getY() + mula.getHeight() * 0.85;
        for (int i = 0; i < count; i++) {
            world.addParticle(RAINBOW_BITS[i % RAINBOW_BITS.length], mula.getX(), y, mula.getZ(),
                    random.nextGaussian() * 0.07, 0.22 + random.nextDouble() * 0.16, random.nextGaussian() * 0.07);
        }
        for (int i = 0; i < 6; i++) {
            world.addParticle(WHITE_TWINKLE, mula.getX() + random.nextGaussian() * 0.3, y + random.nextDouble() * 0.4,
                    mula.getZ() + random.nextGaussian() * 0.3, 0, 0.02, 0);
        }
    }

    /** A ring of light spreading out flat around it. */
    private void ring() {
        World world = mula.getWorld();
        double y = centerY();
        int count = 16;
        for (int i = 0; i < count; i++) {
            double a = MathHelper.TAU * i / count;
            world.addParticle(i % 2 == 0 ? WHITE_TWINKLE : mula.getVariant().getTwinkle(), mula.getX() + Math.cos(a) * 0.3,
                    y, mula.getZ() + Math.sin(a) * 0.3, Math.cos(a) * 0.16, 0, Math.sin(a) * 0.16);
        }
        mula.getMotion().flare();
    }

    private void sleepyZ() {
        float yaw = mula.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        mula.getWorld().addParticle(SLEEPY_Z, mula.getX() - MathHelper.sin(yaw) * 0.2 + 0.1,
                mula.getY() + mula.getHeight() * 0.95, mula.getZ() + MathHelper.cos(yaw) * 0.2, 0.004, 0.03, 0);
    }

    /** Hurt: it "sees stars", a few star bits knocked out of it. */
    private void seeStars() {
        World world = mula.getWorld();
        Random random = mula.getRandom();
        double y = mula.getY() + mula.getHeight() * 0.85;
        for (int i = 0; i < 3; i++) {
            world.addParticle(RAINBOW_BITS[i == 2 ? 5 : 0], mula.getX(), y, mula.getZ(),
                    random.nextGaussian() * 0.06, 0.18, random.nextGaussian() * 0.06);
        }
    }

    /** Full: the burst, now with a shower of star bits and a firework twinkle. */
    private void burst() {
        World world = mula.getWorld();
        Random random = mula.getRandom();
        double y = centerY();
        world.addParticle(ParticleTypes.FLASH, mula.getX(), y, mula.getZ(), 0, 0, 0);
        for (int i = 0; i < 24; i++) {
            double vx = random.nextGaussian() * 0.12, vy = random.nextGaussian() * 0.12 + 0.05, vz = random.nextGaussian() * 0.12;
            world.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.WAX_OFF, mula.getX(), y, mula.getZ(), vx, vy, vz);
            world.addParticle(mula.starDust(), mula.getX() + vx * 6, y + vy * 6, mula.getZ() + vz * 6, 0, 0, 0);
        }
        shower(30);
        sound(SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.6f, 1.2f);
    }

    private void poof(double x, double y, double z) {
        World world = mula.getWorld();
        Random random = mula.getRandom();
        for (int i = 0; i < 3; i++) {
            world.addParticle(ParticleTypes.SMOKE, x + random.nextGaussian() * 0.12, y + random.nextGaussian() * 0.12,
                    z + random.nextGaussian() * 0.12, 0, 0.02, 0);
        }
        for (int i = 0; i < 4; i++) {
            world.addParticle(mula.getVariant().getTwinkle(), x + random.nextGaussian() * 0.25,
                    y + random.nextGaussian() * 0.25, z + random.nextGaussian() * 0.25, 0, 0.01, 0);
        }
    }

    void chime(float pitch) {
        sound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, pitch);
    }

    private void chime(float pitch, float volume) {
        sound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, volume, pitch);
    }

    /** The happy shimmer when the light sinks in: tiny twinkles rising all over its body. */
    private void shimmer() {
        if (!visible()) return;
        World world = mula.getWorld();
        double r = 0.18 * mula.getScaleFactor() + 0.1;
        for (int i = 0; i < 9; i++) {
            double a = mula.getRandom().nextDouble() * MathHelper.TAU;
            world.addParticle(i % 3 == 0 ? WHITE_TWINKLE : mula.getVariant().getTwinkle(), mula.getX() + Math.cos(a) * r,
                    centerY() + jitter(r), mula.getZ() + Math.sin(a) * r, 0, 0.025, 0);
        }
    }

    private void sound(SoundEvent sound, float volume, float pitch) {
        mula.getWorld().playSound(mula.getX(), centerY(), mula.getZ(), sound, SoundCategory.NEUTRAL, volume, pitch, false);
    }
}
