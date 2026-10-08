package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartEntity;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartFuse;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.DetectorRailBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;

import java.util.UUID;

/**
 * The Boomcart: the hot potato's rules (lit, passed on with shorter and shorter extensions, never twice in a row by
 * the same player, retargeted at someone else), its loads (swapped, the other one given back), its blast by load (TNT
 * breaks blocks unless mobGriefing is off; a firework breaks none and hurts nobody, it shoves), and the rails (it
 * follows a curve, a detector rail sees it).
 */
public class BoomcartGameTests implements FabricGameTest {
    /** mobGriefing is changed for the whole server: these tests run alone in their batch. */
    private static final String GRIEFING_BATCH = "boomcart_griefing";

    private static void floor(TestContext context) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static ServerPlayerEntity player(TestContext context, BlockPos at, Hand hand, ItemStack held) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        BlockPos abs = context.getAbsolutePos(at);
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
        player.setStackInHand(hand, held);
        return player;
    }

    private static void remove(TestContext context, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) context.getWorld().getServer().getPlayerManager().remove(player);
    }

    // ---------------------------------------------------------------- the hot potato

    /** The rules alone: 8 s, then +3 s, +2 s, +1 s, then nothing; never twice in a row; it always blows. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theFuseRules(TestContext context) {
        BoomcartFuse fuse = new BoomcartFuse();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        context.assertEquals(fuse.strike(a), BoomcartFuse.Result.LIT, "lit");
        context.assertEquals(fuse.ticks(), BoomcartFuse.FUSE_TICKS, "8 s");
        context.assertEquals(fuse.strike(a), BoomcartFuse.Result.SAME_PLAYER, "not twice in a row");
        context.assertEquals(fuse.ticks(), BoomcartFuse.FUSE_TICKS, "the refusal changes nothing");
        int[] expected = {60, 40, 20, 0, 0};
        UUID[] passers = {b, a, b, a, b};
        int total = BoomcartFuse.FUSE_TICKS;
        for (int i = 0; i < expected.length; i++) {
            context.assertEquals(fuse.strike(passers[i]), BoomcartFuse.Result.PASSED, "passed " + i);
            context.assertEquals(fuse.extension(), expected[i], "extension " + i);
            total += expected[i];
            context.assertEquals(fuse.ticks(), total, "fuse after pass " + i);
        }
        int ticks = 0;
        while (!fuse.tick()) {
            ticks++;
            context.assertTrue(ticks < 10_000, "it blows in the end");
        }
        context.assertEquals(ticks + 1, total, "blows when the fuse is out");
        context.complete();
    }

    /** Lit by A it goes for B; A can't pass it on; B passes it on, +3 s, and it goes for A. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void passedOnBetweenTwoPlayers(TestContext context) {
        floor(context);
        BoomcartEntity boomcart = context.spawnEntity(ModEntities.BOOMCART, new BlockPos(3, 1, 3));
        ServerPlayerEntity a = player(context, new BlockPos(1, 1, 3), Hand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        ServerPlayerEntity b = player(context, new BlockPos(5, 1, 3), Hand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        boomcart.interact(a, Hand.MAIN_HAND);
        context.assertTrue(boomcart.isLit(), "lit");
        context.assertEquals(boomcart.panicTargetId(), b.getUuid(), "goes for B, never the lighter");
        int fuse = boomcart.getFuse();
        boomcart.interact(a, Hand.MAIN_HAND);
        context.assertEquals(boomcart.getFuse(), fuse, "A can't pass it on twice in a row");
        context.assertEquals(boomcart.panicTargetId(), b.getUuid(), "still goes for B");
        boomcart.interact(b, Hand.MAIN_HAND);
        context.assertEquals(boomcart.getFuse(), fuse + 60, "+3 s");
        context.assertEquals(boomcart.panicTargetId(), a.getUuid(), "now goes for A");
        boomcart.discard();
        remove(context, a, b);
        context.complete();
    }

    // ---------------------------------------------------------------- loads

    /** TNT by default; fed a rocket, it gives the TNT back; fed TNT, it gives the rocket back. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void loadsAreSwapped(TestContext context) {
        floor(context);
        BoomcartEntity boomcart = context.spawnEntity(ModEntities.BOOMCART, new BlockPos(3, 1, 3));
        context.assertTrue(boomcart.getLoad().isOf(Items.TNT), "TNT by default");
        PlayerEntity player = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.FIREWORK_ROCKET, 2));
        boomcart.interact(player, Hand.MAIN_HAND);
        context.assertTrue(boomcart.carriesFirework(), "loaded with a rocket");
        context.assertEquals(player.getMainHandStack().getCount(), 1, "one rocket eaten");
        context.assertTrue(player.getInventory().count(Items.TNT) == 1, "the TNT given back");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TNT));
        boomcart.interact(player, Hand.MAIN_HAND);
        context.assertTrue(boomcart.getLoad().isOf(Items.TNT), "TNT again");
        context.assertTrue(player.getInventory().count(Items.FIREWORK_ROCKET) == 1, "the rocket given back");
        boomcart.discard();
        context.complete();
    }

    // ---------------------------------------------------------------- the blast

    /** TNT: the floor under it is blown away; with mobGriefing off, nothing is broken. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = GRIEFING_BATCH, tickLimit = 40)
    public void tntBreaksBlocksUnlessMobGriefingIsOff(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        GameRules.BooleanRule griefing = world.getGameRules().get(GameRules.DO_MOB_GRIEFING);
        boolean before = griefing.get();
        griefing.set(false, world.getServer());
        BoomcartEntity first = context.spawnEntity(ModEntities.BOOMCART, new BlockPos(2, 1, 2));
        first.explode(world);
        context.assertTrue(context.getBlockState(new BlockPos(2, 0, 2)).isOf(Blocks.STONE), "mobGriefing off: nothing broken");
        griefing.set(true, world.getServer());
        BoomcartEntity second = context.spawnEntity(ModEntities.BOOMCART, new BlockPos(5, 1, 5));
        second.explode(world);
        boolean broken = !context.getBlockState(new BlockPos(5, 0, 5)).isOf(Blocks.STONE);
        griefing.set(before, world.getServer());
        context.assertTrue(broken, "mobGriefing on: the floor is blown away");
        context.assertTrue(first.isRemoved() && second.isRemoved(), "gone with the blast");
        context.complete();
    }

    /** A firework: sparks and a shove; the floor stays, the pig isn't hurt but pushed away. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void fireworkBreaksNothingAndHurtsNobody(TestContext context) {
        floor(context);
        BoomcartEntity boomcart = context.spawnEntity(ModEntities.BOOMCART, new BlockPos(3, 1, 3));
        boomcart.setLoad(new ItemStack(Items.FIREWORK_ROCKET));
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(5, 1, 3));
        float health = pig.getHealth();
        boomcart.explode(context.getWorld());
        context.assertTrue(pig.getVelocity().x > 0.1, "the pig is shoved away: " + pig.getVelocity());
        context.waitAndRun(5, () -> {
            context.assertEquals(pig.getHealth(), health, "the pig isn't hurt");
            for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
                context.assertTrue(context.getBlockState(new BlockPos(x, 0, z)).isOf(Blocks.STONE), "floor intact");
            }
            context.complete();
        });
    }

    // ---------------------------------------------------------------- rails

    /** Pushed along a straight rail, it takes the curve at its end and goes on along the other branch. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void itFollowsACurve(TestContext context) {
        floor(context);
        for (int x = 1; x <= 5; x++) {
            context.setBlockState(new BlockPos(x, 1, 1), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
        }
        context.setBlockState(new BlockPos(6, 1, 1), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.SOUTH_WEST));
        for (int z = 2; z <= 6; z++) {
            context.setBlockState(new BlockPos(6, 1, z), Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
        }
        BoomcartEntity boomcart = context.spawnEntity(ModEntities.BOOMCART, new BlockPos(1, 1, 1));
        boomcart.setVelocity(new Vec3d(0.35, 0, 0));
        BlockPos origin = context.getAbsolutePos(BlockPos.ORIGIN);
        context.waitAndRun(40, () -> {
            double x = boomcart.getX() - origin.getX(), z = boomcart.getZ() - origin.getZ();
            context.assertTrue(x > 6.3 && x < 6.7, "on the branch's line (x " + x + ")");
            context.assertTrue(z > 2.5, "went round the curve (z " + z + ")");
            boomcart.discard();
            context.complete();
        });
    }

    /** A detector rail under it is powered. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aDetectorRailSeesIt(TestContext context) {
        floor(context);
        BlockPos rail = new BlockPos(3, 1, 3);
        context.setBlockState(rail, Blocks.DETECTOR_RAIL.getDefaultState());
        BoomcartEntity boomcart = context.spawnEntity(ModEntities.BOOMCART, rail);
        context.waitAndRun(5, () -> {
            context.assertTrue(context.getBlockState(rail).get(DetectorRailBlock.POWERED), "the detector rail is powered");
            boomcart.discard();
            context.complete();
        });
    }
}
