package fr.lordfinn.steveparty.world;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlock;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.magpie.WildMagpieEntity;
import fr.lordfinn.steveparty.entities.custom.magpie.WildMagpieSpawns;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Magpie Nests in the trees of the woods where the wild Pies live ({@link WildMagpieSpawns#BIOMES}): now and then
 * (the placed feature {@code steveparty:magpie_nest}, one chunk in {@value #RARITY_NOTE}) a nest on the top of a
 * tree's crown, on its leaves or a log, air above it, turned any way. Most are empty, one in four holds a few shiny
 * things (gold or iron nuggets, coins). One to three wild Pies are born with it, its nest their own (they sleep in it
 * and bring it what shines), like the bees of a bee nest. A nest is a block of its own, not a leaf: no decay; broken,
 * it drops with what it holds. No pot until a Common pot space links it.
 */
public class MagpieNestFeature extends Feature<DefaultFeatureConfig> {
    /** Documentation only: see data/steveparty/worldgen/placed_feature/magpie_nest.json (rarity_filter). */
    static final String RARITY_NOTE = "5";
    public static final RegistryKey<PlacedFeature> PLACED = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Steveparty.id("magpie_nest"));
    public static final MagpieNestFeature FEATURE = Registry.register(Registries.FEATURE, Steveparty.id("magpie_nest"),
            new MagpieNestFeature());
    /** How many columns around the chosen one it tries before giving up (the canopy has holes). */
    private static final int TRIES = 8;

    public MagpieNestFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    public static void initialize() {
        BiomeModifications.addFeature(BiomeSelectors.tag(WildMagpieSpawns.BIOMES), GenerationStep.Feature.VEGETAL_DECORATION, PLACED);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        Random random = context.getRandom();
        BlockPos origin = context.getOrigin();
        for (int i = 0; i < TRIES; i++) {
            int x = origin.getX() + (i == 0 ? 0 : random.nextInt(15) - 7), z = origin.getZ() + (i == 0 ? 0 : random.nextInt(15) - 7);
            BlockPos top = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), z);
            if (place(world, top, random)) return true;
        }
        return false;
    }

    /** Whether a nest goes at {@code at}: air there and above, leaves or a log under it. */
    public static boolean fits(StructureWorldAccess world, BlockPos at) {
        BlockState under = world.getBlockState(at.down());
        return world.isAir(at) && world.isAir(at.up()) && (under.isIn(BlockTags.LEAVES) || under.isIn(BlockTags.LOGS));
    }

    /** A nest at {@code at} if it fits (turned any way, maybe a few shiny things in it) and its Pies. */
    public static boolean place(StructureWorldAccess world, BlockPos at, Random random) {
        if (!fits(world, at)) return false;
        Direction facing = Direction.Type.HORIZONTAL.random(random);
        world.setBlockState(at, ModBlocks.MAGPIE_NEST.getDefaultState().with(MagpieNestBlock.FACING, facing), Block.NOTIFY_LISTENERS);
        if (world.getBlockEntity(at) instanceof MagpieNestBlockEntity nest && random.nextInt(4) == 0) {
            ItemStack[] finds = {new ItemStack(Items.GOLD_NUGGET), new ItemStack(Items.IRON_NUGGET), new ItemStack(ModItems.COIN)};
            for (int n = 1 + random.nextInt(3); n > 0; n--) nest.insert(finds[random.nextInt(finds.length)].copy());
        }
        int pies = 1 + random.nextInt(3);
        for (int i = 0; i < pies; i++) {
            WildMagpieEntity magpie = ModEntities.WILD_MAGPIE.create(world.toServerWorld());
            if (magpie == null) continue;
            double x = at.getX() + 0.5 + (random.nextDouble() - 0.5) * 3, z = at.getZ() + 0.5 + (random.nextDouble() - 0.5) * 3;
            magpie.refreshPositionAndAngles(x, at.getY() + 0.5 + random.nextDouble(), z, random.nextFloat() * 360, 0);
            magpie.initialize(world, world.getLocalDifficulty(at), SpawnReason.CHUNK_GENERATION, null);
            magpie.adoptNest(at);
            world.spawnEntityAndPassengers(magpie);
        }
        return true;
    }
}
