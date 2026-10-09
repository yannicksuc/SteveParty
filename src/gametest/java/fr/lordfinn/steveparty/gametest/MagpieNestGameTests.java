package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlock;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestPile;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.magpie.WildMagpieEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.BiomeKeys;
import fr.lordfinn.steveparty.world.MagpieNestFeature;

import java.util.function.BooleanSupplier;

/**
 * The Magpie Nest: turned to whoever places it and saved so, filled by hand (and by hoppers) with coins and shiny
 * things, each one more coin on its pile, its content saved; the wild Pies sleeping in it at night (in the middle, or
 * on the free corner of the rim beside a pile, one per nest) and leaving at dawn, bringing it the shiny things lying
 * around and nothing else. The tests with wild Pies have batches of their own (any loaded nest is theirs, any shiny
 * thing lying around).
 */
public class MagpieNestGameTests implements FabricGameTest {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("MagpieNestGameTests");

    private static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        when(context, condition, ticks, () -> what, then);
    }

    private static void when(TestContext context, BooleanSupplier condition, int ticks, java.util.function.Supplier<String> what, Runnable then) {
        if (condition.getAsBoolean()) {
            try {
                then.run();
            } catch (RuntimeException failure) {
                LOGGER.error("{}: {}", what.get(), failure.getMessage());
                throw failure;
            }
            return;
        }
        if (ticks <= 0) {
            LOGGER.error("timed out: {}", what.get());
            throw new net.minecraft.test.GameTestException("timed out: " + what.get());
        }
        context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
    }

    private static String state(WildMagpieEntity magpie) {
        return " (perch " + magpie.getPerch() + ", flying " + magpie.isInFlight() + ", state " + magpie.getFlightState()
                + ", at " + magpie.getPos() + ", nest " + magpie.getNest() + ")";
    }

    private static MagpieNestBlockEntity placeNest(TestContext context, BlockPos at, Direction facing) {
        context.setBlockState(at.down(), Blocks.STONE);
        context.setBlockState(at, ModBlocks.MAGPIE_NEST.getDefaultState().with(MagpieNestBlock.FACING, facing));
        return context.getBlockEntity(at);
    }

    /** A mock player in survival, gone at the end of the test. */
    private static ServerPlayerEntity survivalPlayer(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.getAbilities().creativeMode = false;
        player.getInventory().clear();
        ThresholdGameTests.atEnd(context, () -> context.getWorld().getServer().getPlayerManager().remove(player));
        return player;
    }

    private static BlockHitResult hit(TestContext context, BlockPos at) {
        BlockPos abs = context.getAbsolutePos(at);
        return new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false);
    }

    /** Placed facing whoever places it (a player looking east: the nest looks west), its facing and content saved. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itFacesWhoPlacesItAndIsSaved(TestContext context) {
        PlayerEntity player = survivalPlayer(context);
        BlockPos at = new BlockPos(1, 1, 1);
        for (Direction looking : Direction.Type.HORIZONTAL) {
            player.setYaw(looking.asRotation());
            ItemPlacementContext placing = new ItemPlacementContext(player, Hand.MAIN_HAND, new ItemStack(ModItems.MAGPIE_NEST), hit(context, at));
            BlockState placed = ModBlocks.MAGPIE_NEST.getPlacementState(placing);
            context.assertEquals(placed.get(MagpieNestBlock.FACING), looking.getOpposite(), "looking " + looking);
        }
        MagpieNestBlockEntity nest = placeNest(context, at, Direction.EAST);
        nest.insert(new ItemStack(ModItems.COIN, 12));
        nest.insert(new ItemStack(Items.GOLD_NUGGET, 3));
        NbtCompound nbt = nest.createNbtWithIdentifyingData(context.getWorld().getRegistryManager());
        BlockEntity loaded = BlockEntity.createFromNbt(nest.getPos(), nest.getCachedState(), nbt, context.getWorld().getRegistryManager());
        context.assertTrue(loaded instanceof MagpieNestBlockEntity, "a nest again");
        MagpieNestBlockEntity again = (MagpieNestBlockEntity) loaded;
        context.assertEquals(again.getCoins(), 12, "its coins saved");
        context.assertEquals(again.getTreasures().getFirst().getCount(), 3, "its shiny things saved");
        context.assertEquals(again.getPileCount(), 15, "a pile of 15");
        context.assertEquals(again.getFacing(), Direction.EAST, "its facing");
        context.complete();
    }

    /**
     * By hand: a stack of coins (sneaking: one), a shiny thing; not dirt. An empty hand takes back the shiny thing,
     * then the coins. A hopper fills it too.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void coinsAndShinyThingsGoInByHand(TestContext context) {
        BlockPos at = new BlockPos(1, 1, 1);
        MagpieNestBlockEntity nest = placeNest(context, at, Direction.NORTH);
        BlockState state = context.getBlockState(at);
        PlayerEntity player = survivalPlayer(context);
        ItemStack coins = new ItemStack(ModItems.COIN, 10);
        state.onUseWithItem(coins, context.getWorld(), player, Hand.MAIN_HAND, hit(context, at));
        context.assertEquals(nest.getPileCount(), 10, "ten coins in");
        context.assertTrue(coins.isEmpty(), "out of the hand");
        player.setSneaking(true);
        ItemStack more = new ItemStack(ModItems.COIN, 5);
        state.onUseWithItem(more, context.getWorld(), player, Hand.MAIN_HAND, hit(context, at));
        context.assertEquals(more.getCount(), 4, "sneaking: one");
        player.setSneaking(false);
        ItemStack nugget = new ItemStack(Items.GOLD_NUGGET);
        state.onUseWithItem(nugget, context.getWorld(), player, Hand.MAIN_HAND, hit(context, at));
        ItemStack dirt = new ItemStack(Items.DIRT);
        state.onUseWithItem(dirt, context.getWorld(), player, Hand.MAIN_HAND, hit(context, at));
        context.assertEquals(dirt.getCount(), 1, "no dirt in a nest");
        context.assertEquals(nest.getPileCount(), 12, "11 coins and a nugget: 12 on the pile");
        context.assertEquals(nest.comparatorOutput(), 1 + 12 * 14 / 64, "the comparator");
        player.getInventory().clear();
        state.onUse(context.getWorld(), player, hit(context, at));
        context.assertTrue(player.getInventory().count(Items.GOLD_NUGGET) == 1, "the nugget back first");
        state.onUse(context.getWorld(), player, hit(context, at));
        context.assertEquals(player.getInventory().count(ModItems.COIN), 11, "then the coins");
        context.assertEquals(nest.getPileCount(), 0, "empty");
        // a hopper over it
        BlockPos hopper = at.up();
        context.setBlockState(hopper, Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) context.getBlockEntity(hopper)).setStack(0, new ItemStack(ModItems.COIN, 2));
        when(context, () -> nest.getPileCount() == 2, 50, "the hopper fills it", context::complete);
    }

    /** The pile: the coins placed by hand first, then a pile stable for a nest, never above the cap drawn. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void thePileStartsWithTheHandPlacedCoins(TestContext context) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(1, 1, 1));
        float[] pile = MagpieNestPile.layout(pos, 30);
        context.assertEquals(pile.length, 30 * MagpieNestPile.STRIDE, "30 coins");
        context.assertTrue(pile[0] == 8 && pile[1] == 2 && pile[2] == 8 && pile[3] == 0, "the first hand-placed coin");
        context.assertTrue(pile[4 * MagpieNestPile.STRIDE + 1] == 10 && pile[4 * MagpieNestPile.STRIDE + 3] == -72, "the fifth");
        float[] again = MagpieNestPile.layout(pos, 30);
        context.assertTrue(java.util.Arrays.equals(pile, again), "the same pile every time");
        context.assertEquals(MagpieNestPile.layout(pos, 500).length, MagpieNestPile.MAX_DRAWN * MagpieNestPile.STRIDE, "at most 64 drawn");
        context.complete();
    }

    // ---------------------------------------------------------------- wild Pies

    private static WildMagpieEntity perchedPie(TestContext context, BlockPos fence) {
        context.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
        WildMagpieEntity magpie = context.spawnEntity(ModEntities.WILD_MAGPIE, Vec3d.ofBottomCenter(fence).add(0, 1.2, 0));
        magpie.setPersistent();
        return magpie;
    }

    /** At night it sleeps in the nest, in its middle, facing its way; at dawn it leaves. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "magpie_nest_wild")
    public void aWildPieSleepsInTheNestAtNight(TestContext context) {
        BlockPos nestPos = new BlockPos(1, 1, 1);
        MagpieNestBlockEntity nest = placeNest(context, nestPos, Direction.SOUTH);
        WildMagpieEntity magpie = perchedPie(context, new BlockPos(6, 1, 6));
        magpie.setNightOverride(false);
        BlockPos abs = context.getAbsolutePos(nestPos);
        when(context, () -> magpie.getPerch() != null, 20, "perched", () -> {
            magpie.setNightOverride(true);
            when(context, () -> abs.equals(magpie.getPerch()), 200, () -> "to bed in the nest" + state(magpie), () -> {
                context.assertTrue(magpie.getPos().squaredDistanceTo(nest.perch()) < 1.0E-6, "in the middle of the nest");
                context.assertTrue(Math.abs(magpie.getPos().x - (abs.getX() + 0.5)) < 1.0E-6, "centred");
                context.assertEquals(magpie.getFlightState(), WildMagpieEntity.ASLEEP, "asleep");
                context.assertTrue(Math.abs(magpie.getYaw() - Direction.SOUTH.asRotation()) < 1.0E-3, "facing the nest's way");
                context.assertEquals(magpie.getNest(), abs, "its nest now");
                magpie.setNightOverride(false);
                when(context, () -> magpie.getFlightState() != WildMagpieEntity.ASLEEP, 20, () -> "awake at dawn" + state(magpie), () -> {
                    boolean off = false;
                    for (int i = 0; i < 30 && !off; i++) off = magpie.takeOff(context.getWorld(), null) && magpie.isInFlight();
                    context.assertTrue(off, "and off");
                    context.complete();
                });
            });
        });
    }

    /** Beside a pile of coins, it sleeps on the free corner of the rim; one Pie a nest. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "magpie_nest_wild")
    public void besideAPileItSleepsOnTheRimAlone(TestContext context) {
        BlockPos nestPos = new BlockPos(1, 1, 1);
        MagpieNestBlockEntity nest = placeNest(context, nestPos, Direction.EAST);
        nest.insert(new ItemStack(ModItems.COIN, 6));
        WildMagpieEntity first = perchedPie(context, new BlockPos(6, 1, 6));
        WildMagpieEntity second = perchedPie(context, new BlockPos(6, 1, 1));
        first.setNightOverride(false);
        second.setNightOverride(false);
        BlockPos abs = context.getAbsolutePos(nestPos);
        when(context, () -> first.getPerch() != null && second.getPerch() != null, 20, "perched", () -> {
            context.assertTrue(first.goToBed(context.getWorld()), "the first one goes to bed");
            context.assertTrue(!second.goToBed(context.getWorld()), "no room for the second one");
            first.setNightOverride(true);
            when(context, () -> abs.equals(first.getPerch()), 200, () -> "in the nest" + state(first), () -> {
                Vec3d corner = MagpieNestPile.perch(abs, Direction.EAST, 6);
                context.assertTrue(first.getPos().squaredDistanceTo(corner) < 1.0E-6, "on the free corner: " + first.getPos());
                context.assertTrue(Math.abs(corner.x - (abs.getX() + 0.5)) > 0.2, "not in the middle");
                context.assertTrue(Math.abs(first.getYaw() - MagpieNestPile.perchYaw(Direction.EAST, 6)) < 1.0E-3, "looking out along the diagonal");
                context.complete();
            });
        });
    }

    /**
     * By day it picks up a shiny thing lying on the ground and brings it to its nest; dirt it leaves, and never takes
     * from a player's inventory.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 500, batchId = "magpie_nest_shiny")
    public void aWildPieBringsShinyThingsToItsNest(TestContext context) {
        BlockPos nestPos = new BlockPos(1, 1, 1);
        MagpieNestBlockEntity nest = placeNest(context, nestPos, Direction.NORTH);
        WildMagpieEntity magpie = perchedPie(context, new BlockPos(6, 1, 6));
        magpie.setNightOverride(false);
        ItemEntity nugget = new ItemEntity(context.getWorld(), 0, 0, 0, new ItemStack(Items.GOLD_NUGGET, 2));
        Vec3d nuggetAt = context.getAbsolute(new Vec3d(4.5, 1.1, 2.5));
        nugget.refreshPositionAndAngles(nuggetAt.x, nuggetAt.y, nuggetAt.z, 0, 0);
        nugget.setVelocity(Vec3d.ZERO);
        nugget.setPickupDelay(0);
        nugget.setNeverDespawn();
        context.getWorld().spawnEntity(nugget);
        ItemEntity dirt = new ItemEntity(context.getWorld(), 0, 0, 0, new ItemStack(Items.DIRT));
        Vec3d dirtAt = context.getAbsolute(new Vec3d(2.5, 1.1, 5.5));
        dirt.refreshPositionAndAngles(dirtAt.x, dirtAt.y, dirtAt.z, 0, 0);
        dirt.setVelocity(Vec3d.ZERO);
        dirt.setPickupDelay(0);
        dirt.setNeverDespawn();
        context.getWorld().spawnEntity(dirt);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        Vec3d playerAt = context.getAbsolute(new Vec3d(7.5, 1, 1.5));
        player.refreshPositionAndAngles(playerAt.x, playerAt.y, playerAt.z, 0, 0);
        player.getInventory().clear();
        player.getInventory().insertStack(new ItemStack(Items.DIAMOND, 3));
        ThresholdGameTests.atEnd(context, () -> context.getWorld().getServer().getPlayerManager().remove(player));
        when(context, () -> nest.getTreasures().stream().anyMatch(stack -> stack.isOf(Items.GOLD_NUGGET)), 450, () -> "the nugget in the nest" + state(magpie), () -> {
            context.assertEquals(nest.getPileCount(), 1, "one nugget in the beak, one in the nest");
            context.assertEquals(nugget.isAlive() ? nugget.getStack().getCount() : 0, 1, "the other one still lying there");
            context.assertTrue(dirt.isAlive() && dirt.getStack().isOf(Items.DIRT), "the dirt left alone");
            context.assertEquals(player.getInventory().count(Items.DIAMOND), 3, "the player's diamonds untouched");
            context.assertTrue(magpie.getCarried().isEmpty(), "its beak empty again");
            context.assertEquals(magpie.getNest(), context.getAbsolutePos(nestPos), "its nest");
            nugget.discard();
            dirt.discard();
            context.complete();
        });
    }

    // ---------------------------------------------------------------- nests in the trees

    /** The nests in the trees are a feature of the woods of the wild Pies, not of the plains or the desert. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nestsGrowInTheWoodsOnly(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        net.minecraft.world.gen.feature.PlacedFeature nests = registries.get(net.minecraft.registry.RegistryKeys.PLACED_FEATURE)
                .get(MagpieNestFeature.PLACED);
        context.assertTrue(nests != null, "the placed feature");
        var biomes = registries.get(net.minecraft.registry.RegistryKeys.BIOME);
        for (var forest : java.util.List.of(BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.DARK_FOREST, BiomeKeys.TAIGA,
                BiomeKeys.FLOWER_FOREST, BiomeKeys.CHERRY_GROVE)) {
            context.assertTrue(biomes.get(forest).getGenerationSettings().isFeatureAllowed(nests), "nests in " + forest.getValue());
        }
        for (var bare : java.util.List.of(BiomeKeys.PLAINS, BiomeKeys.DESERT, BiomeKeys.OCEAN)) {
            context.assertFalse(biomes.get(bare).getGenerationSettings().isFeatureAllowed(nests), "none in " + bare.getValue());
        }
        context.complete();
    }

    /** A nest goes on a tree's crown (leaves or a log under it, air above), with its Pies, their nest their own. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "magpie_nest_gen")
    public void aNestInATreeComesWithItsPies(TestContext context) {
        for (int y = 1; y <= 3; y++) context.setBlockState(new BlockPos(3, y, 3), Blocks.OAK_LOG);
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                context.setBlockState(new BlockPos(x, 4, z), Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
            }
        }
        context.setBlockState(new BlockPos(6, 1, 6), Blocks.STONE);
        net.minecraft.util.math.random.Random random = net.minecraft.util.math.random.Random.create(42);
        context.assertFalse(MagpieNestFeature.place(context.getWorld(), context.getAbsolutePos(new BlockPos(6, 2, 6)), random), "not on stone");
        BlockPos at = context.getAbsolutePos(new BlockPos(3, 5, 3));
        context.assertTrue(MagpieNestFeature.place(context.getWorld(), at, random), "on the crown");
        context.assertTrue(context.getWorld().getBlockState(at).isOf(ModBlocks.MAGPIE_NEST), "a nest");
        context.assertTrue(context.getWorld().isAir(at.up()), "air above it");
        context.assertTrue(context.getWorld().getBlockState(at.down()).isIn(BlockTags.LEAVES), "on the leaves");
        java.util.List<WildMagpieEntity> pies = context.getWorld().getEntitiesByType(ModEntities.WILD_MAGPIE,
                new net.minecraft.util.math.Box(at).expand(4), pie -> at.equals(pie.getNest()));
        context.assertTrue(!pies.isEmpty() && pies.size() <= 3, "1 to 3 Pies, its own: " + pies.size());
        context.assertTrue(!pies.getFirst().canImmediatelyDespawn(1.0E6), "a Pie with a nest stays");
        pies.forEach(WildMagpieEntity::discard);
        context.getWorld().removeBlock(at, false);
        context.complete();
    }
}
