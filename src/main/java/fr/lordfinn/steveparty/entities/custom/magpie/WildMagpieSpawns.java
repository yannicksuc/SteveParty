package fr.lordfinn.steveparty.entities.custom.magpie;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.MobSpawns;
import fr.lordfinn.steveparty.entities.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.biome.Biome;

/**
 * Where wild Pies live (biome tags in data/steveparty/tags/worldgen/biome): in the woods, {@link #BIOMES} (every
 * forest, birch, dark and flower forests and the grove included, every taiga, the cherry grove and the windswept
 * forest), among the animals ({@link SpawnGroup#CREATURE}), weight {@link #WEIGHT}, by 1 to 3, born on the ground in
 * daylight then off to the trees. A wild one far from every player (beyond 64 blocks) may go away, as a monster does,
 * unless it has a name (WildMagpieEntity#canImmediatelyDespawn).
 * <p>
 * Its colour comes from where it is born ({@link #variantFor}): mostly azure in the birch woods and the cherry grove,
 * golden in the flower forest, rusty in the taigas, pale in the snowy ones, dark in the dark forest; classic
 * elsewhere, with a rare odd one anywhere.
 */
public final class WildMagpieSpawns {
    public static final TagKey<Biome> BIOMES = tag("magpie");
    public static final TagKey<Biome> AZURE_BIOMES = tag("magpie_azure");
    public static final TagKey<Biome> GOLDEN_BIOMES = tag("magpie_golden");
    public static final TagKey<Biome> RUSTY_BIOMES = tag("magpie_rusty");
    public static final TagKey<Biome> PALE_BIOMES = tag("magpie_pale");
    public static final TagKey<Biome> DARK_BIOMES = tag("magpie_dark");
    public static final int WEIGHT = 6;
    /** In its biome, this share of the Pies wear its colour. */
    private static final float BIOME_SHARE = 0.45f;
    /** Anywhere, this share are an odd colour (any but the classic). */
    private static final float ODD_SHARE = 0.04f;

    private WildMagpieSpawns() {
    }

    private static TagKey<Biome> tag(String name) {
        return TagKey.of(RegistryKeys.BIOME, Steveparty.id(name));
    }

    public static void initialize() {
        MobSpawns.register(ModEntities.WILD_MAGPIE, SpawnLocationTypes.ON_GROUND, WildMagpieSpawns::canSpawn,
                BiomeSelectors.tag(BIOMES), SpawnGroup.CREATURE, WEIGHT, 1, 3);
    }

    /** On soil, snow or leaves, in daylight. */
    private static boolean canSpawn(EntityType<WildMagpieEntity> type, ServerWorldAccess world,
                                    SpawnReason reason, BlockPos pos, Random random) {
        BlockState ground = world.getBlockState(pos.down());
        boolean soil = ground.isIn(BlockTags.DIRT) || ground.isIn(BlockTags.SNOW) || ground.isIn(BlockTags.LEAVES)
                || ground.isIn(BlockTags.ANIMALS_SPAWNABLE_ON);
        return soil && world.getBaseLightLevel(pos, 0) > 8;
    }

    /** The colour of a Pie born at {@code pos}, from its biome. */
    public static MagpieVariant variantFor(ServerWorldAccess world, BlockPos pos, Random random) {
        RegistryEntry<Biome> biome = world.getBiome(pos);
        MagpieVariant local = biome.isIn(DARK_BIOMES) ? MagpieVariant.DARK
                : biome.isIn(PALE_BIOMES) ? MagpieVariant.PALE
                : biome.isIn(AZURE_BIOMES) ? MagpieVariant.AZURE
                : biome.isIn(GOLDEN_BIOMES) ? MagpieVariant.GOLDEN
                : biome.isIn(RUSTY_BIOMES) ? MagpieVariant.RUSTY
                : null;
        if (local != null && random.nextFloat() < BIOME_SHARE) return local;
        if (random.nextFloat() < ODD_SHARE) return randomVariant(random, false);
        return MagpieVariant.CLASSIC;
    }

    /** Any colour ({@code classic}: the classic one among them). */
    public static MagpieVariant randomVariant(Random random, boolean classic) {
        MagpieVariant[] all = MagpieVariant.values();
        return classic ? all[random.nextInt(all.length)] : all[1 + random.nextInt(all.length - 1)];
    }
}
