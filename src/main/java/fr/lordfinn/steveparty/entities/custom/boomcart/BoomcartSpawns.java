package fr.lordfinn.steveparty.entities.custom.boomcart;

import fr.lordfinn.steveparty.entities.MobSpawns;
import fr.lordfinn.steveparty.entities.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.gen.structure.StructureKeys;

/**
 * Where Boomcarts live: in abandoned mineshafts, rarely (with their rails to roll on), and very rarely in the caves
 * around them. Always alone, underground (no sky light, below sea level), in the dark.
 */
public final class BoomcartSpawns {
    /** Weight among the monsters (zombies: 95), then one try in {@link #MINESHAFT_CHANCE} kept in a mineshaft, one in
     * {@link #CAVE_CHANCE} elsewhere. */
    private static final int WEIGHT = 10, MINESHAFT_CHANCE = 3, CAVE_CHANCE = 60;
    /** At most this much block light where it is born. */
    private static final int MAX_LIGHT = 7;
    /** At least this far below sea level. */
    private static final int DEPTH = 8;

    private BoomcartSpawns() {
    }

    public static void initialize() {
        MobSpawns.register(ModEntities.BOOMCART, SpawnLocationTypes.ON_GROUND, BoomcartSpawns::canSpawn,
                BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, WEIGHT, 1, 1);
    }

    private static boolean canSpawn(EntityType<BoomcartEntity> type, ServerWorldAccess world, SpawnReason reason,
                                    BlockPos pos, Random random) {
        if (reason != SpawnReason.NATURAL && reason != SpawnReason.CHUNK_GENERATION) return true;
        if (pos.getY() >= world.getSeaLevel() - DEPTH
                || world.getLightLevel(LightType.SKY, pos) > 0
                || world.getLightLevel(LightType.BLOCK, pos) > MAX_LIGHT) return false;
        return random.nextInt(inMineshaft(world.toServerWorld(), pos) ? MINESHAFT_CHANCE : CAVE_CHANCE) == 0;
    }

    private static boolean inMineshaft(ServerWorld world, BlockPos pos) {
        return world.getStructureAccessor().getStructureContaining(pos, structure ->
                structure.matchesKey(StructureKeys.MINESHAFT) || structure.matchesKey(StructureKeys.MINESHAFT_MESA)).hasChildren();
    }
}
