package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpiePerches;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpieVariant;
import fr.lordfinn.steveparty.entities.custom.magpie.WildMagpieEntity;
import fr.lordfinn.steveparty.entities.custom.magpie.WildMagpieSpawns;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.SpawnSettings;

/**
 * The wild Pie: born in the woods (and not the Common pot's Pie), standing exactly on the visual top of what it
 * perches on (a fence or a wall post at 1.0 not 1.5, a log, a chain, a slab, a stairs' upper step), landing there
 * by itself, its colour saved, and its flight to a perch.
 */
public class WildMagpieGameTests implements SteveGameTest {
    /** Beyond the template: a magpie looks for perches 16 blocks around. */
    @Override
    public int landAround() {
        return 16;
    }

    private static boolean spawnsIn(TestContext context, net.minecraft.registry.RegistryKey<Biome> key, net.minecraft.entity.EntityType<?> type) {
        RegistryEntry<Biome> biome = context.getWorld().getRegistryManager().get(RegistryKeys.BIOME).entryOf(key);
        return biome.value().getSpawnSettings().getSpawnEntries(SpawnGroup.CREATURE).getEntries().stream()
                .anyMatch((SpawnSettings.SpawnEntry entry) -> entry.type == type);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wildPiesSpawnInTheWoodsOnly(TestContext context) {
        context.assertTrue(SpawnRestriction.getLocation(ModEntities.WILD_MAGPIE) == SpawnLocationTypes.ON_GROUND, "a spawn rule");
        context.assertEquals(ModEntities.WILD_MAGPIE.getSpawnGroup(), SpawnGroup.CREATURE, "among the animals");
        for (var forest : java.util.List.of(BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.DARK_FOREST,
                BiomeKeys.FLOWER_FOREST, BiomeKeys.TAIGA, BiomeKeys.SNOWY_TAIGA, BiomeKeys.CHERRY_GROVE)) {
            context.assertTrue(spawnsIn(context, forest, ModEntities.WILD_MAGPIE), "born in " + forest.getValue());
        }
        context.assertFalse(spawnsIn(context, BiomeKeys.PLAINS, ModEntities.WILD_MAGPIE), "not in the plains");
        context.assertFalse(spawnsIn(context, BiomeKeys.DESERT, ModEntities.WILD_MAGPIE), "not in the desert");
        // the Common pot's Pie is never born by itself
        context.assertEquals(ModEntities.MAGPIE.getSpawnGroup(), SpawnGroup.MISC, "the pot's Pie is not an animal");
        context.assertFalse(spawnsIn(context, BiomeKeys.FOREST, ModEntities.MAGPIE), "the pot's Pie never spawns");
        context.complete();
    }

    private static void assertPerch(TestContext context, BlockPos rel, BlockState state, double top, double x, double z, String what) {
        context.setBlockState(rel, state);
        BlockPos abs = context.getAbsolutePos(rel);
        Vec3d at = MagpiePerches.freePerchOn(context.getWorld(), abs);
        context.assertTrue(at != null, what + ": a perch");
        Vec3d expected = new Vec3d(abs.getX() + x, abs.getY() + top, abs.getZ() + z);
        context.assertTrue(at.squaredDistanceTo(expected) < 1.0E-8, what + ": feet at " + expected + ", not " + at);
    }

    /** Feet on what you see: fence and wall posts at 1.0 (they collide at 1.5), a log, a chain, a slab, a step. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itPerchesOnTheVisualTop(TestContext context) {
        assertPerch(context, new BlockPos(0, 1, 0), Blocks.OAK_FENCE.getDefaultState(), 1.0, 0.5, 0.5, "fence");
        context.setBlockState(new BlockPos(2, 1, 0), Blocks.SPRUCE_FENCE.getDefaultState());
        assertPerch(context, new BlockPos(3, 1, 0), Blocks.SPRUCE_FENCE.getDefaultState().with(net.minecraft.block.FenceBlock.WEST, true),
                1.0, 0.5, 0.5, "a connected fence: on its post");
        assertPerch(context, new BlockPos(0, 1, 2), Blocks.COBBLESTONE_WALL.getDefaultState(), 1.0, 0.5, 0.5, "wall");
        assertPerch(context, new BlockPos(2, 1, 2), Blocks.OAK_LOG.getDefaultState(), 1.0, 0.5, 0.5, "log");
        assertPerch(context, new BlockPos(4, 1, 2), Blocks.STRIPPED_BIRCH_WOOD.getDefaultState(), 1.0, 0.5, 0.5, "stripped wood");
        assertPerch(context, new BlockPos(0, 1, 4), Blocks.CHAIN.getDefaultState(), 1.0, 0.5, 0.5, "chain");
        assertPerch(context, new BlockPos(2, 1, 4), Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                0.5, 0.5, 0.5, "bottom slab");
        // a stairs facing north: its upper step at the north (z 0 to 0.5), the feet on it, a little inside the edge
        assertPerch(context, new BlockPos(4, 1, 4), Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH)
                .with(StairsBlock.HALF, BlockHalf.BOTTOM), 1.0, 0.5, 0.375, "stairs");
        context.assertTrue(MagpiePerches.isFavourite(Blocks.OAK_FENCE.getDefaultState())
                && MagpiePerches.isFavourite(Blocks.ANDESITE_WALL.getDefaultState())
                && MagpiePerches.isFavourite(Blocks.STRIPPED_OAK_LOG.getDefaultState())
                && MagpiePerches.isFavourite(Blocks.CHAIN.getDefaultState()), "its favourites");
        context.assertFalse(MagpiePerches.isFavourite(Blocks.OAK_LEAVES.getDefaultState()), "leaves are not a favourite");
        // no room: a block on the fence
        context.setBlockState(new BlockPos(0, 2, 0), Blocks.STONE.getDefaultState());
        context.assertTrue(MagpiePerches.freePerchOn(context.getWorld(), context.getAbsolutePos(new BlockPos(0, 1, 0))) == null,
                "no room on a covered fence");
        context.complete();
    }

    /** Let go above a fence (as from its egg: over the fence's 1.5 collision), it settles on the post, at 1.0. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void itSettlesOnTheFencePost(TestContext context) {
        BlockPos fence = new BlockPos(2, 1, 2);
        context.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
        WildMagpieEntity magpie = context.spawnEntity(ModEntities.WILD_MAGPIE, new Vec3d(2.5, 2.5, 2.5));
        BlockPos abs = context.getAbsolutePos(fence);
        context.waitAndRun(5, () -> {
            context.assertEquals(magpie.getPerch(), abs, "perched on the fence");
            context.assertTrue(Math.abs(magpie.getY() - (abs.getY() + 1.0)) < 1.0E-6, "feet on the post's top: " + magpie.getY());
            context.assertTrue(Math.abs(magpie.getX() - (abs.getX() + 0.5)) < 1.0E-6 && Math.abs(magpie.getZ() - (abs.getZ() + 0.5)) < 1.0E-6,
                    "centred on the post");
            context.assertTrue(Math.abs(magpie.getBoundingBox().minY - (abs.getY() + 1.0)) < 1.0E-6, "its hitbox too");
            context.complete();
        });
    }

    /** Its colour is kept with the world. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itsColourIsSaved(TestContext context) {
        for (MagpieVariant variant : MagpieVariant.values()) {
            WildMagpieEntity magpie = context.spawnEntity(ModEntities.WILD_MAGPIE, new Vec3d(1.5, 1, 1.5));
            magpie.setVariant(variant);
            NbtCompound nbt = new NbtCompound();
            magpie.writeNbt(nbt);
            WildMagpieEntity loaded = ModEntities.WILD_MAGPIE.create(context.getWorld());
            loaded.readNbt(nbt);
            context.assertEquals(loaded.getVariant(), variant, "variant " + variant.id);
            magpie.discard();
        }
        context.assertTrue(WildMagpieSpawns.randomVariant(context.getWorld().random, false) != MagpieVariant.CLASSIC, "an odd colour");
        context.complete();
    }

    /** Off to another perch (the other fence 10 blocks away, or the floor around), it lands exactly on its top. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void itFliesToAnotherPerch(TestContext context) {
        BlockPos from = new BlockPos(0, 1, 0), to = new BlockPos(7, 1, 7);
        context.setBlockState(from, Blocks.OAK_FENCE.getDefaultState());
        context.setBlockState(to, Blocks.OAK_FENCE.getDefaultState());
        WildMagpieEntity magpie = context.spawnEntity(ModEntities.WILD_MAGPIE, new Vec3d(0.5, 2.0, 0.5));
        BlockPos target = context.getAbsolutePos(to);
        context.waitAndRun(3, () -> {
            context.assertEquals(magpie.getPerch(), context.getAbsolutePos(from), "first on its fence");
            boolean off = false;
            for (int i = 0; i < 30 && !off; i++) off = magpie.takeOff(context.getWorld(), null) && magpie.isInFlight();
            context.assertTrue(off, "it finds a perch to fly to");
            waitLanding(context, magpie, 0);
        });
    }

    private static void waitLanding(TestContext context, WildMagpieEntity magpie, int waited) {
        if (!magpie.isInFlight()) {
            BlockPos perch = magpie.getPerch();
            context.assertTrue(perch != null, "landed on a perch");
            Vec3d top = MagpiePerches.perchOn(context.getWorld(), perch);
            context.assertTrue(top != null && top.squaredDistanceTo(magpie.getPos()) < 1.0E-8, "exactly on its top");
            context.complete();
            return;
        }
        context.assertTrue(waited < 150, "lands in time");
        context.waitAndRun(1, () -> waitLanding(context, magpie, waited + 1));
    }
}
