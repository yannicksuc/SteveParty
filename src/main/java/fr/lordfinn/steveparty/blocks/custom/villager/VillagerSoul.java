package fr.lordfinn.steveparty.blocks.custom.villager;

import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The villager squashed inside a villager block: its entity data, kept by the block entity from the piston that
 * made it (profession, level, trades, experience, name, gossip, inventory, health...) and given back when a sticky
 * piston pulls it out.
 * <p>
 * Only what belongs to the villager itself is kept: its position, motion, UUID and brain memories (bed and
 * workstation reservations, which point to places that may be gone) are dropped. The UUID is not kept so that a
 * copied villager block item (creative) can't make two entities with the same UUID; a released villager finds a bed
 * and a workstation again, and keeps its profession (a villager that traded keeps it without a workstation).
 * <p>
 * Pistons move blocks without their block entity data: the data of the villager blocks a piston moves is handed
 * over through {@link #carry}/{@link #take} to the block entity that appears where they arrive.
 */
public final class VillagerSoul {
    /** The key of the villager's data in the block entity's (and the dropped item's) data. */
    public static final String KEY = "Villager";
    private static final String[] VOLATILE = {"UUID", "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air",
            "OnGround", "PortalCooldown", "Brain", "leash", "Leash", "Passengers", "HurtTime", "HurtByTimestamp",
            "DeathTime", "FallFlying", "SleepingX", "SleepingY", "SleepingZ", "TicksFrozen", "HasVisualFire"};
    /** Ticks a carried block entity data waits for its block to arrive (a piston moves in 2 ticks). */
    private static final int CARRY_TICKS = 20;
    private static final Map<Carried, NbtCompound> CARRIED = new HashMap<>();
    private static final Map<Carried, Long> CARRIED_AT = new HashMap<>();

    private record Carried(RegistryKey<World> world, long pos) {
    }

    private VillagerSoul() {
    }

    /** The data to keep of {@code villager}. */
    public static NbtCompound capture(VillagerEntity villager) {
        NbtCompound nbt = villager.writeNbt(new NbtCompound());
        for (String key : VOLATILE) nbt.remove(key);
        return nbt;
    }

    /**
     * Brings the villager back at {@code pos} (feet on the block's bottom), from its kept data or, for a villager
     * block that never had one (creative, older worlds), as a new villager of the place's biome.
     */
    public static VillagerEntity release(ServerWorld world, BlockPos pos, @Nullable NbtCompound soul) {
        VillagerEntity villager = EntityType.VILLAGER.create(world, SpawnReason.CONVERSION);
        if (villager == null) return null;
        if (soul != null) {
            villager.readNbt(soul);
        } else {
            villager.initialize(world, world.getLocalDifficulty(pos), SpawnReason.CONVERSION, null);
        }
        villager.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0);
        villager.setVelocity(0, 0.25, 0);
        world.spawnEntity(villager);
        world.playSound(null, pos, ModSounds.VILLAGER_BLOCK_FREED, SoundCategory.BLOCKS, 1f, 1f);
        world.playSound(null, pos, SoundEvents.ENTITY_SLIME_JUMP, SoundCategory.BLOCKS, 0.7f, 1.3f);
        world.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0);
        return villager;
    }

    /** A piston moves a villager block (with this block entity data) to {@code to}. */
    public static void carry(World world, BlockPos to, NbtCompound data) {
        long now = world.getTime();
        if (CARRIED.size() > 64) {
            CARRIED_AT.entrySet().removeIf(e -> {
                boolean old = now - e.getValue() > CARRY_TICKS || now < e.getValue();
                if (old) CARRIED.remove(e.getKey());
                return old;
            });
        }
        Carried key = new Carried(world.getRegistryKey(), to.asLong());
        CARRIED.put(key, data);
        CARRIED_AT.put(key, now);
    }

    /** The block entity data a piston brought to {@code pos} just now, or null. */
    @Nullable
    public static NbtCompound take(World world, BlockPos pos) {
        if (CARRIED.isEmpty()) return null;
        Carried key = new Carried(world.getRegistryKey(), pos.asLong());
        NbtCompound data = CARRIED.remove(key);
        Long at = CARRIED_AT.remove(key);
        return data != null && at != null && world.getTime() - at <= CARRY_TICKS ? data : null;
    }
}
