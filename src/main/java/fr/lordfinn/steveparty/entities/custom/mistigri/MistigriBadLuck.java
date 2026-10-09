package fr.lordfinn.steveparty.entities.custom.mistigri;

import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The Mistigri's bad luck, and the luck of his owner.
 * <ul>
 *     <li><b>Crossing his path</b>: a player passing from one side of his line of sight to the other, between
 *     {@link #MIN_AHEAD} and {@link #AHEAD} blocks in front of him, gets Bad Luck for {@link #UNLUCK_TICKS}. The line
 *     is taken when they come in front of him ({@link Watch}): turning his head to follow them never counts as a
 *     crossing. Once per {@link #COOLDOWN} per player; never his owner.</li>
 *     <li><b>His owner's luck</b>: a tamed one within {@link #LUCK_RANGE} blocks of his owner keeps Luck on them.</li>
 *     <li><b>Monsters miss</b>: a monster within {@link #MISS_RANGE} blocks of a tamed Mistigri misses one blow in
 *     {@link #MISS_CHANCE} (the blow does nothing, a puff of witch sparks).</li>
 *     <li><b>Chests</b>: a chest, barrel or ender chest he sits on won't open.</li>
 * </ul>
 */
public final class MistigriBadLuck {
    /** His path: from this far ahead to this far (blocks). */
    public static final double MIN_AHEAD = 0.4, AHEAD = 5.0;
    /** Half the width of the band in front of him where a crossing counts (blocks each side of his line). */
    public static final double BAND = 2.0;
    /** Bad Luck for one minute; once every 5 seconds at most per player. */
    public static final int UNLUCK_TICKS = 1200, COOLDOWN = 100;
    /** A watch lasts this long at most (a player standing in front of him too long starts a new one). */
    static final int WATCH_TICKS = 80;
    public static final double LUCK_RANGE = 16.0;
    public static final double MISS_RANGE = 10.0;
    public static final int MISS_CHANCE = 3;

    /** A player seen in front of him: his line then (origin, facing, right), and on which side they were. */
    record Watch(Vec3d origin, Vec3d facing, Vec3d right, double side, long since) {
    }

    private MistigriBadLuck() {
    }

    public static void initialize() {
        // monsters near a tamed Mistigri miss now and then
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(source.getAttacker() instanceof Monster) || !(entity.getWorld() instanceof ServerWorld world)) return true;
            Entity attacker = source.getAttacker();
            if (!nearTamed(world, attacker.getPos())) return true;
            if (world.getRandom().nextInt(MISS_CHANCE) != 0) return true;
            missed(world, entity);
            return false;
        });
        // a chest he sits on won't open
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player.isSpectator()) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            if (!isSeat(world.getBlockState(pos).getBlock()) || sitter(world, pos) == null) return ActionResult.PASS;
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                MessageUtils.sendToPlayer(serverPlayer, Text.translatable("message.steveparty.mistigri.on_chest")
                        .formatted(Formatting.DARK_PURPLE), MessageUtils.MessageType.ACTION_BAR);
                world.playSound(null, pos, ModSounds.MISTIGRI_HISS, SoundCategory.NEUTRAL, 0.6f, 1.1f);
            }
            return ActionResult.FAIL;
        });
    }

    /** A block he may sit on, which then won't open. */
    public static boolean isSeat(Block block) {
        return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof EnderChestBlock;
    }

    /** The Mistigri sitting on the chest at {@code pos} (or on the other half of a double chest), null if none. */
    public static MistigriEntity sitter(World world, BlockPos pos) {
        for (MistigriEntity mistigri : world.getEntitiesByClass(MistigriEntity.class, new Box(pos.up()).expand(1.0, 0.5, 1.0),
                MistigriEntity::isAlive)) {
            BlockPos chest = mistigri.chest();
            if (chest == null && !mistigri.isLoafing()) continue;
            if (chest == null) chest = mistigri.getBlockPos().down();
            if (chest.equals(pos)) return mistigri;
            for (Direction side : Direction.Type.HORIZONTAL) {
                if (chest.equals(pos.offset(side)) && world.getBlockState(chest).getBlock() == world.getBlockState(pos).getBlock()
                        && world.getBlockState(pos).getBlock() instanceof ChestBlock) {
                    return mistigri;
                }
            }
        }
        return null;
    }

    /** Whether a tamed Mistigri is within {@link #MISS_RANGE} blocks of {@code at}. */
    public static boolean nearTamed(ServerWorld world, Vec3d at) {
        return !world.getEntitiesByClass(MistigriEntity.class, new Box(at, at).expand(MISS_RANGE),
                mistigri -> mistigri.isAlive() && mistigri.isTamed() && !mistigri.isBoardActor()
                        && mistigri.squaredDistanceTo(at) <= MISS_RANGE * MISS_RANGE).isEmpty();
    }

    private static void missed(ServerWorld world, Entity target) {
        Vec3d at = target.getPos().add(0, target.getHeight() * 0.6, 0);
        world.spawnParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.05);
        world.playSound(null, at.x, at.y, at.z, ModSounds.MISTIGRI_BAD_LUCK, SoundCategory.HOSTILE, 0.5f, 1.4f);
    }

    /** Keeps Luck on his owner while near him (refreshed every second, a little longer than that). */
    public static void giveLuck(ServerWorld world, MistigriEntity mistigri) {
        if (!(mistigri.getOwner() instanceof PlayerEntity owner) || owner.getWorld() != world) return;
        if (owner.squaredDistanceTo(mistigri) > LUCK_RANGE * LUCK_RANGE) return;
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 60, 0, true, false, true), mistigri);
    }

    /** The players crossing his path (every other tick). */
    public static void tickCrossings(ServerWorld world, MistigriEntity mistigri) {
        long now = world.getTime();
        Map<UUID, Watch> watches = mistigri.watches;
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !player.isAlive() || mistigri.isOwner(player)) {
                watches.remove(player.getUuid());
                continue;
            }
            if (player.squaredDistanceTo(mistigri) > (AHEAD + 2) * (AHEAD + 2)) {
                watches.remove(player.getUuid());
                continue;
            }
            Watch watch = watches.get(player.getUuid());
            if (watch == null || now - watch.since() > WATCH_TICKS) {
                Vec3d facing = mistigri.facing();
                Vec3d right = new Vec3d(-facing.z, 0, facing.x);
                Vec3d origin = mistigri.getPos();
                double[] local = local(player.getPos(), origin, facing, right);
                if (inBand(local)) watches.put(player.getUuid(), new Watch(origin, facing, right, local[1], now));
                else watches.remove(player.getUuid());
                continue;
            }
            double[] local = local(player.getPos(), watch.origin(), watch.facing(), watch.right());
            if (!inBand(local)) {
                watches.remove(player.getUuid());
                continue;
            }
            if (Math.signum(local[1]) != 0 && Math.signum(local[1]) != Math.signum(watch.side()) && watch.side() != 0) {
                watches.remove(player.getUuid());
                crossed(world, mistigri, player, now);
            }
        }
        for (Iterator<Map.Entry<UUID, Long>> it = mistigri.badLuckCooldowns.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue() <= now) it.remove();
        }
    }

    /** {@code pos} from his line: [ahead, to his right]. */
    private static double[] local(Vec3d pos, Vec3d origin, Vec3d facing, Vec3d right) {
        Vec3d d = pos.subtract(origin);
        return new double[]{d.x * facing.x + d.z * facing.z, d.x * right.x + d.z * right.z};
    }

    private static boolean inBand(double[] local) {
        return local[0] >= MIN_AHEAD && local[0] <= AHEAD && Math.abs(local[1]) <= BAND;
    }

    /** {@code player} crossed his path: Bad Luck (a minute), witch sparks, his hiss. */
    public static void crossed(ServerWorld world, MistigriEntity mistigri, PlayerEntity player, long now) {
        Long next = mistigri.badLuckCooldowns.get(player.getUuid());
        if (next != null && next > now) return;
        mistigri.badLuckCooldowns.put(player.getUuid(), now + COOLDOWN);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.UNLUCK, UNLUCK_TICKS, 0), mistigri);
        Vec3d at = player.getPos().add(0, player.getHeight() * 0.6, 0);
        world.spawnParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 12, 0.3, 0.4, 0.3, 0.05);
        world.playSound(null, at.x, at.y, at.z, ModSounds.MISTIGRI_BAD_LUCK, SoundCategory.NEUTRAL, 0.7f, 1.0f);
        mistigri.getLookControl().lookAt(player);
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            MessageUtils.sendToPlayer(serverPlayer, Text.translatable("message.steveparty.mistigri.crossed")
                    .formatted(Formatting.DARK_PURPLE), MessageUtils.MessageType.ACTION_BAR);
        }
    }
}
