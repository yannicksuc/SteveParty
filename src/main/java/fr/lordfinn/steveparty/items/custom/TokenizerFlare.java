package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.particles.KamekShapeEffect;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.Arm;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Tokenizer Wand's homing flare: holding the use button in the air, a small comet of Kamek magic flies from the
 * wand and curves towards the nearest mob the spell could take (within {@link #RANGE} blocks, in sight, the one
 * closest to where the player looks preferred). Reaching it while the button is still held opens the spell on it
 * (the held press then goes on drawing on the client); released early, or without a target, it fizzles out.
 * <p>
 * Server side: the server picks the target and moves the flare; everyone around sees it as particles.
 */
public final class TokenizerFlare {
    /** Search range, in blocks (the spell reaches {@link TokenizerWandItem#MAX_SPELL_DISTANCE}). */
    public static final double RANGE = 32;
    /** Angle penalty of the target choice: a mob right behind costs this many blocks more than one straight ahead. */
    private static final double LOOK_WEIGHT = 8;
    private static final double START_SPEED = 0.45, MAX_SPEED = 1.3, ACCELERATION = 0.06, STEERING = 0.3;
    /** Without a target, the flare flies this long, then fizzles. */
    private static final int LONELY_TICKS = 8;
    private static final int MAX_TICKS = 60;

    private static final Map<UUID, Flare> FLARES = new HashMap<>();

    private static final class Flare {
        Vec3d position, velocity;
        final MobEntity target;
        int age;

        Flare(Vec3d position, Vec3d velocity, MobEntity target) {
            this.position = position;
            this.velocity = velocity;
            this.target = target;
        }
    }

    private TokenizerFlare() {
    }

    /**
     * The mob the flare goes to: among the mobs the spell could take within {@link #RANGE} blocks and in sight, the
     * one with the best score (distance, plus a penalty growing as it is away from where the player looks), or null.
     */
    public static MobEntity chooseTarget(ServerPlayerEntity player, ItemStack wand) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVector();
        List<MobEntity> mobs = player.getWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(RANGE),
                mob -> mob.squaredDistanceTo(eye) <= RANGE * RANGE && TokenizerWandItem.isSpellTarget(player, wand, mob)
                        && player.canSee(mob));
        return mobs.stream().min(Comparator.comparingDouble(mob -> {
            Vec3d to = mob.getPos().add(0, mob.getHeight() / 2, 0).subtract(eye);
            double distance = to.length();
            double facing = distance < 1.0E-3 ? 1 : look.dotProduct(to.multiply(1 / distance));
            return distance + LOOK_WEIGHT * (1 - facing);
        })).orElse(null);
    }

    /** About where the wand's tip is: in front of the player, on the side of the hand, below the eyes. */
    private static Vec3d wandTip(ServerPlayerEntity player) {
        boolean mainHand = player.getMainHandStack().getItem() instanceof TokenizerWandItem;
        double side = (player.getMainArm() == Arm.RIGHT) == mainHand ? 1 : -1;
        float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return player.getEyePos().add(player.getRotationVector().multiply(0.6))
                .add(-MathHelper.cos(yaw) * 0.3 * side, -0.2, -MathHelper.sin(yaw) * 0.3 * side);
    }

    public static void start(ServerPlayerEntity player, ItemStack wand) {
        MobEntity target = chooseTarget(player, wand);
        Vec3d tip = wandTip(player);
        FLARES.put(player.getUuid(), new Flare(tip, player.getRotationVector().multiply(START_SPEED), target));
        player.getWorld().playSound(null, tip.x, tip.y, tip.z, ModSounds.TOKEN_SPELL_FLARE, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /** Every tick while the button is held: the flare homes in, and opens the spell when it reaches its mob. */
    public static void tick(ServerPlayerEntity player, ItemStack wand) {
        Flare flare = FLARES.get(player.getUuid());
        if (flare == null || !(player.getWorld() instanceof ServerWorld world)) return;
        flare.age++;
        MobEntity target = flare.target;
        if (target == null || !target.isAlive() || target.getWorld() != world) {
            if (flare.age >= LONELY_TICKS) {
                fizzle(player);
                return;
            }
        } else {
            Vec3d center = target.getPos().add(0, target.getHeight() / 2, 0);
            Vec3d to = center.subtract(flare.position);
            double distance = to.length();
            if (distance < Math.max(0.6, target.getWidth() / 2 + 0.3)) {
                FLARES.remove(player.getUuid());
                burst(world, center);
                player.stopUsingItem();
                TokenizerWandItem.openSpell(player, target);
                return;
            }
            // Curves towards the mob, faster and faster
            double speed = Math.min(MAX_SPEED, flare.velocity.length() + ACCELERATION);
            Vec3d wanted = to.multiply(Math.min(speed, distance) / distance);
            flare.velocity = flare.velocity.lerp(wanted, STEERING);
        }
        if (flare.age > MAX_TICKS) {
            fizzle(player);
            return;
        }
        Vec3d previous = flare.position;
        flare.position = flare.position.add(flare.velocity);
        trail(world, previous, flare.position);
    }

    /** Released early (or nothing to reach): the flare fizzles out in sparkles. */
    public static void fizzle(ServerPlayerEntity player) {
        Flare flare = FLARES.remove(player.getUuid());
        if (flare == null || !(player.getWorld() instanceof ServerWorld world)) return;
        Vec3d at = flare.position;
        world.spawnParticles(KamekShapeEffect.sparkle(0.9F, 0.8F, 0, KamekShapeEffect.RANDOM_COLOR), at.x, at.y, at.z,
                8, 0.15, 0.15, 0.15, 0.05);
        world.playSound(null, at.x, at.y, at.z, ModSounds.TOKEN_SPELL_FIZZLE, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /** The comet: a bright sparkle head and a few shapes left behind along the way. */
    private static void trail(ServerWorld world, Vec3d from, Vec3d to) {
        world.spawnParticles(KamekShapeEffect.sparkle(1.6F, 0F, 4, 0xFFFFFF), to.x, to.y, to.z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            Vec3d at = from.lerp(to, i / 3.0);
            world.spawnParticles(KamekShapeEffect.shape(0.7F, 0.85F, 10), at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private static void burst(ServerWorld world, Vec3d at) {
        world.spawnParticles(KamekShapeEffect.shape(1.1F, 0.8F, 0), at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.2);
    }

    /** A player's flare is forgotten when they leave. */
    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FLARES.remove(handler.player.getUuid()));
    }
}
