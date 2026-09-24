package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
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
 * named, leashed, teleported, just spawned), and the food flying into its mouth or being spat back.
 * <p>
 * Cheap: a few counters per tick; particles only for a Mula drawn in the last few ticks (off screen or too far:
 * nothing), through {@code World#addParticle}, which follows the particle setting and skips far particles. Particle
 * effects are shared constants, nothing is allocated per tick.
 */
public final class MulaEffects {
    /** Ticks for food to fly into the mouth / for a refused item to be spat back. */
    public static final int SWALLOW_TICKS = 8, SPIT_TICKS = 14;
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

    private final MulaEntity mula;
    /** Last age at which the Mula was drawn (set by the renderer). */
    public int lastRenderAge = -100;

    private int orbitTicks, cometTicks;

    /** The item flying in (swallowed) or out (refused); age -1 when none. */
    private ItemStack flyingStack = ItemStack.EMPTY;
    private int flyingAge = -1;
    private boolean flyingOut;
    private double flyFromX, flyFromY, flyFromZ;
    /** Ticks since the last food arrived in its belly (drives its little "plop" inside); large when long ago. */
    private int swallowedAge = 1000;

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

        if (flyingAge >= 0) {
            flyingAge++;
            if (!flyingOut && flyingAge == SWALLOW_TICKS) gulp();
            if (flyingAge > (flyingOut ? SPIT_TICKS : SWALLOW_TICKS)) flyingAge = -1;
        }
        if (swallowedAge < 1000) swallowedAge++;

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
            poof(lastServerX, lastServerY + mula.getHeight() * 0.8, lastServerZ);
            poof(serverX, serverY + mula.getHeight() * 0.8, serverZ);
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
                default -> { }
            }
        }
    }

    /** Fed (the feed counter changed): the food flies from the feeder's hand into its mouth. */
    void onFed() {
        ItemStack food = mula.getLastFood();
        if (food.isEmpty()) return;
        PlayerEntity feeder = mula.getWorld().getClosestPlayer(mula, 8);
        if (feeder != null) {
            float yaw = feeder.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            // roughly the right hand, a bit in front of the player
            flyFromX = feeder.getX() - MathHelper.sin(yaw) * 0.45 - MathHelper.cos(yaw) * 0.3;
            flyFromY = feeder.getEyeY() - 0.45;
            flyFromZ = feeder.getZ() + MathHelper.cos(yaw) * 0.45 - MathHelper.sin(yaw) * 0.3;
        } else {
            flyFromX = mula.getX();
            flyFromY = centerY() + 1;
            flyFromZ = mula.getZ();
        }
        flyingStack = food;
        flyingOut = false;
        flyingAge = 0;
    }

    /** Tamed (the vanilla hearts status): a ring of twinkles and a chime with the hearts. */
    void onTamed() {
        sparkleRing(14, 0.09);
        chime(1.6f);
    }

    private void gulp() {
        swallowedAge = 0;
        World world = mula.getWorld();
        double y = centerY();
        ParticleEffect crumbs = new ItemStackParticleEffect(ParticleTypes.ITEM, flyingStack);
        for (int i = 0; i < 5; i++) {
            world.addParticle(crumbs, mula.getX(), y, mula.getZ(), (mula.getRandom().nextDouble() - 0.5) * 0.1, 0.08,
                    (mula.getRandom().nextDouble() - 0.5) * 0.1);
        }
        for (int i = 0; i < 4; i++) {
            world.addParticle(mula.getVariant().getTwinkle(), mula.getX() + (mula.getRandom().nextDouble() - 0.5) * 0.5,
                    y + mula.getRandom().nextDouble() * 0.3, mula.getZ() + (mula.getRandom().nextDouble() - 0.5) * 0.5,
                    0, 0.02, 0);
        }
        sound(SoundEvents.ENTITY_ALLAY_ITEM_TAKEN, 0.5f, 1.3f);
    }

    /** "No": the item the nearest player holds out is spat back at them, with a little puff and a low boop. */
    private void refuse() {
        PlayerEntity player = mula.getWorld().getClosestPlayer(mula, 8);
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandStack();
        double y = centerY();
        poof(mula.getX(), y, mula.getZ());
        sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.4f, 0.7f);
        if (player == null || held.isEmpty()) return;
        flyingStack = held.copyWithCount(1);
        flyingOut = true;
        flyingAge = 0;
        flyFromX = player.getX();
        flyFromY = player.getEyeY() - 0.45;
        flyFromZ = player.getZ();
        sound(SoundEvents.ENTITY_ALLAY_ITEM_THROWN, 0.4f, 0.9f);
    }

    // ------------------------------------------------------------------------------------------ flying item (renderer)

    /** @return the item flying in or out, or EMPTY. */
    public ItemStack flyingStack() {
        return flyingAge >= 0 ? flyingStack : ItemStack.EMPTY;
    }

    /** 0..1 along its flight (partial tick included). */
    public float flyingProgress(float partialTick) {
        return MathHelper.clamp((flyingAge + partialTick) / (flyingOut ? SPIT_TICKS : SWALLOW_TICKS), 0f, 1f);
    }

    public boolean isFlyingOut() {
        return flyingOut;
    }

    /** Where the flight starts (in) or ends (out): the player's hand. */
    public double flyX() { return flyFromX; }
    public double flyY() { return flyFromY; }
    public double flyZ() { return flyFromZ; }

    /** Food in its belly is shown once it has arrived; this is the size of its "plop" (0 hidden, springs to 1). */
    public float bellyItemScale(float partialTick) {
        if (flyingAge >= 0 && !flyingOut) return 0f;
        float t = swallowedAge + partialTick;
        if (t >= 12) return 1f;
        // overshoots then settles, like a jelly swallowing it
        return (float) (1 - Math.exp(-t * 0.45) * Math.cos(t * 0.7));
    }

    // ------------------------------------------------------------------------------------------ particles

    /** Height of the middle of the model (it floats above its hitbox's feet: its centre is at 0.8 of its height). */
    private double centerY() {
        return mula.getY() + mula.getHeight() * 0.8;
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
        double y = mula.getY() + mula.getHeight() * 1.2;
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
                mula.getY() + mula.getHeight() * 1.3, mula.getZ() + MathHelper.cos(yaw) * 0.2, 0.004, 0.03, 0);
    }

    /** Hurt: it "sees stars", a few star bits knocked out of it. */
    private void seeStars() {
        World world = mula.getWorld();
        Random random = mula.getRandom();
        double y = mula.getY() + mula.getHeight() * 1.2;
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

    private void sound(SoundEvent sound, float volume, float pitch) {
        mula.getWorld().playSound(mula.getX(), centerY(), mula.getZ(), sound, SoundCategory.NEUTRAL, volume, pitch, false);
    }
}
