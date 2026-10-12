package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlock;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpieEntity;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpieVariant;
import fr.lordfinn.steveparty.entities.custom.magpie.WildMagpieEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The wild Pie's life: its feathers when it dies (more than a chicken's, more with Looting) and what it holds, love
 * with any seed and a chick of one of its parents' colours (never the Common pot's Pie: it is no animal), the chick in
 * its nest (and hopping out and back), on foot without one, growing up; and the long flights: far, high, within the
 * land that ticks, and home again. Each kind in a batch of its own (a nest or a chick of another test nearby would
 * change what they do).
 */
public class WildMagpieLifeGameTests implements SteveGameTest {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("WildMagpieLifeGameTests");

    /** The long flights go 40 blocks away and back. */
    @Override
    public int landAround() {
        return 48;
    }

    private static void when(TestContext context, BooleanSupplier condition, int ticks, Supplier<String> what, Runnable then) {
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
            throw new GameTestException("timed out: " + what.get());
        }
        context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
    }

    private static String state(WildMagpieEntity magpie) {
        return " (perch " + magpie.getPerch() + ", flying " + magpie.isInFlight() + ", at " + magpie.getPos()
                + ", nest " + magpie.getNest() + ", baby " + magpie.isBaby() + ")";
    }

    private static ServerPlayerEntity survivalPlayer(TestContext context, Vec3d at) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.getAbilities().creativeMode = false;
        player.getInventory().clear();
        Vec3d abs = context.getAbsolute(at);
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, 0, 0);
        TestCleanup.atEnd(context, () -> context.getWorld().getServer().getPlayerManager().remove(player));
        return player;
    }

    private static WildMagpieEntity perchedPie(TestContext context, BlockPos fence) {
        context.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
        WildMagpieEntity magpie = context.spawnEntity(ModEntities.WILD_MAGPIE, Vec3d.ofBottomCenter(fence).add(0, 1.2, 0));
        magpie.setPersistent();
        magpie.setNightOverride(false);
        return magpie;
    }

    private static BlockPos placeNest(TestContext context, BlockPos at) {
        context.setBlockState(at.down(), Blocks.STONE);
        context.setBlockState(at, ModBlocks.MAGPIE_NEST.getDefaultState().with(MagpieNestBlock.FACING, Direction.SOUTH));
        return context.getAbsolutePos(at);
    }

    // ---------------------------------------------------------------- loot

    private static List<Integer> feathers(TestContext context, int looting) {
        ServerWorld world = context.getWorld();
        WildMagpieEntity magpie = context.spawnEntity(ModEntities.WILD_MAGPIE, new Vec3d(1.5, 1, 1.5));
        ServerPlayerEntity player = survivalPlayer(context, new Vec3d(3.5, 1, 1.5));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        if (looting > 0) {
            RegistryEntry<Enchantment> entry = world.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT)
                    .getOrThrow(Enchantments.LOOTING);
            sword.addEnchantment(entry, looting);
        }
        player.setStackInHand(Hand.MAIN_HAND, sword);
        LootTable table = world.getServer().getReloadableRegistries().getLootTable(ModEntities.WILD_MAGPIE.getLootTableId());
        LootContextParameterSet loot = new LootContextParameterSet.Builder(world)
                .add(LootContextParameters.THIS_ENTITY, magpie)
                .add(LootContextParameters.ORIGIN, magpie.getPos())
                .add(LootContextParameters.DAMAGE_SOURCE, world.getDamageSources().playerAttack(player))
                .add(LootContextParameters.ATTACKING_ENTITY, player)
                .add(LootContextParameters.DIRECT_ATTACKING_ENTITY, player)
                .add(LootContextParameters.LAST_DAMAGE_PLAYER, player)
                .build(LootContextTypes.ENTITY);
        List<Integer> counts = new ArrayList<>();
        for (long seed = 0; seed < 300; seed++) {
            int count = 0;
            for (ItemStack stack : table.generateLoot(loot, seed)) {
                context.assertTrue(stack.isOf(Items.FEATHER), "only feathers: " + stack);
                count += stack.getCount();
            }
            counts.add(count);
        }
        magpie.discard();
        return counts;
    }

    /** 1 to 3 feathers (a chicken: 0 to 2); Looting III: up to 3 more. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wild_magpie_loot")
    public void itDropsMoreFeathersThanAChickenAndMoreWithLooting(TestContext context) {
        List<Integer> plain = feathers(context, 0);
        context.assertTrue(plain.stream().allMatch(n -> n >= 1 && n <= 3), "1 to 3 feathers: " + plain);
        context.assertTrue(plain.contains(1) && plain.contains(3), "1 and 3 both seen");
        List<Integer> looting = feathers(context, 3);
        context.assertTrue(looting.stream().allMatch(n -> n >= 1 && n <= 6), "1 to 6 feathers with Looting III");
        context.assertTrue(looting.stream().anyMatch(n -> n > 3), "more than 3 now and then with Looting III");
        double plainMean = plain.stream().mapToInt(Integer::intValue).average().orElse(0);
        double lootingMean = looting.stream().mapToInt(Integer::intValue).average().orElse(0);
        context.assertTrue(lootingMean > plainMean + 1, "more on average: " + plainMean + " -> " + lootingMean);
        context.complete();
    }

    /** Killed, it drops its feathers and what it held in its beak. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wild_magpie_loot")
    public void killedItDropsWhatItHolds(TestContext context) {
        context.setBlockState(new BlockPos(2, 0, 2), Blocks.STONE);
        WildMagpieEntity magpie = context.spawnEntity(ModEntities.WILD_MAGPIE, new Vec3d(2.5, 1, 2.5));
        magpie.setCarried(new ItemStack(Items.GOLD_NUGGET));
        ServerPlayerEntity player = survivalPlayer(context, new Vec3d(4.5, 1, 2.5));
        magpie.damage(context.getWorld().getDamageSources().playerAttack(player), 100);
        context.assertTrue(magpie.isDead(), "dead");
        context.waitAndRun(2, () -> {
            List<ItemEntity> drops = context.getWorld().getEntitiesByClass(ItemEntity.class,
                    new Box(context.getAbsolutePos(new BlockPos(2, 1, 2))).expand(2), item -> true);
            int feathers = drops.stream().filter(i -> i.getStack().isOf(Items.FEATHER)).mapToInt(i -> i.getStack().getCount()).sum();
            int nuggets = drops.stream().filter(i -> i.getStack().isOf(Items.GOLD_NUGGET)).mapToInt(i -> i.getStack().getCount()).sum();
            context.assertTrue(feathers >= 1 && feathers <= 3, "1 to 3 feathers: " + feathers);
            context.assertEquals(nuggets, 1, "the nugget it held");
            drops.forEach(ItemEntity::discard);
            context.complete();
        });
    }

    // ---------------------------------------------------------------- love

    /**
     * Fed seeds (any kind: wheat and pumpkin here), two Pies fall in love (a player holding seeds does not scare them)
     * and a chick is born: of one of their colours, their nest its own. A chick fed seeds grows faster.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "wild_magpie_love")
    public void seedsMakeThemBreedAChick(TestContext context) {
        BlockPos nest = placeNest(context, new BlockPos(6, 1, 6));
        WildMagpieEntity first = perchedPie(context, new BlockPos(2, 1, 2));
        WildMagpieEntity second = perchedPie(context, new BlockPos(4, 1, 2));
        first.setVariant(MagpieVariant.AZURE);
        second.setVariant(MagpieVariant.RUSTY);
        first.adoptNest(nest);
        ServerPlayerEntity player = survivalPlayer(context, new Vec3d(3.5, 1, 4.5));
        context.assertTrue(first.isBreedingItem(new ItemStack(Items.MELON_SEEDS)) && first.isBreedingItem(new ItemStack(Items.TORCHFLOWER_SEEDS))
                && first.isBreedingItem(new ItemStack(Items.BEETROOT_SEEDS)) && first.isBreedingItem(new ItemStack(Items.PITCHER_POD)), "any seed");
        context.assertFalse(first.isBreedingItem(new ItemStack(Items.WHEAT)), "not wheat itself");
        when(context, () -> first.getPerch() != null && second.getPerch() != null, 20, () -> "perched" + state(first) + state(second), () -> {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 5));
            player.interact(first, Hand.MAIN_HAND);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.PUMPKIN_SEEDS, 5));
            player.interact(second, Hand.MAIN_HAND);
            context.assertTrue(first.isInLove() && second.isInLove(), "both in love");
            context.assertEquals(player.getMainHandStack().getCount(), 4, "one seed eaten");
            Box around = new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(12);
            when(context, () -> !context.getWorld().getEntitiesByType(ModEntities.WILD_MAGPIE, around, WildMagpieEntity::isBaby).isEmpty(),
                    300, () -> "a chick" + state(first) + state(second), () -> {
                        WildMagpieEntity chick = context.getWorld().getEntitiesByType(ModEntities.WILD_MAGPIE, around, WildMagpieEntity::isBaby).getFirst();
                        context.assertTrue(first.getPerch() != null && second.getPerch() != null, "they never flew off: " + state(first) + state(second));
                        context.assertTrue(chick.getVariant() == MagpieVariant.AZURE || chick.getVariant() == MagpieVariant.RUSTY,
                                "a parent's colour: " + chick.getVariant());
                        context.assertEquals(chick.getNest(), nest, "their nest its own");
                        context.assertTrue(first.getBreedingAge() > 0 && !first.isInLove(), "the parents rest");
                        int age = chick.getBreedingAge();
                        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.MELON_SEEDS, 5));
                        player.interact(chick, Hand.MAIN_HAND);
                        context.assertTrue(chick.getBreedingAge() > age, "seeds make it grow: " + age + " -> " + chick.getBreedingAge());
                        context.complete();
                    });
        });
    }

    /** In love, two Pies apart still meet and breed while the player keeps the seeds in hand (no longer tempted). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 500, batchId = "wild_magpie_love")
    public void inLoveTheyMeetEvenWithSeedsInHand(TestContext context) {
        WildMagpieEntity first = perchedPie(context, new BlockPos(1, 1, 1));
        WildMagpieEntity second = perchedPie(context, new BlockPos(7, 1, 1));
        ServerPlayerEntity player = survivalPlayer(context, new Vec3d(4.5, 1, 6.5));
        when(context, () -> first.getPerch() != null && second.getPerch() != null, 20, () -> "perched" + state(first) + state(second), () -> {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 5));
            player.interact(first, Hand.MAIN_HAND);
            player.interact(second, Hand.MAIN_HAND);
            context.assertTrue(first.isInLove() && second.isInLove(), "both in love");
            Box around = new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(12);
            when(context, () -> !context.getWorld().getEntitiesByType(ModEntities.WILD_MAGPIE, around, WildMagpieEntity::isBaby).isEmpty(),
                    440, () -> "a chick, seeds still in hand" + state(first) + state(second), context::complete);
        });
    }

    /** The Common pot's Pie is no animal: seeds do nothing to it, it never breeds. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wild_magpie_love")
    public void thePotsPieNeverBreeds(TestContext context) {
        MagpieEntity pie = ModEntities.MAGPIE.create(context.getWorld());
        Vec3d at = context.getAbsolute(new Vec3d(2.5, 1, 2.5));
        pie.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        context.getWorld().spawnEntity(pie);
        ServerPlayerEntity player = survivalPlayer(context, new Vec3d(4.5, 1, 2.5));
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 5));
        player.interact(pie, Hand.MAIN_HAND);
        context.assertFalse(((Object) pie) instanceof AnimalEntity, "not an animal");
        context.assertEquals(player.getMainHandStack().getCount(), 5, "it eats no seed");
        pie.discard();
        context.complete();
    }

    // ---------------------------------------------------------------- chicks

    /** A chick with a nest goes into it, sits there, hops out onto a block close by and comes back. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "wild_magpie_chick_nest")
    public void aChickStaysInItsNest(TestContext context) {
        BlockPos nest = placeNest(context, new BlockPos(3, 1, 3));
        for (BlockPos post : List.of(new BlockPos(1, 1, 3), new BlockPos(5, 1, 3), new BlockPos(3, 1, 1), new BlockPos(3, 1, 5))) {
            context.setBlockState(post, Blocks.OAK_FENCE.getDefaultState());
        }
        WildMagpieEntity chick = context.spawnEntity(ModEntities.WILD_MAGPIE, new Vec3d(3.5, 1.6, 3.5));
        chick.setBaby(true);
        chick.setPersistent();
        chick.setNightOverride(false);
        chick.adoptNest(nest);
        int[] farthest = {0};
        context.runAtEveryTick(() -> {
            if (chick.isAlive()) farthest[0] = Math.max(farthest[0], around(chick.getBlockPos(), nest));
        });
        when(context, () -> nest.equals(chick.getPerch()), 200, () -> "into its nest" + state(chick), () -> {
            context.assertFalse(chick.isWalking(), "not on foot");
            boolean out = false;
            for (int i = 0; i < 20 && !out; i++) out = chick.hopOut(context.getWorld());
            context.assertTrue(out, "it hops out");
            when(context, () -> chick.getPerch() != null && !nest.equals(chick.getPerch()), 60, () -> "out" + state(chick), () -> {
                context.assertTrue(around(chick.getPerch(), nest) <= WildMagpieEntity.CHICK_OUTING_RADIUS, "close by: " + chick.getPerch());
                context.assertTrue(chick.hopHome(context.getWorld()), "it hops home");
                when(context, () -> nest.equals(chick.getPerch()), 60, () -> "home again" + state(chick), () -> {
                    context.assertTrue(farthest[0] <= WildMagpieEntity.CHICK_OUTING_RADIUS, "never far from its nest: " + farthest[0]);
                    chick.discard();
                    context.complete();
                });
            });
        });
    }

    /** How far {@code pos} is from {@code nest}, horizontally (blocks along x or z, the larger). */
    private static int around(BlockPos pos, BlockPos nest) {
        return Math.max(Math.abs(pos.getX() - nest.getX()), Math.abs(pos.getZ() - nest.getZ()));
    }

    /** Without a nest, a chick walks on the ground (gravity, collisions) and stays there. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "wild_magpie_chick_ground")
    public void aChickWithoutANestWalks(TestContext context) {
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
        WildMagpieEntity chick = context.spawnEntity(ModEntities.WILD_MAGPIE, new Vec3d(3.5, 3, 3.5));
        chick.setBaby(true);
        chick.setPersistent();
        chick.setNightOverride(false);
        double ground = context.getAbsolutePos(new BlockPos(0, 1, 0)).getY();
        when(context, () -> chick.isWalking() && chick.isOnGround(), 60, () -> "on the ground" + state(chick), () -> {
            context.assertFalse(chick.hasNoGravity(), "gravity");
            context.assertTrue(Math.abs(chick.getY() - ground) < 1.0E-3, "standing on the grass: " + chick.getY());
            context.assertTrue(chick.getHeight() < 0.4F, "a small hitbox: " + chick.getHeight());
            context.waitAndRun(100, () -> {
                context.assertTrue(chick.getY() < ground + 1.5, "still on the ground (a hop at most): " + chick.getY());
                context.assertTrue(chick.getNest() == null && chick.isWalking(), "still on foot");
                chick.discard();
                context.complete();
            });
        });
    }

    /** A chick grows up in time into an adult: no longer on foot, flying again. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "wild_magpie_chick_ground")
    public void aChickGrowsUp(TestContext context) {
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
        WildMagpieEntity chick = context.spawnEntity(ModEntities.WILD_MAGPIE, new Vec3d(3.5, 1, 3.5));
        chick.setBaby(true);
        chick.setPersistent();
        chick.setNightOverride(false);
        context.assertEquals(chick.getBreedingAge(), -24000, "the usual baby time");
        when(context, () -> chick.isWalking(), 20, () -> "on foot" + state(chick), () -> {
            chick.setBreedingAge(-20);
            when(context, () -> !chick.isBaby(), 60, () -> "grown up" + state(chick), () -> {
                context.waitAndRun(2, () -> {
                    context.assertFalse(chick.isWalking(), "no longer on foot");
                    context.assertTrue(chick.hasNoGravity(), "a flying bird again");
                    context.assertTrue(chick.getHeight() > 0.5F, "an adult's size");
                    chick.discard();
                    context.complete();
                });
            });
        });
    }

    // ---------------------------------------------------------------- tempted by seeds

    private static double horizontal(WildMagpieEntity magpie, ServerPlayerEntity player) {
        return Math.sqrt(magpie.getPos().subtract(player.getPos()).horizontalLengthSquared());
    }

    /** Facing {@code player} (within 25 degrees). */
    private static boolean faces(WildMagpieEntity magpie, ServerPlayerEntity player) {
        Vec3d to = player.getPos().subtract(magpie.getPos());
        float yaw = (float) (Math.atan2(to.z, to.x) * (180 / Math.PI)) - 90;
        return Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(magpie.getHeadYaw() - yaw)) < 25;
    }

    private static void floor(TestContext context) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
    }

    /**
     * A player holding seeds 8 blocks away: the Pie looks at him and hops to him (not away), lands about 2 blocks from
     * him, follows him when he goes; the seeds put away, it is no longer tempted.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "wild_magpie_tempt_adult")
    public void seedsTemptAPie(TestContext context) {
        floor(context);
        WildMagpieEntity magpie = perchedPie(context, new BlockPos(1, 1, 1));
        ServerPlayerEntity player = survivalPlayer(context, new Vec3d(6.5, 1, 6.5));
        when(context, () -> magpie.getPerch() != null, 20, () -> "perched" + state(magpie), () -> {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BEETROOT_SEEDS, 3));
            when(context, () -> !magpie.isInFlight() && magpie.getPerch() != null && horizontal(magpie, player) <= WildMagpieEntity.TEMPT_FOLLOW,
                    150, () -> "close to him" + state(magpie), () -> {
                        context.assertEquals(magpie.getTempter(), player, "tempted by him");
                        context.assertTrue(horizontal(magpie, player) >= 0.9, "beside him, not on him: " + horizontal(magpie, player));
                        context.waitAndRun(WildMagpieEntity.TEMPT_CHECK_INTERVAL + 1, () -> {
                            context.assertTrue(faces(magpie, player), "looking at him: " + magpie.getHeadYaw());
                            Vec3d moved = context.getAbsolute(new Vec3d(1.5, 1, 6.5));
                            player.refreshPositionAndAngles(moved.x, moved.y, moved.z, 0, 0);
                            when(context, () -> !magpie.isInFlight() && magpie.getPerch() != null && horizontal(magpie, player) <= WildMagpieEntity.TEMPT_FOLLOW,
                                    150, () -> "followed him" + state(magpie), () -> {
                                        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                                        when(context, () -> magpie.getTempter() == null, WildMagpieEntity.TEMPT_CHECK_INTERVAL + 2,
                                                () -> "no longer tempted" + state(magpie), () -> {
                                                    magpie.discard();
                                                    context.complete();
                                                });
                                    });
                        });
                    });
        });
    }

    /** A chick on foot waddles to a player holding seeds and looks at him; the seeds put away, it is no longer tempted. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "wild_magpie_tempt_chick")
    public void seedsTemptAChick(TestContext context) {
        floor(context);
        WildMagpieEntity chick = context.spawnEntity(ModEntities.WILD_MAGPIE, new Vec3d(1.5, 1, 1.5));
        chick.setBaby(true);
        chick.setPersistent();
        chick.setNightOverride(false);
        ServerPlayerEntity player = survivalPlayer(context, new Vec3d(6.5, 1, 6.5));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.MELON_SEEDS, 3));
        when(context, () -> chick.isWalking() && horizontal(chick, player) <= 2.3, 200, () -> "waddled to him" + state(chick), () -> {
            context.assertEquals(chick.getTempter(), player, "tempted by him");
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            when(context, () -> chick.getTempter() == null, WildMagpieEntity.TEMPT_CHECK_INTERVAL + 2, () -> "no longer tempted" + state(chick), () -> {
                chick.discard();
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- long flights

    /** The reach of a long flight stops short of the land that does not tick, with a margin; all of it where it all ticks. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wild_magpie_long")
    public void aLongFlightStopsAtTheLoadedLandsEdge(TestContext context) {
        Vec3d from = new Vec3d(0.5, 64, 0.5);
        context.assertEquals(WildMagpieEntity.loadedReach(from, 0, 60, pos -> true), 60.0, "all loaded: all the way");
        double reach = WildMagpieEntity.loadedReach(from, 0, 60, pos -> pos.getX() < 30);
        context.assertTrue(reach > 0 && reach <= 30 - 8, "stops 8 blocks short of x 30: " + reach);
        double west = WildMagpieEntity.loadedReach(from, Math.PI, 60, pos -> pos.getX() > -20);
        context.assertTrue(west <= 12, "westwards too: " + west);
        context.assertTrue(WildMagpieEntity.loadedReach(from, 0, 60, pos -> pos.getX() < 10) < WildMagpieEntity.LONG_FLIGHT_SHORTEST,
                "too short to be worth it");
        context.complete();
    }

    /**
     * On the test's roof: from a fence, a long flight 40 blocks east climbs high, stays over land that ticks every tick,
     * lands far beyond an ordinary hop; with its nest back at the start, its next flight takes it home.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700, batchId = "wild_magpie_long")
    public void aLongFlightGoesFarAndComesHome(TestContext context) {
        ServerWorld world = context.getWorld();
        int roof = 10;
        List<BlockPos> placed = new ArrayList<>();
        Runnable clean = () -> {
            for (BlockPos p : placed) world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        };
        TestCleanup.atEnd(context, clean);
        BlockPos base = context.getAbsolutePos(new BlockPos(0, roof, 0));
        BlockState fence = Blocks.OAK_FENCE.getDefaultState();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) place(world, base.add(x, 0, z), fence, placed);
        for (int x = 34; x <= 46; x++) for (int z = -6; z <= 6; z++) place(world, base.add(x, 0, z), fence, placed);
        BlockPos nest = base.add(0, 0, 4);
        place(world, nest.down(), Blocks.STONE.getDefaultState(), placed);
        place(world, nest, ModBlocks.MAGPIE_NEST.getDefaultState(), placed);
        WildMagpieEntity magpie = ModEntities.WILD_MAGPIE.create(world);
        magpie.refreshPositionAndAngles(base.getX() + 0.5, base.getY() + 1.2, base.getZ() + 0.5, 0, 0);
        magpie.setPersistent();
        magpie.setNightOverride(false);
        magpie.adoptNest(nest);
        world.spawnEntity(magpie);
        TestCleanup.atEnd(context, magpie::discard);
        Vec3d start = Vec3d.ofBottomCenter(base);
        double[] highest = {0};
        boolean[] outside = {false};
        context.runAtEveryTick(() -> {
            if (!magpie.isAlive()) return;
            highest[0] = Math.max(highest[0], magpie.getY() - start.y);
            if (!world.shouldTickEntity(magpie.getBlockPos())) outside[0] = true;
        });
        when(context, () -> magpie.getPerch() != null, 20, () -> "perched" + state(magpie), () -> {
            boolean off = false;
            for (int i = 0; i < 10 && !off; i++) off = magpie.longFlight(world, 0, 40);
            context.assertTrue(off && magpie.isInLongFlight(), "off on a long flight" + state(magpie));
            when(context, () -> !magpie.isInFlight() && magpie.getPerch() != null, 300, () -> "landed far" + state(magpie), () -> {
                double far = Math.sqrt(magpie.getPos().subtract(start).horizontalLengthSquared());
                context.assertTrue(far > WildMagpieEntity.SEARCH_RADIUS * 2, "much farther than a hop: " + far);
                context.assertTrue(highest[0] > 8, "it climbed high: " + highest[0]);
                context.assertFalse(outside[0], "always over land that ticks");
                context.assertEquals(magpie.getNest(), nest, "its nest kept");
                boolean back = false;
                for (int i = 0; i < 10 && !back; i++) back = magpie.takeOff(world, null);
                context.assertTrue(back && magpie.isInLongFlight(), "a long flight home" + state(magpie));
                Vec3d end = magpie.getFlightEnd();
                context.assertTrue(end != null && Math.sqrt(end.subtract(Vec3d.ofBottomCenter(nest)).horizontalLengthSquared()) < 14,
                        "ending near its nest: " + end);
                when(context, () -> !magpie.isInFlight(), 300, () -> "home" + state(magpie), () -> {
                    context.assertFalse(outside[0], "always over land that ticks");
                    context.complete();
                });
            });
        });
    }

    private static void place(ServerWorld world, BlockPos pos, BlockState state, List<BlockPos> placed) {
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
        placed.add(pos);
    }
}
