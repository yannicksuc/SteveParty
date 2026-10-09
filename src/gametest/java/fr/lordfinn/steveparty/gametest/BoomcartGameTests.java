package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartEntity;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartFuse;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.Blocks;
import net.minecraft.block.DetectorRailBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import java.util.UUID;

/**
 * The Boomcart: the hot potato's rules (lit, passed on with shorter and shorter extensions, never twice in a row by
 * the same player, retargeted at someone else), its loads (swapped, the other one given back), its blast by load (TNT
 * breaks blocks unless mobGriefing is off; a firework breaks none and hurts nobody, it shoves, its show is its
 * rocket's own stars or a default one for a plain rocket, and the rocket is saved with it), and the rails (it
 * follows a curve, a detector rail sees it).
 */
public class BoomcartGameTests implements FabricGameTest {
    /** mobGriefing is changed for the whole server: these tests run alone in their batch. */
    private static final String GRIEFING_BATCH = "boomcart_griefing";

    private static ServerPlayerEntity player(TestContext context, BlockPos at, Hand hand, ItemStack held) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        TestPlayers.placeOn(context, player, at, 0);
        player.setStackInHand(hand, held);
        return player;
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
        TestBoards.floor(context, 8);
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
        TestPlayers.remove(context, a, b);
        context.complete();
    }

    // ---------------------------------------------------------------- loads

    /** TNT by default; fed a rocket, it gives the TNT back; fed TNT, it gives the rocket back. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void loadsAreSwapped(TestContext context) {
        TestBoards.floor(context, 8);
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
        TestBoards.floor(context, 8);
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
        TestBoards.floor(context, 8);
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

    /** A rocket with two stars of its own, flight 2. */
    private static ItemStack starRocket() {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(2, List.of(
                new FireworkExplosionComponent(FireworkExplosionComponent.Type.CREEPER, IntList.of(0xE02020),
                        IntList.of(0x2040E0), true, false),
                new FireworkExplosionComponent(FireworkExplosionComponent.Type.BURST, IntList.of(0x20E040, 0xFFFFFF),
                        IntList.of(), false, true))));
        return rocket;
    }

    /**
     * Its show is its rocket's own: every star (shape, colours, fade, trail, twinkle); a plain rocket (flight only)
     * gets the default ball and star. Neither breaks a block nor hurts anyone, a survival player standing by included.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void fireworkShowIsTheRocketsOwn(TestContext context) {
        TestBoards.floor(context, 8);
        ItemStack stars = starRocket();
        BoomcartEntity custom = context.spawnEntity(ModEntities.BOOMCART, new BlockPos(2, 1, 2));
        custom.setLoad(stars);
        ItemStack plainRocket = new ItemStack(Items.FIREWORK_ROCKET);
        plainRocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(3, List.of()));
        BoomcartEntity plain = context.spawnEntity(ModEntities.BOOMCART, new BlockPos(5, 1, 5));
        plain.setLoad(plainRocket);
        ServerPlayerEntity player = player(context, new BlockPos(3, 1, 3), Hand.MAIN_HAND, ItemStack.EMPTY);
        player.changeGameMode(GameMode.SURVIVAL);
        PigEntity pig = context.spawnMob(EntityType.PIG, new BlockPos(4, 1, 4));
        float playerHealth = player.getHealth(), pigHealth = pig.getHealth();
        custom.explode(context.getWorld());
        plain.explode(context.getWorld());
        context.assertEquals(custom.shownBurst(), stars.get(DataComponentTypes.FIREWORKS).explosions(),
                "the rocket's own stars");
        context.assertEquals(plain.shownBurst(), BoomcartEntity.DEFAULT_BURST, "a plain rocket: the default show");
        context.waitAndRun(5, () -> {
            context.assertEquals(player.getHealth(), playerHealth, "the player isn't hurt");
            context.assertEquals(pig.getHealth(), pigHealth, "the pig isn't hurt");
            for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
                context.assertTrue(context.getBlockState(new BlockPos(x, 0, z)).isOf(Blocks.STONE), "floor intact");
            }
            TestPlayers.remove(context, player);
            context.complete();
        });
    }

    /** Saved and loaded again, it still carries the same rocket, its stars included. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aSavedBoomcartKeepsItsRockets(TestContext context) {
        ItemStack stars = starRocket();
        BoomcartEntity boomcart = context.spawnEntity(ModEntities.BOOMCART, new BlockPos(3, 1, 3));
        boomcart.setLoad(stars);
        NbtCompound nbt = new NbtCompound();
        boomcart.writeNbt(nbt);
        BoomcartEntity reloaded = ModEntities.BOOMCART.create(context.getWorld());
        context.assertTrue(reloaded != null, "a new Boomcart");
        reloaded.readNbt(nbt);
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(reloaded.getLoad(), stars),
                "the same rocket: " + reloaded.getLoad().getComponents());
        boomcart.discard();
        context.complete();
    }

    // ---------------------------------------------------------------- rails

    /** Pushed along a straight rail, it takes the curve at its end and goes on along the other branch. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void itFollowsACurve(TestContext context) {
        TestBoards.floor(context, 8);
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
        TestBoards.floor(context, 8);
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
