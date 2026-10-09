package fr.lordfinn.steveparty.entities.custom.fumarole;

import fr.lordfinn.steveparty.entities.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.biome.BiomeKeys;

/**
 * Where Fumaroles live: in the Nether wastes, the basalt deltas and the soul sand valleys, by the lava: on solid ground
 * with a lava source within {@link #LAVA_RANGE} blocks around and below, or in the lava lakes and rivers themselves
 * (it swims). Rarely and always alone: weight {@link #WEIGHT} among the monsters, then one natural try in
 * {@link #CHANCE} kept. Its tank is born at least half full (FumaroleEntity#initialize).
 */
public final class FumaroleSpawns {
    private static final int WEIGHT = 4, CHANCE = 3;
    private static final int LAVA_RANGE = 6, LAVA_DOWN = 4;

    private FumaroleSpawns() {
    }

    public static void initialize() {
        SpawnRestriction.register(ModEntities.FUMAROLE, SpawnLocationTypes.UNRESTRICTED,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, FumaroleSpawns::canSpawn);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.NETHER_WASTES, BiomeKeys.BASALT_DELTAS,
                BiomeKeys.SOUL_SAND_VALLEY), SpawnGroup.MONSTER, ModEntities.FUMAROLE, WEIGHT, 1, 1);
    }

    static boolean canSpawn(EntityType<FumaroleEntity> type, ServerWorldAccess world, SpawnReason reason,
                            BlockPos pos, Random random) {
        if (reason != SpawnReason.NATURAL && reason != SpawnReason.CHUNK_GENERATION) return true;
        if (random.nextInt(CHANCE) != 0) return false;
        if (inLava(world.getFluidState(pos))) return true; // in the lake itself
        if (!world.getBlockState(pos.down()).isSolidBlock(world, pos.down()) || !world.getFluidState(pos).isEmpty()) return false;
        return nearLava(world, pos, random);
    }

    private static boolean inLava(FluidState fluid) {
        return fluid.isOf(Fluids.LAVA) && fluid.isStill();
    }

    /** A few random looks around for a lava source (cheap: spawn checks run often). */
    private static boolean nearLava(ServerWorldAccess world, BlockPos pos, Random random) {
        BlockPos.Mutable at = new BlockPos.Mutable();
        for (int i = 0; i < 24; i++) {
            at.set(pos.getX() + random.nextBetween(-LAVA_RANGE, LAVA_RANGE), pos.getY() - random.nextInt(LAVA_DOWN + 1),
                    pos.getZ() + random.nextBetween(-LAVA_RANGE, LAVA_RANGE));
            if (inLava(world.getFluidState(at))) return true;
        }
        return false;
    }
}
