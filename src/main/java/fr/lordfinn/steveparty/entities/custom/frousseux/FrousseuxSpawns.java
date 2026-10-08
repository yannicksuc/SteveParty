package fr.lordfinn.steveparty.entities.custom.frousseux;

import fr.lordfinn.steveparty.entities.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.ServerWorldAccess;

/**
 * Where Frousseux live: in the caves of the Overworld, alone or two together, rarely. Underground (no sky light, below
 * sea level), in the dark, in the open (it floats). Its colour is drawn when it is born ({@link FrousseuxColor#random}).
 * Its own light keeps others from being born right by it.
 */
public final class FrousseuxSpawns {
    /** Weight among the ambient creatures (bats: 10), and one natural try in {@link #CHANCE} kept on top. */
    private static final int WEIGHT = 2, CHANCE = 4;
    /** At most this much block light where it is born. */
    private static final int MAX_LIGHT = 3;
    /** At least this far below sea level. */
    private static final int DEPTH = 8;

    private FrousseuxSpawns() {
    }

    public static void initialize() {
        SpawnRestriction.register(ModEntities.FROUSSEUX, SpawnLocationTypes.UNRESTRICTED,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, FrousseuxSpawns::canSpawn);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.AMBIENT, ModEntities.FROUSSEUX,
                WEIGHT, 1, 2);
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
