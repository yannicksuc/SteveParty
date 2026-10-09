package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.entities.ModEntities;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.Entity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronSpawns;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronGoals;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronBlast;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronScreenHandler;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronPumping;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronRiding;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.util.List;

/**
 * The Trichaudron: born at least half full; buckets take lava from its tank and pour some in (lava only); pumping drinks
 * without taking the source; whoever empties it can tame it like a horse, riding it (the emptier its tank, the likelier);
 * tamed and saddled it is ridden, the riders' keys added up; an untamed one throws its rider off;
 * it swims in lava; the charged jump; the blast (fire mostly, armour and shields); the spill on death; its save.
 */
public class TrichaudronGameTests implements SteveGameTest {
    /** Beyond the template: its shots and strips reach 22 blocks east of the template's corner. */
    @Override
    public int landAround() {
        return 16;
    }

    /** mobGriefing is changed for the whole server: these tests run alone in their batch. */
    private static final String GRIEFING_BATCH = "trichaudron_griefing";

    private static TrichaudronEntity spawn(TestContext context, BlockPos at) {
        TrichaudronEntity trichaudron = context.spawnEntity(ModEntities.TRICHAUDRON, at);
        trichaudron.setAiDisabled(true);
        return trichaudron;
    }

    /** A clear strip beyond the 8-block template (whatever stands there), floored. */
    private static void strip(TestContext context, int length) {
        strip(context, length, 8);
    }

    private static void strip(TestContext context, int length, int height) {
        for (int x = -1; x < length; x++) for (int z = 3; z < 14; z++) {
            context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y < height; y++) context.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    private static void pool(TestContext context, BlockPos corner, int w, int d) {
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) context.setBlockState(corner.add(x, 0, z), Blocks.LAVA);
    }

