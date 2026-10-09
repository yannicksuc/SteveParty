package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleEntity;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumarolePumping;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.util.List;

/**
 * The Fumarole: born nearly empty with its stats; buckets take lava from its tank and pour some in; pumping drinks a
 * source and respects the pool and rate rules; the blast hurts, burns, shoves and costs a bucket (the empty tank's
 * puff is weaker and free); its death spills lava only with mobGriefing; its tank is saved.
 */
public class FumaroleGameTests implements FabricGameTest {
    /** mobGriefing is changed for the whole server: these tests run alone in their batch. */
    private static final String GRIEFING_BATCH = "fumarole_griefing";

    private static FumaroleEntity spawn(TestContext context, BlockPos at) {
        FumaroleEntity fumarole = context.spawnEntity(ModEntities.FUMAROLE, at);
        fumarole.setAiDisabled(true);
        return fumarole;
    }

    private static void floor(TestContext context, int size) {
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
    }

    /** A pool of lava sources, {@code w} x {@code d}, its corner at {@code corner} (relative). */
    private static void pool(TestContext context, BlockPos corner, int w, int d) {
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) context.setBlockState(corner.add(x, 0, z), Blocks.LAVA);
    }

    private static ServerPlayerEntity player(TestContext context, ItemStack held) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, held);
        return player;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spawnsNearlyEmptyWithItsStats(TestContext context) {
        floor(context, 8);
        ServerWorld world = context.getWorld();
        FumaroleEntity fumarole = ModEntities.FUMAROLE.create(world);
        context.assertTrue(fumarole != null, "created");
        BlockPos at = context.getAbsolutePos(new BlockPos(4, 1, 4));
        fumarole.refreshPositionAndAngles(at, 0, 0);
        fumarole.initialize(world, world.getLocalDifficulty(at), SpawnReason.SPAWN_EGG, null);
        world.spawnEntity(fumarole);
        context.assertTrue(fumarole.isAlive(), "alive");
        context.assertTrue(fumarole.getTank() >= 0 && fumarole.getTank() <= FumaroleEntity.SPAWN_TANK_MAX, "nearly empty: " + fumarole.getTank());
        context.assertEquals(fumarole.getMaxHealth(), (float) FumaroleEntity.MAX_HEALTH, "80 HP");
        context.assertEquals(fumarole.getAttributeValue(EntityAttributes.GENERIC_ARMOR), FumaroleEntity.ARMOR, "armour 10");
        context.assertEquals(fumarole.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE), 1.0, "no knockback");
        context.assertTrue(fumarole.isFireImmune(), "fire immune");
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bucketsTakeAndPourLava(TestContext context) {
        floor(context, 8);
        FumaroleEntity fumarole = spawn(context, new BlockPos(4, 1, 4));
        fumarole.setTank(2);
        ServerPlayerEntity player = player(context, new ItemStack(Items.BUCKET));
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.LAVA_BUCKET), "an empty bucket comes back full");
        context.assertEquals(fumarole.getTank(), 1, "one bucket taken");
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.BUCKET), "a lava bucket is poured in");
        context.assertEquals(fumarole.getTank(), 2, "one bucket poured");
        fumarole.setTank(0);
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.BUCKET), "nothing to take from an empty tank");
        fumarole.setTank(FumaroleEntity.TANK_MAX);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.LAVA_BUCKET), "a full tank takes no more");
        context.assertEquals(fumarole.getTank(), FumaroleEntity.TANK_MAX, "still full");
        context.getWorld().getServer().getPlayerManager().remove(player);
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_pump")
    public void pumpingDrinksASourceWithinTheRules(TestContext context) {
        floor(context, 16);
        FumaroleEntity fumarole = spawn(context, new BlockPos(2, 1, 7));
        fumarole.setTank(0);
        // a 5x5 pool: 25 sources; its middle has 4 source neighbours and 24 others around
        pool(context, new BlockPos(7, 0, 5), 5, 5);
        BlockPos middle = context.getAbsolutePos(new BlockPos(9, 0, 7));
        context.assertTrue(fumarole.canReach(middle), "within the nozzle's reach");
        context.assertTrue(fumarole.pump(middle), "drinks the middle source");
        context.assertTrue(context.getWorld().getBlockState(middle).isAir(), "the source is gone");
        context.assertEquals(fumarole.getTank(), 1, "one bucket in the tank");
        // the rate: another gulp too soon is refused
        BlockPos next = context.getAbsolutePos(new BlockPos(8, 0, 7));
        context.assertFalse(fumarole.pump(next), "not twice within 3 s");
        // a corner source has only 2 source neighbours but too few around once the pool is small
        FumarolePumping rules = new FumarolePumping();
        context.assertTrue(rules.rateAllows(0), "a fresh rate allows");
        for (int i = 0; i < FumarolePumping.MAX_PER_MINUTE; i++) rules.record(i * FumarolePumping.MIN_GAP);
        long last = (FumarolePumping.MAX_PER_MINUTE - 1) * (long) FumarolePumping.MIN_GAP;
        context.assertFalse(rules.rateAllows(last + FumarolePumping.MIN_GAP), "at most 6 per minute");
        context.assertTrue(rules.rateAllows(1200), "a minute later it may again");
        // a small pool (3x3: 9 sources) is never touched
        pool(context, new BlockPos(1, 0, 12), 3, 3);
        BlockPos small = context.getAbsolutePos(new BlockPos(2, 0, 13));
        context.assertFalse(FumarolePumping.leavesEnough(context.getWorld(), small), "a 9-source pool is left alone");
        // the big pool's lone edge source (one neighbour) is left alone
        context.setBlockState(new BlockPos(12, 0, 7), Blocks.LAVA);
        context.assertFalse(FumarolePumping.leavesEnough(context.getWorld(), context.getAbsolutePos(new BlockPos(12, 0, 7))),
                "a source with one source neighbour is left alone");
        // flowing lava is no source
        context.setBlockState(new BlockPos(14, 1, 14), Blocks.LAVA.getDefaultState().with(net.minecraft.block.FluidBlock.LEVEL, 3));
        context.assertFalse(FumarolePumping.isSource(context.getWorld(), context.getAbsolutePos(new BlockPos(14, 1, 14))), "flowing lava");
        // a full tank drinks no more
        fumarole.setTank(FumaroleEntity.TANK_MAX);
        context.assertFalse(fumarole.canPump(next), "full");
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 2; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (context.getBlockState(pos).getFluidState().isOf(net.minecraft.fluid.Fluids.LAVA)) context.setBlockState(pos, Blocks.STONE);
        }
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_pump_floor")
    public void pumpingStopsAtTheAreaFloor(TestContext context) {
        floor(context, 16);
        FumaroleEntity fumarole = spawn(context, new BlockPos(1, 1, 7));
        // a 4x4 pool: 16 sources; pumping may bring it down to 13 at best (12 others must remain around)
        pool(context, new BlockPos(6, 0, 5), 4, 4);
        int taken = 0;
        for (int round = 0; round < 16; round++) {
            for (int x = 6; x < 10; x++) {
                for (int z = 5; z < 9; z++) {
                    BlockPos pos = context.getAbsolutePos(new BlockPos(x, 0, z));
                    if (FumarolePumping.isSource(context.getWorld(), pos) && FumarolePumping.leavesEnough(context.getWorld(), pos)) {
                        context.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState());
                        taken++;
                    }
                }
            }
        }
        int left = 0;
        for (int x = 6; x < 10; x++) for (int z = 5; z < 9; z++) {
            if (FumarolePumping.isSource(context.getWorld(), context.getAbsolutePos(new BlockPos(x, 0, z)))) left++;
        }
        context.assertTrue(left >= FumarolePumping.MIN_REMAINING, "at least " + FumarolePumping.MIN_REMAINING + " left: " + left);
        context.assertTrue(taken > 0 && taken + left == 16, "some taken: " + taken);
        for (int x = 6; x < 10; x++) for (int z = 5; z < 9; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_blast", tickLimit = 40)
    public void theBlastHurtsBurnsShovesAndCostsABucket(TestContext context) {
        // a clear strip 22 blocks long (beyond the 8-block template, whatever stands there)
        for (int x = -1; x < 22; x++) for (int z = 3; z < 14; z++) {
            context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y < 8; y++) context.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
        }
        FumaroleEntity fumarole = spawn(context, new BlockPos(1, 1, 8));
        fumarole.setYaw(-90);
        fumarole.setBodyYaw(-90);
        fumarole.setHeadYaw(-90);
        fumarole.setTank(5);
        Vec3d nozzle = fumarole.nozzle();
        PigEntity zombie = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        zombie.setAiDisabled(true);
        zombie.refreshPositionAndAngles(nozzle.x + 6, context.getAbsolutePos(new BlockPos(0, 1, 0)).getY(), nozzle.z, 0, 0);
        float before = zombie.getHealth();
        List<LivingEntity> hurt = fumarole.blast(zombie);
        context.assertTrue(hurt.contains(zombie), "the pig is hit: origin " + fumarole.blastOrigin() + " nozzle " + nozzle + " pig " + zombie.getPos() + " tank " + fumarole.getTank() + " hurt " + hurt);
        context.assertTrue(before - zombie.getHealth() >= FumaroleEntity.BLAST_DAMAGE - 1.5f, "about 6 damage: " + (before - zombie.getHealth()));
        context.assertTrue(zombie.isOnFire(), "set on fire");
        Vec3d push = zombie.getVelocity();
        context.assertTrue(push.x > 0.5 && push.y > 0.1, "shoved away and up: " + push);
        context.assertEquals(fumarole.getTank(), 4, "a bucket spent");
        // empty: a weaker puff, no fire, free
        PigEntity near = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        near.setAiDisabled(true);
        near.refreshPositionAndAngles(nozzle.x + 3, context.getAbsolutePos(new BlockPos(0, 1, 0)).getY(), nozzle.z, 0, 0);
        fumarole.setTank(0);
        float nearBefore = near.getHealth();
        fumarole.blast(near);
        float puffDamage = nearBefore - near.getHealth();
        context.assertTrue(puffDamage > 0 && puffDamage <= FumaroleEntity.PUFF_DAMAGE + 0.01f, "a weak puff: " + puffDamage);
        context.assertFalse(near.isOnFire(), "the puff burns nothing");
        context.assertEquals(fumarole.getTank(), 0, "the puff is free");
        // a wall stops the steam
        PigEntity hidden = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        hidden.setAiDisabled(true);
        Vec3d behind = nozzle.add(8, 0, 0);
        hidden.refreshPositionAndAngles(behind.x, context.getAbsolutePos(new BlockPos(0, 1, 0)).getY(), behind.z, 0, 0);
        BlockPos wall = BlockPos.ofFloored(nozzle.add(4, 0, 0));
        for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) context.getWorld().setBlockState(wall.add(0, dy, dz), Blocks.STONE.getDefaultState());
        fumarole.setTank(3);
        float hiddenBefore = hidden.getHealth();
        fumarole.blast(hidden);
        context.assertEquals(hidden.getHealth(), hiddenBefore, "the wall stops the steam");
        for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) context.getWorld().setBlockState(wall.add(0, dy, dz), Blocks.AIR.getDefaultState());
        zombie.discard();
        near.discard();
        hidden.discard();
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = GRIEFING_BATCH, tickLimit = 40)
    public void deathSpillsLavaOnlyWithMobGriefing(TestContext context) {
        floor(context, 8);
        ServerWorld world = context.getWorld();
        GameRules.BooleanRule griefing = world.getGameRules().get(GameRules.DO_MOB_GRIEFING);
        boolean before = griefing.get();
        try {
            griefing.set(false, world.getServer());
            FumaroleEntity calm = spawn(context, new BlockPos(1, 1, 1));
            calm.setTank(FumaroleEntity.TANK_MAX);
            context.assertEquals(calm.spill(world), 0, "no mobGriefing: no lava placed");
            context.assertEquals(calm.getTank(), 0, "its tank is spilt all the same");
            calm.discard();
            griefing.set(true, world.getServer());
            FumaroleEntity full = spawn(context, new BlockPos(4, 1, 4));
            full.setTank(FumaroleEntity.TANK_MAX);
            BlockPos at = full.getBlockPos();
            int spilt = full.spill(world);
            StringBuilder around = new StringBuilder();
            for (BlockPos spot : new BlockPos[]{at, at.north(), at.south(), at.east(), at.west()}) around.append(world.getBlockState(spot)).append(' ');
            context.assertEquals(spilt, FumaroleEntity.SPILL_MAX, "27 buckets: 3 sources (" + around + ")");
            context.assertTrue(FumarolePumping.isSource(world, at), "a source where it died");
            for (BlockPos spot : new BlockPos[]{at, at.north(), at.south(), at.east(), at.west()}) {
                world.setBlockState(spot, Blocks.AIR.getDefaultState());
            }
            FumaroleEntity low = spawn(context, new BlockPos(1, 1, 6));
            low.setTank(8);
            context.assertEquals(low.spill(world), 0, "fewer than 9 buckets: none");
            low.discard();
            full.discard();
        } finally {
            griefing.set(before, world.getServer());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itsTankIsSaved(TestContext context) {
        floor(context, 8);
        FumaroleEntity fumarole = spawn(context, new BlockPos(4, 1, 4));
        fumarole.setTank(17);
        NbtCompound nbt = new NbtCompound();
        fumarole.writeNbt(nbt);
        FumaroleEntity copy = ModEntities.FUMAROLE.create(context.getWorld());
        context.assertTrue(copy != null, "created");
        copy.readNbt(nbt);
        context.assertEquals(copy.getTank(), 17, "17 buckets back");
        fumarole.discard();
        context.complete();
    }
}
