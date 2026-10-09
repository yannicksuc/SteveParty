package fr.lordfinn.steveparty.entities.custom.frousseux;

import fr.lordfinn.steveparty.entities.MobSpawns;
import fr.lordfinn.steveparty.entities.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.LightType;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Where Frousseux live: in the caves of the Overworld, alone or two together. Underground (no sky light, below sea
 * level), in the dark, in the open (it floats). Its colour is drawn when it is born ({@link FrousseuxColor#random}).
 * Its own light keeps others from being born right by it.
 * <ul>
 *     <li>Among the ambient creatures (with the bats), here and there in the caves.</li>
 *     <li>And by the players exploring caves, so that one meets one or two in a cave trip (they used to be scattered
 *     out of sight): once every {@link #NEAR_EVERY} ticks per player in a cave, one chance in {@link #NEAR_CHANCE},
 *     unless {@link #NEAR_MAX} Frousseux are already within {@link #NEAR_RANGE} blocks: one (sometimes two) is born
 *     {@link #NEAR_MIN_DISTANCE} to {@link #NEAR_MAX_DISTANCE} blocks away, in a dark open spot of the cave.</li>
 * </ul>
 * Wild ones despawn only far from every player (FrousseuxEntity#canImmediatelyDespawn): beyond 64 blocks.
 */
public final class FrousseuxSpawns {
    /** Weight among the ambient creatures (bats: 10), and one natural try in {@link #CHANCE} kept on top. */
    private static final int WEIGHT = 2, CHANCE = 4;
    /** At most this much block light where it is born. */
    private static final int MAX_LIGHT = 3;
    /** At least this far below sea level. */
    private static final int DEPTH = 8;

    /** By the players in caves: how often (ticks), one try in this many, at most this many around (blocks). */
    static final int NEAR_EVERY = 400, NEAR_CHANCE = 2, NEAR_MAX = 2;
    static final double NEAR_RANGE = 48;
    static final int NEAR_MIN_DISTANCE = 16, NEAR_MAX_DISTANCE = 32, NEAR_TRIES = 24;

    private FrousseuxSpawns() {
    }

    public static void initialize() {
        MobSpawns.register(ModEntities.FROUSSEUX, SpawnLocationTypes.UNRESTRICTED, FrousseuxSpawns::canSpawn,
                BiomeSelectors.foundInOverworld(), SpawnGroup.AMBIENT, WEIGHT, 1, 2);
        ServerTickEvents.END_WORLD_TICK.register(FrousseuxSpawns::nearPlayers);
    }

    /** Now and then, by each player exploring a cave: one more Frousseux if there are few around. */
    private static void nearPlayers(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) return;
        long time = world.getTime();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if ((time + player.getId() * 37L) % NEAR_EVERY != 0 || player.isSpectator()) continue;
            BlockPos at = player.getBlockPos();
            if (at.getY() >= world.getSeaLevel() - DEPTH || world.getLightLevel(LightType.SKY, at) > 0) continue; // not in a cave
            Random random = world.getRandom();
            if (random.nextInt(NEAR_CHANCE) != 0) continue;
            if (world.getEntitiesByClass(FrousseuxEntity.class, player.getBoundingBox().expand(NEAR_RANGE), e -> true).size() >= NEAR_MAX) continue;
            BlockPos spot = findSpot(world, at, random);
            if (spot == null) continue;
            int count = random.nextInt(3) == 0 ? 2 : 1;
            for (int i = 0; i < count; i++) {
                FrousseuxEntity frousseux = ModEntities.FROUSSEUX.create(world);
                if (frousseux == null) return;
                frousseux.refreshPositionAndAngles(spot.getX() + 0.5 + i * 0.6, spot.getY() + 0.3, spot.getZ() + 0.5,
                        random.nextFloat() * 360, 0);
                frousseux.initialize(world, world.getLocalDifficulty(spot), SpawnReason.EVENT, null);
                world.spawnEntity(frousseux);
            }
        }
    }

    /** A dark open spot of the cave {@link #NEAR_MIN_DISTANCE} to {@link #NEAR_MAX_DISTANCE} blocks from {@code at}. */
    private static @Nullable BlockPos findSpot(ServerWorld world, BlockPos at, Random random) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int tries = 0; tries < NEAR_TRIES; tries++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = NEAR_MIN_DISTANCE + random.nextInt(NEAR_MAX_DISTANCE - NEAR_MIN_DISTANCE + 1);
            pos.set(at.getX() + Math.cos(angle) * distance, at.getY() + random.nextInt(17) - 8, at.getZ() + Math.sin(angle) * distance);
            if (!world.isChunkLoaded(pos)) continue;
            // down to the floor of the cave there (at most a few blocks), then a little above it
            for (int down = 0; down < 6 && world.getBlockState(pos.down()).isAir(); down++) pos.move(0, -1, 0);
            if (world.getBlockState(pos.down()).isAir()) continue;
            pos.move(0, 1, 0);
            if (canSpawn(ModEntities.FROUSSEUX, world, SpawnReason.EVENT, pos, random)
                    && world.getBlockState(pos.up()).isAir()) return pos.toImmutable();
        }
        return null;
    }

    private static boolean canSpawn(EntityType<FrousseuxEntity> type, ServerWorldAccess world, SpawnReason reason,
                                    BlockPos pos, Random random) {
        if (reason == SpawnReason.NATURAL && random.nextInt(CHANCE) != 0) return false;
        return pos.getY() < world.getSeaLevel() - DEPTH
                && world.getLightLevel(LightType.SKY, pos) == 0
                && world.getLightLevel(LightType.BLOCK, pos) <= MAX_LIGHT
                && world.getBlockState(pos).isAir();
    }
}