    private static ServerPlayerEntity player(TestContext context, ItemStack held) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, held);
        return player;
    }

    /** Facing +x (yaw -90), its centre head at rest. */
    private static TrichaudronEntity facingEast(TestContext context, BlockPos at) {
        TrichaudronEntity trichaudron = spawn(context, at);
        trichaudron.setYaw(-90);
        trichaudron.setBodyYaw(-90);
        trichaudron.setHeadYaw(-90);
        for (int head = 0; head < TrichaudronEntity.HEADS.length; head++) trichaudron.restHead(head);
        return trichaudron;
    }

    private static int floorY(TestContext context) {
        return context.getAbsolutePos(new BlockPos(0, 1, 0)).getY();
    }

    /** Empties its tank with a bucket in this player's hands: he is one who emptied it. */
    private static void empty(TrichaudronEntity trichaudron, ServerPlayerEntity player) {
        trichaudron.setTank(1);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
        player.interact(trichaudron, Hand.MAIN_HAND);
    }

    private static void tame(TrichaudronEntity trichaudron, ServerPlayerEntity player) {
        empty(trichaudron, player);
        trichaudron.tame(player); // what a successful ride does
    }

    // ---------------------------------------------------------------- spawn, tank

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spawnsAtLeastHalfFullWithItsStats(TestContext context) {
        TestBoards.floor(context, 8);
        ServerWorld world = context.getWorld();
        for (int i = 0; i < 5; i++) {
            TrichaudronEntity trichaudron = ModEntities.TRICHAUDRON.create(world);
            context.assertTrue(trichaudron != null, "created");
            BlockPos at = context.getAbsolutePos(new BlockPos(4, 1, 4));
            trichaudron.refreshPositionAndAngles(at, 0, 0);
            trichaudron.initialize(world, world.getLocalDifficulty(at), SpawnReason.NATURAL, null);
            context.assertTrue(trichaudron.getTank() >= TrichaudronEntity.TANK_MAX / 2, "at least half full: " + trichaudron.getTank());
            context.assertEquals(trichaudron.getMaxHealth(), (float) TrichaudronEntity.MAX_HEALTH, "80 HP");
            context.assertTrue(trichaudron.isFireImmune(), "fire immune");
            context.assertFalse(trichaudron.isTamed(), "wild");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bucketsTakeAndPourOnlyLava(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        trichaudron.setTank(5);
        ServerPlayerEntity player = player(context, new ItemStack(Items.BUCKET));
        player.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.LAVA_BUCKET), "an empty bucket comes back full");
        context.assertEquals(trichaudron.getTank(), 4, "one bucket taken");
        player.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.BUCKET), "a lava bucket is poured in");
        context.assertEquals(trichaudron.getTank(), 5, "one bucket poured");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        player.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.WATER_BUCKET), "water does not go in");
        context.assertEquals(trichaudron.getTank(), 5, "lava only");
        trichaudron.setTank(TrichaudronEntity.TANK_MAX);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        player.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.LAVA_BUCKET), "a full tank takes no more");
        TestPlayers.remove(context, player);
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_pump")
    public void pumpingLeavesTheSource(TestContext context) {
        TestBoards.floor(context, 16);
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(2, 1, 7));
        trichaudron.setTank(0);
        pool(context, new BlockPos(9, 0, 7), 1, 1);
        BlockPos source = context.getAbsolutePos(new BlockPos(9, 0, 7));
        context.assertTrue(trichaudron.canReach(source), "within the nozzle's reach");
        context.assertTrue(trichaudron.pump(source), "drinks from a lone source");
        context.assertTrue(TrichaudronPumping.isSource(context.getWorld(), source), "the source is still there");
        context.assertEquals(trichaudron.getTank(), 1, "one bucket in the tank");
        context.assertFalse(trichaudron.pump(source), "not twice within 3 s");
        TrichaudronPumping pace = new TrichaudronPumping();
        for (int i = 0; i < TrichaudronPumping.MAX_PER_MINUTE; i++) pace.record(i * TrichaudronPumping.MIN_GAP);
        long last = (TrichaudronPumping.MAX_PER_MINUTE - 1) * (long) TrichaudronPumping.MIN_GAP;
        context.assertFalse(pace.rateAllows(last + TrichaudronPumping.MIN_GAP), "at most 6 a minute");
        context.assertTrue(pace.rateAllows(1200), "a minute later it may again");
        context.setBlockState(new BlockPos(9, 0, 7), Blocks.STONE);
        trichaudron.discard();
        context.complete();
    }

    // ---------------------------------------------------------------- taming

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emptyingItMarksTheEmptierWithoutAnger(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        ServerPlayerEntity player = player(context, new ItemStack(Items.BUCKET)), other = player(context, ItemStack.EMPTY);
        trichaudron.setTank(2);
        player.interact(trichaudron, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
        player.interact(trichaudron, Hand.MAIN_HAND);
        context.assertEquals(trichaudron.getTank(), 0, "emptied");
        context.assertTrue(trichaudron.isEmptier(player), "he emptied it");
        context.assertFalse(trichaudron.isEmptier(other), "not the other");
        context.assertFalse(trichaudron.hasGrudge(player), "it doesn't mind");
        context.assertFalse(trichaudron.isTamed(), "not tamed yet");
        TestPlayers.remove(context, player, other);
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itsTamingChanceGrowsAsItsTankEmptiesAndWithItsTemper(TestContext context) {
        context.assertEquals(TrichaudronEntity.tameChance(0, 0), TrichaudronEntity.TAME_EMPTY, "empty: its best chance");
        context.assertEquals(TrichaudronEntity.tameChance(TrichaudronEntity.TANK_MAX, 0), 0, "full: none");
        int half = TrichaudronEntity.tameChance(TrichaudronEntity.TANK_MAX / 2, 0);
        context.assertTrue(half > 0 && half < TrichaudronEntity.TAME_EMPTY, "half full: in between, " + half);
        context.assertEquals(TrichaudronEntity.tameChance(TrichaudronEntity.TANK_MAX, TrichaudronEntity.TEMPER_STEP),
                TrichaudronEntity.TEMPER_STEP, "every failed try a little more");
        context.assertEquals(TrichaudronEntity.tameChance(0, 100), 100, "at most certain");
        context.complete();
    }

    /** A wild one with this temper (a save's), its tank empty. */
    private static TrichaudronEntity withTemper(TestContext context, BlockPos at, int temper) {
        TrichaudronEntity trichaudron = spawn(context, at);
        NbtCompound nbt = new NbtCompound();
        trichaudron.writeNbt(nbt);
        nbt.putInt("Temper", temper);
        trichaudron.readNbt(nbt);
        return trichaudron;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_tame_ride", tickLimit = 200)
    public void onlyWhoEmptiedItTamesItByRiding(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = withTemper(context, new BlockPos(4, 1, 4), 100); // a sure success for an emptier
        ServerPlayerEntity emptier = player(context, ItemStack.EMPTY);
        empty(trichaudron, emptier);
        emptier.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        emptier.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(emptier.getVehicle() == trichaudron, "on its back");
        context.assertFalse(trichaudron.hasGrudge(emptier), "no anger at the one who emptied it");
        context.waitAndRun(TrichaudronEntity.THROW_MIN + TrichaudronEntity.THROW_SPREAD + 5, () -> {
            context.assertTrue(trichaudron.isTamed() && trichaudron.isOwner(emptier), "it gave in: tamed, him its owner");
            context.assertTrue(emptier.getVehicle() == trichaudron, "and he is still on");
            TestPlayers.remove(context, emptier);
            trichaudron.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_tame_stranger", tickLimit = 200)
    public void aStrangerOnItsBackIsThrownAndProvokesIt(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = withTemper(context, new BlockPos(4, 1, 4), 100); // even its surest temper
        trichaudron.setTank(0);
        ServerPlayerEntity stranger = player(context, ItemStack.EMPTY);
        stranger.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(stranger.getVehicle() == trichaudron, "it lets him climb on");
        context.assertTrue(trichaudron.hasGrudge(stranger), "a stranger on its back: provoked");
        context.waitAndRun(TrichaudronEntity.THROW_MIN + TrichaudronEntity.THROW_SPREAD + 5, () -> {
            context.assertFalse(stranger.hasVehicle(), "thrown off");
            context.assertFalse(trichaudron.isTamed(), "and still wild");
            TestPlayers.remove(context, stranger);
            trichaudron.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itsScreenOpensLikeAHorses(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity wild = spawn(context, new BlockPos(2, 1, 2));
        ServerPlayerEntity owner = player(context, ItemStack.EMPTY), other = player(context, ItemStack.EMPTY);
        wild.openInventory(owner);
        context.assertFalse(owner.currentScreenHandler instanceof TrichaudronScreenHandler, "wild: no screen");
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(5, 1, 5));
        tame(trichaudron, owner);
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SADDLE));
        owner.interact(trichaudron, Hand.MAIN_HAND);
        owner.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        owner.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(owner.getVehicle() == trichaudron, "the owner rides it");
        trichaudron.openInventory(owner); // what the inventory key sends while riding
        context.assertTrue(owner.currentScreenHandler instanceof TrichaudronScreenHandler, "its rider's inventory key: its screen");
        owner.closeHandledScreen();
        trichaudron.openInventory(other);
        context.assertFalse(other.currentScreenHandler instanceof TrichaudronScreenHandler, "ridden by someone else: not for a passer-by");
        owner.stopRiding();
        other.setSneaking(true);
        other.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(other.currentScreenHandler instanceof TrichaudronScreenHandler, "nobody on: a sneaking click opens it");
        context.assertFalse(other.hasVehicle(), "and doesn't climb on");
        other.closeHandledScreen();
        TestPlayers.remove(context, owner, other);
        wild.discard();
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tamedAndSaddledItIsRidden(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        ServerPlayerEntity owner = player(context, ItemStack.EMPTY), friend = player(context, ItemStack.EMPTY);
        tame(trichaudron, owner);
        context.assertTrue(trichaudron.isTamed(), "tamed");
        owner.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        owner.interact(trichaudron, Hand.MAIN_HAND);
        context.assertFalse(owner.hasVehicle(), "no saddle: no ride");
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SADDLE));
        owner.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(trichaudron.isSaddled(), "saddled");
        owner.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(owner.getVehicle() == trichaudron, "the owner rides it");
        friend.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(friend.getVehicle() == trichaudron, "a second rider");
        context.assertTrue(trichaudron.riderOf(0) == owner && trichaudron.riderOf(1) == friend, "the centre head, then the left");
        context.assertTrue(trichaudron.isSteered(), "steered");
        TestPlayers.remove(context, owner, friend);
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void anUntamedOneThrowsItsRiderOff(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        ServerPlayerEntity player = player(context, ItemStack.EMPTY);
        player.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(player.getVehicle() == trichaudron, "it lets him climb on");
        trichaudron.sprayOff(player);
        context.assertFalse(player.hasVehicle(), "sprayed off");
        context.assertTrue(player.getVelocity().y >= TrichaudronEntity.THROW_UP - 0.01, "thrown up: " + player.getVelocity());
        context.assertTrue(player.isOnFire(), "a little fire");
        // fire resistance: no fire; a shield toward the head: no throw at all
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200));
        player.extinguish();
        player.interact(trichaudron, Hand.MAIN_HAND);
        trichaudron.sprayOff(player);
        context.assertFalse(player.isOnFire(), "fire resistance: no fire");
        // and on its own, a few seconds later
        player.setVelocity(Vec3d.ZERO);
        player.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(player.getVehicle() == trichaudron, "on again");
        context.waitAndRun(TrichaudronEntity.THROW_MIN + TrichaudronEntity.THROW_SPREAD + 5, () -> {
            context.assertFalse(player.hasVehicle(), "thrown off within a few seconds");
            context.assertFalse(trichaudron.isTamed(), "and still wild");
            TestPlayers.remove(context, player);
            trichaudron.discard();
            context.complete();
        });
    }

    // ---------------------------------------------------------------- riding

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ridersKeysAddUp(TestContext context) {
        ServerWorld world = context.getWorld();
        PigEntity a = EntityType.PIG.create(world), b = EntityType.PIG.create(world), c = EntityType.PIG.create(world);
        context.assertTrue(a != null && b != null && c != null, "created");
        a.forwardSpeed = 1;
        b.forwardSpeed = -1;
        a.sidewaysSpeed = 1;
        b.sidewaysSpeed = -1;
        TrichaudronRiding.Steer cancel = TrichaudronRiding.combine(List.of(a, b), e -> false);
        context.assertTrue(cancel.forward() == 0 && cancel.turn() == 0, "opposite keys cancel");
        b.forwardSpeed = 1;
        c.forwardSpeed = 1;
        b.sidewaysSpeed = 1;
        c.sidewaysSpeed = 1;
        TrichaudronRiding.Steer three = TrichaudronRiding.combine(List.of(a, b, c), e -> false);
        context.assertTrue(three.forward() == 3 && three.turn() == 3, "agreeing keys add up");
        float one = TrichaudronRiding.speedFor(1), all = TrichaudronRiding.speedFor(3);
        context.assertTrue(one > TrichaudronEntity.SPEED * 1.8, "one rider: much faster than its wild crawl");
        context.assertTrue(all > one * 1.8, "three riders: much faster than one (" + all + " vs " + one + ")");
        context.assertTrue(TrichaudronRiding.turnFor(3) == 3 * TrichaudronRiding.turnFor(1), "three turning: three times as fast");
        context.assertTrue(TrichaudronRiding.speedFor(-1) < one, "backward slower");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_ride")
    public void aRiderDrivesIt(TestContext context) {
        strip(context, 30);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(2, 1, 8));
        trichaudron.setAiDisabled(false);
        ServerPlayerEntity owner = player(context, ItemStack.EMPTY);
        tame(trichaudron, owner);
        trichaudron.setTank(TrichaudronEntity.TANK_MAX); // full of fuel
        trichaudron.inventory.setStack(0, new ItemStack(Items.SADDLE));
        owner.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        owner.startRiding(trichaudron);
        double startX = trichaudron.getX();
        // its moves, driven here tick by tick (the server would call them): one rider's forward key
        owner.forwardSpeed = 1;
        for (int tick = 0; tick < 30; tick++) trichaudron.travel(Vec3d.ZERO);
        context.assertTrue(trichaudron.getX() - startX > 3, "driven forward: " + (trichaudron.getX() - startX));
        TestPlayers.remove(context, owner);
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_steady", tickLimit = 260)
    public void ridenStillItHoldsItsHeadingAndItsBodyFacesIt(TestContext context) {
        strip(context, 30);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(6, 1, 8));
        trichaudron.setAiDisabled(false); // its goals (wandering, looking around) on
        ServerPlayerEntity owner = player(context, ItemStack.EMPTY);
        tame(trichaudron, owner);
        trichaudron.setTank(TrichaudronEntity.TANK_MAX);
        trichaudron.inventory.setStack(0, new ItemStack(Items.SADDLE));
        owner.startRiding(trichaudron);
        float heading = trichaudron.getYaw();
        float[] worst = {0, 0};
        context.runAtEveryTick(() -> {
            worst[0] = Math.max(worst[0], Math.abs(MathHelper.wrapDegrees(trichaudron.getYaw() - heading)));
            worst[1] = Math.max(worst[1], Math.abs(MathHelper.wrapDegrees(trichaudron.bodyYaw - trichaudron.getYaw())));
        });
        context.runAtTick(240, () -> {
            context.assertTrue(worst[0] < 0.5f, "no input: its heading holds, drift " + worst[0]);
            context.assertTrue(worst[1] < 0.5f, "its body faces its heading, off by " + worst[1]);
            TestPlayers.remove(context, owner);
            trichaudron.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_fuel")
    public void lavaIsItsFuel(TestContext context) {
        strip(context, 30);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(2, 1, 8));
        trichaudron.setAiDisabled(false);
        ServerPlayerEntity owner = player(context, ItemStack.EMPTY);
        tame(trichaudron, owner);
        trichaudron.inventory.setStack(0, new ItemStack(Items.SADDLE));
        owner.startRiding(trichaudron);
        owner.forwardSpeed = 1;
        double[] moved = new double[3];
        int[] tanks = {0, TrichaudronEntity.TANK_MAX / 2, TrichaudronEntity.TANK_MAX};
        for (int k = 0; k < 3; k++) {
            trichaudron.setTank(tanks[k]);
            trichaudron.setVelocity(Vec3d.ZERO);
            double startX = trichaudron.getX();
            for (int tick = 0; tick < 20; tick++) trichaudron.travel(Vec3d.ZERO);
            moved[k] = trichaudron.getX() - startX;
        }
        context.assertTrue(Math.abs(moved[0]) < 0.25, "empty: it isn't driven, " + moved[0]);
        context.assertTrue(moved[1] > 0.5 && moved[1] < moved[2], "half full: slower than full, " + moved[1] + " < " + moved[2]);
        // a lava bucket from the saddle: poured in, not fired
        trichaudron.setTank(5);
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        trichaudron.riderClick(owner);
        context.assertEquals(trichaudron.getTank(), 6, "a bucket more in its tank");
        context.assertTrue(owner.getMainHandStack().isOf(Items.BUCKET), "the bucket left empty");
        TestPlayers.remove(context, owner);
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itsRidersHoldTheCentreThenLeftThenRightHead(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        context.assertTrue(TrichaudronEntity.HEADS[0].suffix().equals("_c") && TrichaudronEntity.HEADS[1].suffix().equals("_l")
                && TrichaudronEntity.HEADS[2].suffix().equals("_r"), "heads: centre, left, right");
        ServerPlayerEntity[] riders = {player(context, ItemStack.EMPTY), player(context, ItemStack.EMPTY), player(context, ItemStack.EMPTY)};
        for (int i = 0; i < 3; i++) {
            riders[i].startRiding(trichaudron, true);
            context.assertEquals(trichaudron.headOf(riders[i]), i, "rider " + (i + 1) + " holds head " + TrichaudronEntity.HEADS[i].suffix());
        }
        TestPlayers.remove(context, riders);
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_lava_leap", tickLimit = 140)
    public void theChargedJumpLeapsOutOfTheLava(TestContext context) {
        // a pool 8 x 8, 2 deep, its rim one block over the lava (low: the test's room is only so high)
        for (int x = -1; x <= 8; x++) for (int z = -1; z <= 8; z++) for (int y = 0; y <= 4; y++) {
            boolean wall = x < 0 || x > 7 || z < 0 || z > 7 || y == 0;
            context.setBlockState(new BlockPos(x, y, z), wall ? (y <= 3 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState())
                    : y <= 2 ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState());
        }
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        trichaudron.setAiDisabled(false);
        trichaudron.setTank(10);
        double surface = context.getAbsolutePos(new BlockPos(0, 2, 0)).getY() + 0.9;
        double[] top = {Double.NEGATIVE_INFINITY};
        context.runAtEveryTick(() -> top[0] = Math.max(top[0], trichaudron.getY()));
        context.waitAndRun(40, () -> {
            context.assertTrue(trichaudron.isSwimmingInLava(), "deep in the lava");
            top[0] = Double.NEGATIVE_INFINITY;
            context.assertTrue(trichaudron.thrusterJump(TrichaudronRiding.CHARGE_MAX), "it leaps from the lava");
            context.waitAndRun(20, () -> {
                context.assertTrue(top[0] > surface + 1.5, "out of the lava, over its rim: " + (top[0] - surface));
                trichaudron.discard();
                for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) for (int y = 1; y <= 2; y++) context.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_float", tickLimit = 220)
    public void itFloatsSteadyInCalmLava(TestContext context) {
        // a pool 8 x 8, 5 deep, walled
        for (int x = -1; x <= 8; x++) for (int z = -1; z <= 8; z++) for (int y = 0; y <= 6; y++) {
            boolean wall = x < 0 || x > 7 || z < 0 || z > 7 || y == 0;
            context.setBlockState(new BlockPos(x, y, z), wall ? Blocks.STONE.getDefaultState() : y <= 5 ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState());
        }
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        double[] range = {Double.MAX_VALUE, -Double.MAX_VALUE, 0};
        context.runAtEveryTick(() -> {
            if (trichaudron.age < 100) return; // settled first
            range[0] = Math.min(range[0], trichaudron.getY());
            range[1] = Math.max(range[1], trichaudron.getY());
            range[2] = Math.max(range[2], Math.abs(trichaudron.getVelocity().y));
        });
        context.runAtTick(200, () -> {
            context.assertTrue(trichaudron.isSwimmingInLava(), "floating");
            context.assertTrue(range[1] - range[0] < 0.1, "steady: its height varies by " + (range[1] - range[0]));
            context.assertTrue(range[2] < 0.03, "no kicks: vertical speed at most " + range[2]);
            trichaudron.discard();
            for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) for (int y = 1; y <= 5; y++) context.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_swim", tickLimit = 140)
    public void itSwimsInLava(TestContext context) {
        // a pool 8 x 8, 6 deep, walled
        for (int x = -1; x <= 8; x++) for (int z = -1; z <= 8; z++) for (int y = 0; y <= 6; y++) {
            boolean wall = x < 0 || x > 7 || z < 0 || z > 7 || y == 0;
            context.setBlockState(new BlockPos(x, y, z), wall ? Blocks.STONE.getDefaultState() : y <= 5 ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState());
        }
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        double bottom = trichaudron.getY();
        context.waitAndRun(100, () -> {
            context.assertTrue(trichaudron.getY() > bottom + 2, "floated up: " + (trichaudron.getY() - bottom));
            context.assertTrue(trichaudron.isInLava(), "still in the lava, swimming");
            trichaudron.discard();
            for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) for (int y = 1; y <= 5; y++) context.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_jump", tickLimit = 60)
    public void theChargedJumpLeapsHighAndCostsLava(TestContext context) {
        strip(context, 20, 32);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(2, 1, 8));
        trichaudron.setAiDisabled(false); // a mob without AI doesn't move at all
        trichaudron.setTank(5);
        context.waitAndRun(4, () -> {
            context.assertFalse(trichaudron.thrusterJump(TrichaudronRiding.CHARGE_MIN - 1), "too short a charge: nothing");
            double start = trichaudron.getY();
            context.assertTrue(trichaudron.thrusterJump(TrichaudronRiding.CHARGE_MAX), "a full charge leaps");
            context.assertEquals(trichaudron.getTank(), 3, "two buckets for a full charge");
            context.assertTrue(trichaudron.getVelocity().y >= TrichaudronRiding.JUMP_UP_MAX - 0.01, "up fast: " + trichaudron.getVelocity());
            context.waitAndRun(10, () -> {
                context.assertTrue(trichaudron.getY() > start + 6, "high up: " + (trichaudron.getY() - start));
                // a ledge to climb: a wall 5 high in front, its top clear
                trichaudron.discard();
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_climb")
    public void itsHeadsFindALedgeToClimb(TestContext context) {
        strip(context, 20, 20);
        // a wall 5 high, 3 blocks ahead of its shell
        for (int y = 1; y <= 5; y++) for (int z = 3; z < 14; z++) context.setBlockState(new BlockPos(7, y, z), Blocks.STONE);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(2, 1, 8));
        Vec3d ledge = TrichaudronRiding.findLedge(context.getWorld(), trichaudron.getPos(), trichaudron.getYaw(),
                TrichaudronEntity.WIDTH / 2, TrichaudronEntity.HEIGHT, trichaudron.getY());
        context.assertTrue(ledge != null, "a ledge found");
        context.assertTrue(Math.abs(ledge.y - (floorY(context) + 5)) < 0.01, "on top of the wall: " + ledge);
        for (int y = 6; y <= 7; y++) for (int z = 3; z < 14; z++) context.setBlockState(new BlockPos(7, y, z), Blocks.STONE);
        for (int y = 8; y <= 15; y++) for (int z = 3; z < 14; z++) context.setBlockState(new BlockPos(7, y, z), Blocks.STONE);
        context.assertTrue(TrichaudronRiding.findLedge(context.getWorld(), trichaudron.getPos(), trichaudron.getYaw(),
                TrichaudronEntity.WIDTH / 2, TrichaudronEntity.HEIGHT, trichaudron.getY()) == null, "a wall too high: none");
        for (int y = 1; y <= 15; y++) for (int z = 3; z < 14; z++) context.setBlockState(new BlockPos(7, y, z), Blocks.AIR);
        trichaudron.discard();
        context.complete();
    }

    // ---------------------------------------------------------------- the blast

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_blast", tickLimit = 40)
    public void theBlastBurnsShovesAndCostsABucket(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        trichaudron.setTank(5);
        Vec3d nozzle = trichaudron.nozzle(0);
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        pig.setAiDisabled(true);
        pig.refreshPositionAndAngles(nozzle.x + 6, floorY(context), nozzle.z, 0, 0);
        float before = pig.getHealth();
        List<LivingEntity> hurt = trichaudron.blast(0, pig);
        context.assertTrue(hurt.contains(pig), "the pig is hit");
        float taken = before - pig.getHealth();
        context.assertTrue(Math.abs(taken - (TrichaudronBlast.FIRE_DAMAGE + TrichaudronBlast.PHYSICAL_DAMAGE)) < 0.6f, "fire and physical: " + taken);
        context.assertTrue(pig.isOnFire(), "set on fire");
        context.assertTrue(pig.getVelocity().x > 1.5 && pig.getVelocity().y > 0.3, "shoved hard: " + pig.getVelocity());
        context.assertEquals(trichaudron.getTank(), 4, "a bucket spent");
        // a fire-proof mob: only the physical part, no fire
        PigEntity proof = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        proof.setAiDisabled(true);
        proof.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200));
        proof.refreshPositionAndAngles(nozzle.x + 9, floorY(context), nozzle.z + 4, 0, 0);
        float proofBefore = proof.getHealth();
        trichaudron.restHead(0);
        trichaudron.blast(0, proof);
        context.assertTrue(Math.abs(proofBefore - proof.getHealth() - TrichaudronBlast.PHYSICAL_DAMAGE) < 0.6f, "fire resistance: physical only");
        context.assertFalse(proof.isOnFire(), "and no fire");
        // empty: a weak puff, no fire, free
        PigEntity near = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        near.setAiDisabled(true);
        near.refreshPositionAndAngles(nozzle.x + 3, floorY(context), nozzle.z - 4, 0, 0);
        trichaudron.setTank(0);
        trichaudron.restHead(0);
        float nearBefore = near.getHealth();
        trichaudron.blast(0, near);
        float puff = nearBefore - near.getHealth();
        context.assertTrue(puff > 0 && puff <= TrichaudronBlast.PUFF_DAMAGE + 0.01f, "a weak puff: " + puff);
        context.assertFalse(near.isOnFire(), "the puff burns nothing");
        context.assertEquals(trichaudron.getTank(), 0, "the puff is free");
        pig.discard();
        proof.discard();
        near.discard();
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_shield", tickLimit = 40)
    public void aShieldStopsTheBlast(TestContext context) {
        strip(context, 24);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        trichaudron.setTank(5);
        Vec3d nozzle = trichaudron.nozzle(0);
        ServerPlayerEntity player = player(context, ItemStack.EMPTY);
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
        player.refreshPositionAndAngles(nozzle.x + 5, floorY(context), nozzle.z, 90, 0); // facing -x, the jet
        player.setHeadYaw(90);
        player.setCurrentHand(Hand.OFF_HAND);
        PigEntity behind = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        behind.setAiDisabled(true);
        behind.refreshPositionAndAngles(nozzle.x + 9, floorY(context), nozzle.z, 0, 0);
        float health = player.getHealth(), pigHealth = behind.getHealth();
        context.assertTrue(TrichaudronBlast.shields(player, new Vec3d(1, 0, 0)), "the shield is up toward the jet");
        trichaudron.blast(0, player);
        context.assertEquals(player.getHealth(), health, "no damage");
        context.assertFalse(player.isOnFire(), "no fire");
        context.assertTrue(player.getVelocity().horizontalLength() < 0.2, "no shove: " + player.getVelocity());
        context.assertEquals(behind.getHealth(), pigHealth, "the steam stops on the shield");
        TestPlayers.remove(context, player);
        behind.discard();
        trichaudron.discard();
        context.complete();
    }

    private static ItemStack armour(TestContext context, Item item, int protection) {
        ItemStack stack = new ItemStack(item);
        RegistryEntry<Enchantment> entry = context.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.PROTECTION);
        if (protection > 0) stack.addEnchantment(entry, protection);
        return stack;
    }

    private static void wear(TestContext context, ServerPlayerEntity player, Item[] set, int protection) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) player.equipStack(slots[i], armour(context, set[i], protection));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_armour", tickLimit = 40)
    public void heavyEnchantedArmourStopsThePhysicalPart(TestContext context) {
        strip(context, 22);
        Item[] diamond = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        Item[] netherite = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        Item[] iron = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
        ServerPlayerEntity d3 = player(context, ItemStack.EMPTY), d2 = player(context, ItemStack.EMPTY),
                n4 = player(context, ItemStack.EMPTY), i4 = player(context, ItemStack.EMPTY);
        wear(context, d3, diamond, 3);
        wear(context, d2, diamond, 2);
        wear(context, n4, netherite, 4);
        wear(context, i4, iron, 4);
        context.waitAndRun(2, () -> { // the armour's attributes apply on the next tick
            context.assertTrue(TrichaudronBlast.armourStops(d3), "full diamond Protection III");
            context.assertTrue(TrichaudronBlast.armourStops(n4), "full netherite Protection IV");
            context.assertFalse(TrichaudronBlast.armourStops(d2), "diamond Protection II: not enough");
            context.assertFalse(TrichaudronBlast.armourStops(i4), "iron: not enough armour");
            // fire resistance and heavy armour: nothing taken, but shoved all the same
            TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
            trichaudron.setTank(5);
            Vec3d nozzle = trichaudron.nozzle(0);
            d3.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200));
            d3.refreshPositionAndAngles(nozzle.x + 5, floorY(context), nozzle.z, -90, 0); // back to the jet
            float health = d3.getHealth();
            context.assertEquals(TrichaudronBlast.damageOn(d3), 0f, "no damage at all");
            trichaudron.blast(0, d3);
            context.assertEquals(d3.getHealth(), health, "unhurt");
            context.assertTrue(d3.getVelocity().x > 1.0, "but shoved: " + d3.getVelocity());
            context.assertEquals(TrichaudronBlast.damageOn(i4), TrichaudronBlast.FIRE_DAMAGE + TrichaudronBlast.PHYSICAL_DAMAGE, "iron: all of it");
            TestPlayers.remove(context, d3, d2, n4, i4);
            trichaudron.discard();
            context.complete();
        });
    }

    // ---------------------------------------------------------------- death, heads, save

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = GRIEFING_BATCH, tickLimit = 40)
    public void deathSpillsLavaOnlyWithMobGriefing(TestContext context) {
        TestBoards.floor(context, 8);
        ServerWorld world = context.getWorld();
        GameRules.BooleanRule griefing = world.getGameRules().get(GameRules.DO_MOB_GRIEFING);
        boolean before = griefing.get();
        try {
            griefing.set(false, world.getServer());
            TrichaudronEntity calm = spawn(context, new BlockPos(1, 1, 1));
            calm.setTank(TrichaudronEntity.TANK_MAX);
            context.assertEquals(calm.spill(world), 0, "no mobGriefing: no lava placed");
            context.assertEquals(calm.getTank(), 0, "its tank is spilt all the same");
            calm.discard();
            griefing.set(true, world.getServer());
            TrichaudronEntity full = spawn(context, new BlockPos(4, 1, 4));
            full.setTank(TrichaudronEntity.TANK_MAX);
            BlockPos at = full.getBlockPos();
            context.assertEquals(full.spill(world), TrichaudronEntity.SPILL_MAX, "27 buckets: 3 sources");
            context.assertTrue(TrichaudronPumping.isSource(world, at), "a source where it died");
            for (BlockPos spot : new BlockPos[]{at, at.north(), at.south(), at.east(), at.west()}) {
                world.setBlockState(spot, Blocks.AIR.getDefaultState());
            }
            TrichaudronEntity low = spawn(context, new BlockPos(1, 1, 6));
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
    public void eachHeadHasItsOwnVentAndTarget(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 1));
        int heads = TrichaudronEntity.HEADS.length;
        for (int head = 0; head < heads; head++) {
            trichaudron.setVent(head, (byte) (head % 3));
            trichaudron.setHeadTarget(head, head == heads - 1 ? pig : null);
        }
        for (int head = 0; head < heads; head++) {
            context.assertEquals(trichaudron.getVent(head), (byte) (head % 3), "head " + head + "'s vent");
            context.assertTrue(trichaudron.getHeadTarget(head) == (head == heads - 1 ? pig : null), "head " + head + "'s target");
        }
        pig.discard();
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itsTankTamingAndSaddleAreSaved(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        ServerPlayerEntity owner = player(context, ItemStack.EMPTY), friend = player(context, ItemStack.EMPTY);
        empty(trichaudron, friend);
        tame(trichaudron, owner);
        trichaudron.inventory.setStack(0, new ItemStack(Items.SADDLE));
        trichaudron.setTank(17);
        NbtCompound nbt = new NbtCompound();
        trichaudron.writeNbt(nbt);
        TrichaudronEntity copy = ModEntities.TRICHAUDRON.create(context.getWorld());
        context.assertTrue(copy != null, "created");
        copy.readNbt(nbt);
        context.assertEquals(copy.getTank(), 17, "17 buckets back");
        context.assertTrue(copy.isTamed() && copy.isOwner(owner), "tamed, its owner back");
        context.assertTrue(copy.isSaddled(), "its saddle back");
        context.assertTrue(copy.isEmptier(friend) && copy.isEmptier(owner), "who emptied it, back");
        TestPlayers.remove(context, owner, friend);
        trichaudron.discard();
        context.complete();
    }

    // ---------------------------------------------------------------- neutral until provoked

    /**
     * A wild one, its AI on, facing east, its tank part full, and a survival player standing {@code fromShell} blocks
     * east of its shell (its hitbox's edge), {@code dz} blocks aside.
     */
    private static ServerPlayerEntity standOff(TestContext context, TrichaudronEntity trichaudron, double fromShell, double dz) {
        trichaudron.setAiDisabled(false);
        trichaudron.setTank(10);
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        double edge = trichaudron.getBoundingBox().maxX;
        player.refreshPositionAndAngles(edge + fromShell + player.getWidth() / 2, trichaudron.getY(), trichaudron.getZ() + dz, 90, 0);
        return player;
    }

    /** Never targeted, never blasted (on fire: a mock player is never ticked, still join-invulnerable). */
    private static void leftAlone(TestContext context, TrichaudronEntity trichaudron, ServerPlayerEntity player) {
        context.assertFalse(trichaudron.getTarget() == player, "never targeted");
        context.assertFalse(player.isOnFire(), "never blasted");
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_neutral", tickLimit = 140)
    public void aPlayerCloseByIsLeftAlone(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        ServerPlayerEntity player = standOff(context, trichaudron, 4, 0);
        context.runAtTick(120, () -> {
            leftAlone(context, trichaudron, player);
            context.assertFalse(trichaudron.hasGrudge(player), "no grudge");
            TestPlayers.remove(context, player);
            trichaudron.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_provoked_hit", tickLimit = 240)
    public void hittingItMakesItFightThatPlayerOnly(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        ServerPlayerEntity hitter = standOff(context, trichaudron, 10, 0);
        ServerPlayerEntity bystander = standOff(context, trichaudron, 8, 3);
        trichaudron.damage(trichaudron.getDamageSources().playerAttack(hitter), 1);
        context.assertTrue(trichaudron.hasGrudge(hitter), "a grudge against the hitter");
        context.assertFalse(trichaudron.hasGrudge(bystander), "none against the one beside him");
        boolean[] done = {false};
        context.runAtEveryTick(() -> {
            if (done[0] || trichaudron.getTarget() != hitter || !hitter.isOnFire()) return;
            done[0] = true;
            leftAlone(context, trichaudron, bystander);
            TestPlayers.remove(context, hitter, bystander);
            trichaudron.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_provoked")
    public void takingItsLavaDoesntProvokeItAStrangerClimbingOnDoes(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        trichaudron.setTank(10);
        ServerPlayerEntity emptier = player(context, new ItemStack(Items.BUCKET));
        emptier.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(trichaudron.getTank() == 9 && !trichaudron.hasGrudge(emptier), "a bucket of its lava taken: no grudge");
        ServerPlayerEntity rider = player(context, ItemStack.EMPTY);
        rider.interact(trichaudron, Hand.MAIN_HAND);
        context.assertTrue(rider.getVehicle() == trichaudron && trichaudron.hasGrudge(rider), "a stranger climbing on: a grudge");
        TestPlayers.remove(context, emptier, rider);
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_provoked_owner", tickLimit = 140)
    public void itsOwnerNeverProvokesIt(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        ServerPlayerEntity player = standOff(context, trichaudron, 10, 0);
        trichaudron.tame(player);
        trichaudron.setTank(10);
        trichaudron.damage(trichaudron.getDamageSources().playerAttack(player), 1);
        context.assertFalse(trichaudron.hasGrudge(player), "its owner hitting it: no grudge");
        context.runAtTick(120, () -> {
            leftAlone(context, trichaudron, player);
            TestPlayers.remove(context, player);
            trichaudron.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_calms", tickLimit = 100)
    public void itCalmsDownOnceTheAngerIsOverAndThePlayerAway(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        ServerPlayerEntity player = standOff(context, trichaudron, TrichaudronEntity.CALM_DISTANCE + 8, 0);
        trichaudron.setTank(0); // a weak puff out of reach: it only frets
        trichaudron.provoke(player, 20);
        context.assertTrue(trichaudron.hasGrudge(player) && trichaudron.getTarget() == player, "provoked: his target");
        context.runAtTick(60, () -> {
            context.assertFalse(trichaudron.hasGrudge(player), "the anger over, him away: no grudge");
            context.assertFalse(trichaudron.getTarget() == player, "and no target");
            TestPlayers.remove(context, player);
            trichaudron.discard();
            context.complete();
        });
    }

    // ---------------------------------------------------------------- the charged, locked shot

    /** A wild one, its AI on, facing east, aiming at a still pig 10 blocks east of its shell. */
    private static PigEntity sittingDuck(TestContext context, TrichaudronEntity trichaudron) {
        trichaudron.setAiDisabled(false);
        trichaudron.setTank(10);
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        pig.setAiDisabled(true);
        pig.refreshPositionAndAngles(trichaudron.getBoundingBox().maxX + 10 + pig.getWidth() / 2, trichaudron.getY(), trichaudron.getZ(), 0, 0);
        trichaudron.setTarget(pig);
        return pig;
    }

    /** The head whose aim has frozen (its vent charging, its target let go), or -1. */
    private static int lockedHead(TrichaudronEntity trichaudron) {
        for (int head = 0; head < TrichaudronEntity.HEADS.length; head++) {
            if (trichaudron.getVent(head) == TrichaudronEntity.VENT_CHARGING && trichaudron.getHeadTarget(head) == null) return head;
        }
        return -1;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_shot_still", tickLimit = 160)
    public void theLockedShotHitsAStillTarget(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        PigEntity pig = sittingDuck(context, trichaudron);
        float health = pig.getHealth();
        boolean[] done = {false};
        context.runAtEveryTick(() -> {
            if (done[0] || pig.getHealth() >= health) return;
            done[0] = true;
            pig.discard();
            trichaudron.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_shot_dodged", tickLimit = 160)
    public void aStepAsideOnceItsAimFreezesDodgesTheShot(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        PigEntity pig = sittingDuck(context, trichaudron);
        float health = pig.getHealth();
        boolean[] stepped = {false}, fired = {false};
        int[] firedAt = {-1};
        context.runAtEveryTick(() -> {
            if (!stepped[0] && lockedHead(trichaudron) >= 0) { // its aim froze: three blocks aside
                stepped[0] = true;
                pig.refreshPositionAndAngles(pig.getX(), pig.getY(), pig.getZ() + 3, 0, 0);
            }
            for (int head = 0; head < TrichaudronEntity.HEADS.length; head++) {
                if (!fired[0] && trichaudron.getVent(head) == TrichaudronEntity.VENT_SPITTING) {
                    fired[0] = true;
                    firedAt[0] = trichaudron.age;
                }
            }
            if (fired[0] && trichaudron.age >= firedAt[0] + 5) {
                context.assertTrue(stepped[0], "it locked its aim before firing");
                context.assertTrue(pig.getHealth() >= health && !pig.isOnFire(), "the shot went where the pig was: missed");
                pig.discard();
                trichaudron.discard();
                fired[0] = false;
                firedAt[0] = Integer.MAX_VALUE;
                context.complete();
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_shot_behind", tickLimit = 400)
    public void aTargetBehindItIsShotOnlyOnceItHasTurned(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        trichaudron.setYaw(90); // facing west, the pig east of it
        trichaudron.setBodyYaw(90);
        trichaudron.setHeadYaw(90);
        for (int head = 0; head < TrichaudronEntity.HEADS.length; head++) trichaudron.restHead(head);
        PigEntity pig = sittingDuck(context, trichaudron);
        context.assertFalse(trichaudron.faces(pig), "the pig is behind it, out of its necks' arc");
        float health = pig.getHealth();
        boolean[] done = {false};
        context.runAtEveryTick(() -> {
            if (done[0]) return;
            if (pig.getHealth() < health) {
                context.assertTrue(trichaudron.faces(pig), "it turned round before it shot");
                done[0] = true;
                pig.discard();
                trichaudron.discard();
                context.complete();
            }
        });
    }

    // ---------------------------------------------------------------- the Nether: prey and piglin riders

    /** Piglins on its rim, their AI off (they would shoot the players themselves). */
    private static void piglins(TestContext context, TrichaudronEntity trichaudron, int count) {
        for (int i = 0; i < count; i++) {
            PiglinEntity piglin = context.spawnEntity(EntityType.PIGLIN, new BlockPos(1, 1, 8));
            piglin.setBaby(false);
            piglin.setAiDisabled(true);
            piglin.startRiding(trichaudron, true);
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_prey", tickLimit = 600)
    public void aWildOneHuntsNetherMobsNotOthers(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        trichaudron.setAiDisabled(false);
        trichaudron.setTank(10);
        double edge = trichaudron.getBoundingBox().maxX;
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        pig.setAiDisabled(true);
        pig.refreshPositionAndAngles(edge + 4, trichaudron.getY(), trichaudron.getZ() - 3, 0, 0);
        MagmaCubeEntity cube = context.spawnEntity(EntityType.MAGMA_CUBE, new BlockPos(1, 1, 8));
        cube.setAiDisabled(true);
        cube.setSize(2, true);
        cube.refreshPositionAndAngles(edge + 10, trichaudron.getY(), trichaudron.getZ(), 0, 0);
        context.assertTrue(trichaudron.findPrey() == cube, "the magma cube is its prey, not the pig");
        boolean[] done = {false};
        context.runAtEveryTick(() -> {
            if (done[0] || trichaudron.getTarget() != cube) return;
            done[0] = true;
            context.assertTrue(pig.getHealth() >= pig.getMaxHealth(), "the pig is left alone");
            for (var e : List.of(pig, cube, trichaudron)) e.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_piglins")
    public void piglinRidersMakeItFasterAndQuickerToShoot(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = spawn(context, new BlockPos(4, 1, 4));
        double base = trichaudron.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        context.assertTrue(Math.abs(base - TrichaudronEntity.SPEED) < 1.0e-6, "no piglin: its own speed");
        for (int n = 1; n <= 3; n++) {
            piglins(context, trichaudron, 1);
            context.assertEquals(trichaudron.piglinRiders(), n, "piglins on");
            double speed = trichaudron.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            context.assertTrue(Math.abs(speed - TrichaudronEntity.SPEED * (1 + TrichaudronEntity.PIGLIN_SPEED[n])) < 1.0e-6, n + " piglins: faster, " + speed);
            context.assertTrue(TrichaudronEntity.PIGLIN_RANGE[n] > TrichaudronEntity.PIGLIN_RANGE[n - 1], n + " piglins: spots farther");
            context.assertTrue(TrichaudronGoals.cooldownFactor(n) < TrichaudronGoals.cooldownFactor(n - 1), n + " piglins: shoots more often");
        }
        context.assertTrue(trichaudron.findPrey() == null, "ridden by piglins: no hunting");
        List<Entity> riders = List.copyOf(trichaudron.getPassengerList());
        riders.forEach(Entity::stopRiding);
        double back = trichaudron.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        context.assertTrue(Math.abs(back - TrichaudronEntity.SPEED) < 1.0e-6, "piglins off: its own speed again");
        riders.forEach(Entity::discard);
        trichaudron.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_piglins_hostile", tickLimit = 300)
    public void ridenByPiglinsItBlastsPlayersNotInGold(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        piglins(context, trichaudron, 2);
        ServerPlayerEntity foe = standOff(context, trichaudron, 10, 0);
        ServerPlayerEntity golden = standOff(context, trichaudron, 9, 3);
        golden.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        context.assertTrue(trichaudron.ridersHostileTo(foe), "its piglins hate him");
        context.assertFalse(trichaudron.ridersHostileTo(golden), "not the one in gold");
        boolean[] done = {false};
        context.runAtEveryTick(() -> {
            if (done[0] || trichaudron.getTarget() != foe || !foe.isOnFire()) return;
            done[0] = true;
            leftAlone(context, trichaudron, golden);
            for (Entity rider : trichaudron.getPassengerList()) {
                context.assertFalse(rider.isOnFire() || ((LivingEntity) rider).getHealth() < ((LivingEntity) rider).getMaxHealth(),
                        "never its own riders");
            }
            List<Entity> riders = List.copyOf(trichaudron.getPassengerList());
            riders.forEach(Entity::discard);
            TestPlayers.remove(context, foe, golden);
            trichaudron.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_piglins_off", tickLimit = 140)
    public void itsPiglinsOffItIsNeutralAgain(TestContext context) {
        strip(context, 22);
        TrichaudronEntity trichaudron = facingEast(context, new BlockPos(1, 1, 8));
        piglins(context, trichaudron, 1);
        ServerPlayerEntity player = standOff(context, trichaudron, 10, 0);
        List<Entity> riders = List.copyOf(trichaudron.getPassengerList());
        riders.forEach(Entity::stopRiding);
        riders.forEach(Entity::discard);
        context.assertFalse(trichaudron.ridersHostileTo(player), "no piglin on: no hostility");
        context.runAtTick(120, () -> {
            leftAlone(context, trichaudron, player);
            TestPlayers.remove(context, player);
            trichaudron.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trichaudron_piglins_stay", tickLimit = 140)
    public void itsPiglinsStayOn(TestContext context) {
        TestBoards.floor(context, 8);
        TrichaudronEntity trichaudron = context.spawnEntity(ModEntities.TRICHAUDRON, new BlockPos(4, 1, 4));
        trichaudron.mountPiglins(context.getWorld(), context.getWorld().getLocalDifficulty(trichaudron.getBlockPos()), 3);
        context.assertEquals(trichaudron.piglinRiders(), 3, "three piglins seated");
        for (Entity rider : trichaudron.getPassengerList()) context.getWorld().spawnEntity(rider);
        context.assertEquals(TrichaudronSpawns.riderCount(0.2f), 1, "one piglin half the time");
        context.assertEquals(TrichaudronSpawns.riderCount(0.6f), 2, "two a third of the time");
        context.assertEquals(TrichaudronSpawns.riderCount(0.9f), 3, "three the rest");
        context.runAtTick(120, () -> {
            context.assertEquals(trichaudron.piglinRiders(), 3, "still on after a while (their AI on)");
            List<Entity> riders = List.copyOf(trichaudron.getPassengerList());
            riders.forEach(Entity::discard);
            trichaudron.discard();
            context.complete();
        });
    }
}
