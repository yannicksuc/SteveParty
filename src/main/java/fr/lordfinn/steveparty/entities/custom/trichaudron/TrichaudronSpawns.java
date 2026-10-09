package fr.lordfinn.steveparty.entities.custom.trichaudron;

import fr.lordfinn.steveparty.entities.MobSpawns;
import fr.lordfinn.steveparty.entities.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.structure.Structure;

/**
 * Where Trichaudrons live: in the Nether wastes, the basalt deltas and the soul sand valleys, by the lava: on solid ground
 * with a lava source within {@link #LAVA_RANGE} blocks around and below, or in the lava lakes and rivers themselves
 * (it swims). Rarely and always alone: weight {@link #WEIGHT} among the monsters, then one natural try in
 * {@link #CHANCE} kept. Its tank is born at least half full (TrichaudronEntity#initialize). Within
 * {@link #CAMP_RANGE} blocks of a bastion ({@code #steveparty:trichaudron_rider_camps}), {@link #MOUNTED_PERCENT} % are
 * born with piglins on ({@link #piglinRiders}): one (50 %), two (35 %) or three (15 %).
 */
public final class TrichaudronSpawns {
    private static final int WEIGHT = 4, CHANCE = 3;
    private static final int LAVA_RANGE = 6, LAVA_DOWN = 4;
    public static final TagKey<Structure> RIDER_CAMPS = TagKey.of(RegistryKeys.STRUCTURE, Steveparty.id("trichaudron_rider_camps"));
    public static final int CAMP_RANGE = 48, MOUNTED_PERCENT = 40;

    private TrichaudronSpawns() {
    }

    public static void initialize() {
        MobSpawns.register(ModEntities.TRICHAUDRON, SpawnLocationTypes.UNRESTRICTED, TrichaudronSpawns::canSpawn,
                BiomeSelectors.includeByKey(BiomeKeys.NETHER_WASTES, BiomeKeys.BASALT_DELTAS, BiomeKeys.SOUL_SAND_VALLEY),
                SpawnGroup.MONSTER, WEIGHT, 1, 1);
    }

    static boolean canSpawn(EntityType<TrichaudronEntity> type, ServerWorldAccess world, SpawnReason reason,
                            BlockPos pos, Random random) {
        if (reason != SpawnReason.NATURAL && reason != SpawnReason.CHUNK_GENERATION) return true;
        if (random.nextInt(CHANCE) != 0) return false;
        if (inLava(world.getFluidState(pos))) return true; // in the lake itself
        if (!world.getBlockState(pos.down()).isSolidBlock(world, pos.down()) || !world.getFluidState(pos).isEmpty()) return false;
        return nearLava(world, pos, random);
    }

    /** How many piglins a naturally born one carries: none away from a bastion, else see the class comment. */
    public static int piglinRiders(ServerWorldAccess world, BlockPos pos, Random random) {
        if (random.nextInt(100) >= MOUNTED_PERCENT) return 0;
        BlockPos camp = world.toServerWorld().locateStructure(RIDER_CAMPS, pos, CAMP_RANGE / 16 + 1, false);
        if (camp == null || camp.getSquaredDistance(pos.getX(), camp.getY(), pos.getZ()) > CAMP_RANGE * CAMP_RANGE) return 0;
        return riderCount(random.nextFloat());
    }

    /** One (roll below 0.5), two (below 0.85) or three piglins. */
    public static int riderCount(float roll) {
        return roll < 0.5f ? 1 : roll < 0.85f ? 2 : 3;
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
