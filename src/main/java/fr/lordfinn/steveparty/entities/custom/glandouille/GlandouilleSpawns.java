package fr.lordfinn.steveparty.entities.custom.glandouille;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.block.BlockState;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.Biome;

/**
 * Where Glandouilles live, in groups of 2 to 4, under the trees (biome tags in data/steveparty/tags/worldgen/biome):
 * the classic and young ones (sometimes an old one) where oaks grow ({@link #OAK_BIOMES}), the old mossy ones in the
 * old growth taigas ({@link #OLD_GROWTH_BIOMES}), the frosty ones in the snowy taigas ({@link #SNOWY_BIOMES}).
 */
public final class GlandouilleSpawns {
    public static final TagKey<Biome> OAK_BIOMES = TagKey.of(RegistryKeys.BIOME, Steveparty.id("glandouille_oak"));
    public static final TagKey<Biome> OLD_GROWTH_BIOMES = TagKey.of(RegistryKeys.BIOME, Steveparty.id("glandouille_old_growth"));
    public static final TagKey<Biome> SNOWY_BIOMES = TagKey.of(RegistryKeys.BIOME, Steveparty.id("glandouille_snowy"));
    /** Under the trees: leaves at most this many blocks above where it spawns. */
    private static final int CANOPY_SEARCH = 10;

    private GlandouilleSpawns() {
    }

    public static void initialize() {
        SpawnRestriction.register(ModEntities.GLANDOUILLE, SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, GlandouilleSpawns::canSpawn);
        BiomeModifications.addSpawn(BiomeSelectors.tag(OAK_BIOMES), SpawnGroup.CREATURE, ModEntities.GLANDOUILLE, 6, 2, 4);
        BiomeModifications.addSpawn(BiomeSelectors.tag(OLD_GROWTH_BIOMES), SpawnGroup.CREATURE, ModEntities.GLANDOUILLE, 5, 2, 3);
        BiomeModifications.addSpawn(BiomeSelectors.tag(SNOWY_BIOMES), SpawnGroup.CREATURE, ModEntities.GLANDOUILLE, 5, 2, 4);
    }

    /** On soil or snow, in daylight-ish, under leaves (a tree's canopy). */
    private static boolean canSpawn(net.minecraft.entity.EntityType<GlandouilleEntity> type, ServerWorldAccess world,
                                    SpawnReason reason, BlockPos pos, Random random) {
        BlockState ground = world.getBlockState(pos.down());
        boolean soil = ground.isIn(BlockTags.DIRT) || ground.isIn(BlockTags.SNOW) || ground.isIn(BlockTags.ANIMALS_SPAWNABLE_ON);
        return soil && world.getBaseLightLevel(pos, 0) > 6 && underCanopy(world, pos);
    }

    private static boolean underCanopy(WorldAccess world, BlockPos pos) {
        BlockPos.Mutable at = pos.mutableCopy();
        for (int i = 1; i <= CANOPY_SEARCH; i++) {
            at.move(0, 1, 0);
            if (world.getBlockState(at).isIn(BlockTags.LEAVES)) return true;
        }
        return false;
    }

    /** The kind of Glandouille born at {@code pos}, from its biome. */
    public static GlandouilleVariant variantFor(ServerWorldAccess world, BlockPos pos, Random random) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        if (biome.isIn(SNOWY_BIOMES)) return GlandouilleVariant.FROSTY;
        if (biome.isIn(OLD_GROWTH_BIOMES)) return GlandouilleVariant.MOSSY;
        float roll = random.nextFloat();
        if (roll < 0.07f) return GlandouilleVariant.MOSSY;
        if (roll < 0.35f) return GlandouilleVariant.YOUNG;
        return GlandouilleVariant.CLASSIC;
    }
}
