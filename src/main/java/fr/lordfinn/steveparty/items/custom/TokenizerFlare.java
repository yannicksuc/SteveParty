package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.entities.TokenBase;
import fr.lordfinn.steveparty.particles.KamekShapeEffect;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Arm;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Tokenizer Wand's flare: holding the use button in the air, a small comet of Kamek magic flies from the wand in a
 * straight line, along where the player looks, up to {@link #RANGE} blocks. If it hits a mob the spell could take,
 * the spell opens on it (the held press then goes on drawing on the client); if it hits a block, another mob, or
 * reaches its range, or if the button is released before, it fizzles out.
 * <p>
 * Server side and authoritative: the server moves the flare and tests what it hits; everyone around sees it as
 * particles.
 */
public final class TokenizerFlare {
    /** Range, in blocks (the spell reaches {@link TokenizerWandItem#MAX_SPELL_DISTANCE}). */
    public static final double RANGE = 32;
    /** Blocks per tick: the whole range in about a second. */
    private static final double SPEED = 1.6;

    private static final Map<UUID, Flare> FLARES = new HashMap<>();

    private static final class Flare {
        Vec3d position;
        final Vec3d direction;
        double travelled;

        Flare(Vec3d position, Vec3d direction) {
            this.position = position;
            this.direction = direction;
        }
    }

    /** What a stretch of the flare's flight hits first. */
    public record Hit(MobEntity mob, Vec3d at, boolean stops) {
        static final Hit NOTHING = new Hit(null, null, false);
    }

    private TokenizerFlare() {
    }

    /** A player's flare is forgotten when they leave. */
    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            FLARES.remove(handler.player.getUuid());
            TokenizerWandItem.spellClosed(handler.player.getUuid());
        });
    }

    /**
     * What the flare hits flying from {@code from} to {@code to}: the first mob whose hitbox the segment crosses (if
     * the spell could take it, {@link Hit#mob} is set; any mob stops it), or the first block (stops it), whichever
     * comes first; else nothing.
     */
    public static Hit trace(World world, ServerPlayerEntity player, ItemStack wand, Vec3d from, Vec3d to) {
        BlockHitResult block = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        Vec3d end = block.getType() == HitResult.Type.MISS ? to : block.getPos();
        MobEntity nearest = null;
        Vec3d nearestAt = null;
        double nearestDistance = Double.MAX_VALUE;
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, new Box(from, end).expand(1), MobEntity::isAlive)) {
            Optional<Vec3d> at = mob.getBoundingBox().expand(0.2).raycast(from, end);
            if (at.isPresent() && from.squaredDistanceTo(at.get()) < nearestDistance) {
                nearestDistance = from.squaredDistanceTo(at.get());
                nearest = mob;
                nearestAt = at.get();
            }
        }
        if (nearest != null) {
            if (!TokenBase.isToken(nearest) && TokenizerWandItem.isBoss(nearest)) TokenizerWandItem.sendBossRefused(player, nearest);
            return new Hit(TokenizerWandItem.isSpellTarget(player, wand, nearest) ? nearest : null, nearestAt, true);
        }
        return block.getType() == HitResult.Type.MISS ? Hit.NOTHING : new Hit(null, end, true);
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
        Vec3d tip = wandTip(player);
        // Straight where the player looks: aimed at what the crosshair points at, from the wand's tip
        Vec3d aim = player.getEyePos().add(player.getRotationVector().multiply(RANGE));
        FLARES.put(player.getUuid(), new Flare(tip, aim.subtract(tip).normalize()));
        player.getWorld().playSound(null, tip.x, tip.y, tip.z, ModSounds.TOKEN_SPELL_FLARE, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    /** Every tick while the button is held: the flare flies on, and opens the spell on the mob it hits. */
    public static void tick(ServerPlayerEntity player, ItemStack wand) {
        Flare flare = FLARES.get(player.getUuid());
        if (flare == null || !(player.getWorld() instanceof ServerWorld world)) return;
        double step = Math.min(SPEED, RANGE - flare.travelled);
        Vec3d next = flare.position.add(flare.direction.multiply(step));
        Hit hit = trace(world, player, wand, flare.position, next);
        Vec3d reached = hit.stops() ? hit.at() : next;
        trail(world, flare.position, reached);
        flare.travelled += flare.position.distanceTo(reached);
        flare.position = reached;
        if (hit.mob() != null) {
            FLARES.remove(player.getUuid());
            burst(world, reached);
            player.stopUsingItem();
            TokenizerWandItem.openSpell(player, hit.mob());
        } else if (hit.stops() || flare.travelled >= RANGE - 1.0E-3) {
            fizzle(player);
        }
    }

    /** Released early, or it hit nothing it could take: the flare fizzles out in sparkles. */
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
}
