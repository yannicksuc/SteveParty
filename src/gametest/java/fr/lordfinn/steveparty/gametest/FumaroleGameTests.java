package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleBlast;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleEntity;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumarolePumping;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleRiding;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.util.List;

/**
 * The Fumarole: born at least half full; buckets take lava from its tank and pour some in (lava only); pumping drinks
 * without taking the source; emptied it becomes tamable, each head trusts whoever fed it a magma cream, the three by
 * one player tame it; tamed and saddled it is ridden, the riders' keys added up; an untamed one throws its rider off;
 * it swims in lava; the charged jump; the blast (fire mostly, armour and shields); the spill on death; its save.
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
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, held);
        return player;
    }

    private static void remove(TestContext context, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) context.getWorld().getServer().getPlayerManager().remove(player);
    }

    /** Facing +x (yaw -90), its centre head at rest. */
    private static FumaroleEntity facingEast(TestContext context, BlockPos at) {
        FumaroleEntity fumarole = spawn(context, at);
        fumarole.setYaw(-90);
        fumarole.setBodyYaw(-90);
        fumarole.setHeadYaw(-90);
        for (int head = 0; head < FumaroleEntity.HEADS.length; head++) fumarole.restHead(head);
        return fumarole;
    }

    private static int floorY(TestContext context) {
        return context.getAbsolutePos(new BlockPos(0, 1, 0)).getY();
    }

    /** Empties its tank with a bucket in this player's hands: tamable. */
    private static void empty(FumaroleEntity fumarole, ServerPlayerEntity player) {
        fumarole.setTank(1);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
        player.interact(fumarole, Hand.MAIN_HAND);
    }

    private static void tame(FumaroleEntity fumarole, ServerPlayerEntity player) {
        empty(fumarole, player);
        ItemStack cream = new ItemStack(Items.MAGMA_CREAM, 8);
        for (int head = 0; head < FumaroleEntity.HEADS.length; head++) fumarole.feedHead(player, head, cream);
    }

    // ---------------------------------------------------------------- spawn, tank

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void spawnsAtLeastHalfFullWithItsStats(TestContext context) {
        floor(context, 8);
        ServerWorld world = context.getWorld();
        for (int i = 0; i < 5; i++) {
            FumaroleEntity fumarole = ModEntities.FUMAROLE.create(world);
            context.assertTrue(fumarole != null, "created");
            BlockPos at = context.getAbsolutePos(new BlockPos(4, 1, 4));
            fumarole.refreshPositionAndAngles(at, 0, 0);
            fumarole.initialize(world, world.getLocalDifficulty(at), SpawnReason.NATURAL, null);
            context.assertTrue(fumarole.getTank() >= FumaroleEntity.TANK_MAX / 2, "at least half full: " + fumarole.getTank());
            context.assertEquals(fumarole.getMaxHealth(), (float) FumaroleEntity.MAX_HEALTH, "80 HP");
            context.assertTrue(fumarole.isFireImmune(), "fire immune");
            context.assertFalse(fumarole.isTamed() || fumarole.isTamable(), "wild");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bucketsTakeAndPourOnlyLava(TestContext context) {
        floor(context, 8);
        FumaroleEntity fumarole = spawn(context, new BlockPos(4, 1, 4));
        fumarole.setTank(5);
        ServerPlayerEntity player = player(context, new ItemStack(Items.BUCKET));
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.LAVA_BUCKET), "an empty bucket comes back full");
        context.assertEquals(fumarole.getTank(), 4, "one bucket taken");
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.BUCKET), "a lava bucket is poured in");
        context.assertEquals(fumarole.getTank(), 5, "one bucket poured");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.WATER_BUCKET), "water does not go in");
        context.assertEquals(fumarole.getTank(), 5, "lava only");
        fumarole.setTank(FumaroleEntity.TANK_MAX);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(player.getMainHandStack().isOf(Items.LAVA_BUCKET), "a full tank takes no more");
        remove(context, player);
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_pump")
    public void pumpingLeavesTheSource(TestContext context) {
        floor(context, 16);
        FumaroleEntity fumarole = spawn(context, new BlockPos(2, 1, 7));
        fumarole.setTank(0);
        pool(context, new BlockPos(9, 0, 7), 1, 1);
        BlockPos source = context.getAbsolutePos(new BlockPos(9, 0, 7));
        context.assertTrue(fumarole.canReach(source), "within the nozzle's reach");
        context.assertTrue(fumarole.pump(source), "drinks from a lone source");
        context.assertTrue(FumarolePumping.isSource(context.getWorld(), source), "the source is still there");
        context.assertEquals(fumarole.getTank(), 1, "one bucket in the tank");
        context.assertFalse(fumarole.pump(source), "not twice within 3 s");
        FumarolePumping pace = new FumarolePumping();
        for (int i = 0; i < FumarolePumping.MAX_PER_MINUTE; i++) pace.record(i * FumarolePumping.MIN_GAP);
        long last = (FumarolePumping.MAX_PER_MINUTE - 1) * (long) FumarolePumping.MIN_GAP;
        context.assertFalse(pace.rateAllows(last + FumarolePumping.MIN_GAP), "at most 6 a minute");
        context.assertTrue(pace.rateAllows(1200), "a minute later it may again");
        context.setBlockState(new BlockPos(9, 0, 7), Blocks.STONE);
        fumarole.discard();
        context.complete();
    }

    // ---------------------------------------------------------------- taming

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void emptyingItMakesItTamable(TestContext context) {
        floor(context, 8);
        FumaroleEntity fumarole = spawn(context, new BlockPos(4, 1, 4));
        ServerPlayerEntity player = player(context, new ItemStack(Items.BUCKET));
        fumarole.setTank(2);
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertFalse(fumarole.isTamable(), "not yet: a bucket left");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertEquals(fumarole.getTank(), 0, "emptied");
        context.assertTrue(fumarole.isTamable(), "emptied: tamable");
        context.assertFalse(fumarole.isTamed(), "not tamed yet");
        remove(context, player);
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void eachHeadTrustsWhoFedIt(TestContext context) {
        floor(context, 8);
        FumaroleEntity fumarole = spawn(context, new BlockPos(4, 1, 4));
        ServerPlayerEntity a = player(context, ItemStack.EMPTY), b = player(context, ItemStack.EMPTY);
        ItemStack cream = new ItemStack(Items.MAGMA_CREAM, 16);
        fumarole.feedHead(a, 1, cream);
        context.assertTrue(fumarole.trusts(1, a), "head 1 trusts A");
        context.assertFalse(fumarole.trusts(0, a) || fumarole.trusts(2, a), "the others don't");
        context.assertFalse(fumarole.mayShoot(1, a), "head 1 never shoots A");
        context.assertTrue(fumarole.mayShoot(0, a), "head 0 still may");
        context.assertTrue(fumarole.mayShoot(1, b), "head 1 may shoot B");
        fumarole.feedHead(a, 0, cream);
        fumarole.feedHead(a, 2, cream);
        context.assertFalse(fumarole.isTamed(), "trusted by all, but not tamable (full tank): not tamed");
        // tamable, three heads shared between two players: not tamed
        FumaroleEntity other = spawn(context, new BlockPos(2, 1, 2));
        empty(other, a);
        other.feedHead(a, 0, cream);
        other.feedHead(a, 1, cream);
        other.feedHead(b, 2, cream);
        context.assertFalse(other.isTamed(), "two players: not tamed");
        other.feedHead(a, 2, cream);
        context.assertTrue(other.isTamed() && other.isOwner(a), "the three by A: tamed, A its owner");
        context.assertEquals(cream.getCount(), 16 - 7, "a cream a feed");
        remove(context, a, b);
        fumarole.discard();
        other.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tamedAndSaddledItIsRidden(TestContext context) {
        floor(context, 8);
        FumaroleEntity fumarole = spawn(context, new BlockPos(4, 1, 4));
        ServerPlayerEntity owner = player(context, ItemStack.EMPTY), friend = player(context, ItemStack.EMPTY);
        tame(fumarole, owner);
        context.assertTrue(fumarole.isTamed(), "tamed");
        owner.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        owner.interact(fumarole, Hand.MAIN_HAND);
        context.assertFalse(owner.hasVehicle(), "no saddle: no ride (its saddle slot opens)");
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SADDLE));
        owner.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(fumarole.isSaddled(), "saddled");
        owner.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(owner.getVehicle() == fumarole, "the owner rides it");
        friend.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(friend.getVehicle() == fumarole, "a second rider");
        context.assertTrue(fumarole.riderOf(0) == owner && fumarole.riderOf(1) == friend, "the centre head, then the left");
        context.assertTrue(fumarole.isSteered(), "steered");
        remove(context, owner, friend);
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void anUntamedOneThrowsItsRiderOff(TestContext context) {
        floor(context, 8);
        FumaroleEntity fumarole = spawn(context, new BlockPos(4, 1, 4));
        ServerPlayerEntity player = player(context, ItemStack.EMPTY);
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(player.getVehicle() == fumarole, "it lets him climb on");
        fumarole.sprayOff(player);
        context.assertFalse(player.hasVehicle(), "sprayed off");
        context.assertTrue(player.getVelocity().y >= FumaroleEntity.THROW_UP - 0.01, "thrown up: " + player.getVelocity());
        context.assertTrue(player.isOnFire(), "a little fire");
        // fire resistance: no fire; a shield toward the head: no throw at all
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200));
        player.extinguish();
        player.interact(fumarole, Hand.MAIN_HAND);
        fumarole.sprayOff(player);
        context.assertFalse(player.isOnFire(), "fire resistance: no fire");
        // and on its own, a few seconds later
        player.setVelocity(Vec3d.ZERO);
        player.interact(fumarole, Hand.MAIN_HAND);
        context.assertTrue(player.getVehicle() == fumarole, "on again");
        context.waitAndRun(FumaroleEntity.THROW_MIN + FumaroleEntity.THROW_SPREAD + 5, () -> {
            context.assertFalse(player.hasVehicle(), "thrown off within a few seconds");
            context.assertFalse(fumarole.isTamed(), "and still wild");
            remove(context, player);
            fumarole.discard();
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
        FumaroleRiding.Steer cancel = FumaroleRiding.combine(List.of(a, b), e -> false);
        context.assertTrue(cancel.forward() == 0 && cancel.turn() == 0, "opposite keys cancel");
        b.forwardSpeed = 1;
        c.forwardSpeed = 1;
        b.sidewaysSpeed = 1;
        c.sidewaysSpeed = 1;
        FumaroleRiding.Steer three = FumaroleRiding.combine(List.of(a, b, c), e -> false);
        context.assertTrue(three.forward() == 3 && three.turn() == 3, "agreeing keys add up");
        float one = FumaroleRiding.speedFor(1), all = FumaroleRiding.speedFor(3);
        context.assertTrue(one > FumaroleEntity.SPEED * 1.8, "one rider: much faster than its wild crawl");
        context.assertTrue(all > one * 1.8, "three riders: much faster than one (" + all + " vs " + one + ")");
        context.assertTrue(FumaroleRiding.turnFor(3) == 3 * FumaroleRiding.turnFor(1), "three turning: three times as fast");
        context.assertTrue(FumaroleRiding.speedFor(-1) < one, "backward slower");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_ride")
    public void aRiderDrivesIt(TestContext context) {
        strip(context, 30);
        FumaroleEntity fumarole = facingEast(context, new BlockPos(2, 1, 8));
        fumarole.setAiDisabled(false);
        ServerPlayerEntity owner = player(context, ItemStack.EMPTY);
        tame(fumarole, owner);
        fumarole.inventory.setStack(0, new ItemStack(Items.SADDLE));
        owner.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        owner.startRiding(fumarole);
        double startX = fumarole.getX();
        // its moves, driven here tick by tick (the server would call them): one rider's forward key
        owner.forwardSpeed = 1;
        for (int tick = 0; tick < 30; tick++) fumarole.travel(Vec3d.ZERO);
        context.assertTrue(fumarole.getX() - startX > 3, "driven forward: " + (fumarole.getX() - startX));
        remove(context, owner);
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_swim", tickLimit = 140)
    public void itSwimsInLava(TestContext context) {
        // a pool 8 x 8, 6 deep, walled
        for (int x = -1; x <= 8; x++) for (int z = -1; z <= 8; z++) for (int y = 0; y <= 6; y++) {
            boolean wall = x < 0 || x > 7 || z < 0 || z > 7 || y == 0;
            context.setBlockState(new BlockPos(x, y, z), wall ? Blocks.STONE.getDefaultState() : y <= 5 ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState());
        }
        FumaroleEntity fumarole = spawn(context, new BlockPos(4, 1, 4));
        double bottom = fumarole.getY();
        context.waitAndRun(100, () -> {
            context.assertTrue(fumarole.getY() > bottom + 2, "floated up: " + (fumarole.getY() - bottom));
            context.assertTrue(fumarole.isInLava(), "still in the lava, swimming");
            fumarole.discard();
            for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) for (int y = 1; y <= 5; y++) context.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_jump", tickLimit = 60)
    public void theChargedJumpLeapsHighAndCostsLava(TestContext context) {
        strip(context, 20, 32);
        FumaroleEntity fumarole = facingEast(context, new BlockPos(2, 1, 8));
        fumarole.setAiDisabled(false); // a mob without AI doesn't move at all
        fumarole.setTank(5);
        context.waitAndRun(4, () -> {
            context.assertFalse(fumarole.thrusterJump(FumaroleRiding.CHARGE_MIN - 1), "too short a charge: nothing");
            double start = fumarole.getY();
            context.assertTrue(fumarole.thrusterJump(FumaroleRiding.CHARGE_MAX), "a full charge leaps");
            context.assertEquals(fumarole.getTank(), 3, "two buckets for a full charge");
            context.assertTrue(fumarole.getVelocity().y >= FumaroleRiding.JUMP_UP_MAX - 0.01, "up fast: " + fumarole.getVelocity());
            context.waitAndRun(10, () -> {
                context.assertTrue(fumarole.getY() > start + 6, "high up: " + (fumarole.getY() - start));
                // a ledge to climb: a wall 5 high in front, its top clear
                fumarole.discard();
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_climb")
    public void itsHeadsFindALedgeToClimb(TestContext context) {
        strip(context, 20, 20);
        // a wall 5 high, 3 blocks ahead of its shell
        for (int y = 1; y <= 5; y++) for (int z = 3; z < 14; z++) context.setBlockState(new BlockPos(7, y, z), Blocks.STONE);
        FumaroleEntity fumarole = facingEast(context, new BlockPos(2, 1, 8));
        Vec3d ledge = FumaroleRiding.findLedge(context.getWorld(), fumarole.getPos(), fumarole.getYaw(),
                FumaroleEntity.WIDTH / 2, FumaroleEntity.HEIGHT, fumarole.getY());
        context.assertTrue(ledge != null, "a ledge found");
        context.assertTrue(Math.abs(ledge.y - (floorY(context) + 5)) < 0.01, "on top of the wall: " + ledge);
        for (int y = 6; y <= 7; y++) for (int z = 3; z < 14; z++) context.setBlockState(new BlockPos(7, y, z), Blocks.STONE);
        for (int y = 8; y <= 15; y++) for (int z = 3; z < 14; z++) context.setBlockState(new BlockPos(7, y, z), Blocks.STONE);
        context.assertTrue(FumaroleRiding.findLedge(context.getWorld(), fumarole.getPos(), fumarole.getYaw(),
                FumaroleEntity.WIDTH / 2, FumaroleEntity.HEIGHT, fumarole.getY()) == null, "a wall too high: none");
        for (int y = 1; y <= 15; y++) for (int z = 3; z < 14; z++) context.setBlockState(new BlockPos(7, y, z), Blocks.AIR);
        fumarole.discard();
        context.complete();
    }

    // ---------------------------------------------------------------- the blast

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_blast", tickLimit = 40)
    public void theBlastBurnsShovesAndCostsABucket(TestContext context) {
        strip(context, 22);
        FumaroleEntity fumarole = facingEast(context, new BlockPos(1, 1, 8));
        fumarole.setTank(5);
        Vec3d nozzle = fumarole.nozzle(0);
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        pig.setAiDisabled(true);
        pig.refreshPositionAndAngles(nozzle.x + 6, floorY(context), nozzle.z, 0, 0);
        float before = pig.getHealth();
        List<LivingEntity> hurt = fumarole.blast(0, pig);
        context.assertTrue(hurt.contains(pig), "the pig is hit");
        float taken = before - pig.getHealth();
        context.assertTrue(Math.abs(taken - (FumaroleBlast.FIRE_DAMAGE + FumaroleBlast.PHYSICAL_DAMAGE)) < 0.6f, "fire and physical: " + taken);
        context.assertTrue(pig.isOnFire(), "set on fire");
        context.assertTrue(pig.getVelocity().x > 1.5 && pig.getVelocity().y > 0.3, "shoved hard: " + pig.getVelocity());
        context.assertEquals(fumarole.getTank(), 4, "a bucket spent");
        // a fire-proof mob: only the physical part, no fire
        PigEntity proof = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        proof.setAiDisabled(true);
        proof.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200));
        proof.refreshPositionAndAngles(nozzle.x + 9, floorY(context), nozzle.z + 4, 0, 0);
        float proofBefore = proof.getHealth();
        fumarole.restHead(0);
        fumarole.blast(0, proof);
        context.assertTrue(Math.abs(proofBefore - proof.getHealth() - FumaroleBlast.PHYSICAL_DAMAGE) < 0.6f, "fire resistance: physical only");
        context.assertFalse(proof.isOnFire(), "and no fire");
        // empty: a weak puff, no fire, free
        PigEntity near = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        near.setAiDisabled(true);
        near.refreshPositionAndAngles(nozzle.x + 3, floorY(context), nozzle.z - 4, 0, 0);
        fumarole.setTank(0);
        fumarole.restHead(0);
        float nearBefore = near.getHealth();
        fumarole.blast(0, near);
        float puff = nearBefore - near.getHealth();
        context.assertTrue(puff > 0 && puff <= FumaroleBlast.PUFF_DAMAGE + 0.01f, "a weak puff: " + puff);
        context.assertFalse(near.isOnFire(), "the puff burns nothing");
        context.assertEquals(fumarole.getTank(), 0, "the puff is free");
        pig.discard();
        proof.discard();
        near.discard();
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_shield", tickLimit = 40)
    public void aShieldStopsTheBlast(TestContext context) {
        strip(context, 24);
        FumaroleEntity fumarole = facingEast(context, new BlockPos(1, 1, 8));
        fumarole.setTank(5);
        Vec3d nozzle = fumarole.nozzle(0);
        ServerPlayerEntity player = player(context, ItemStack.EMPTY);
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
        player.refreshPositionAndAngles(nozzle.x + 5, floorY(context), nozzle.z, 90, 0); // facing -x, the jet
        player.setHeadYaw(90);
        player.setCurrentHand(Hand.OFF_HAND);
        PigEntity behind = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 8));
        behind.setAiDisabled(true);
        behind.refreshPositionAndAngles(nozzle.x + 9, floorY(context), nozzle.z, 0, 0);
        float health = player.getHealth(), pigHealth = behind.getHealth();
        context.assertTrue(FumaroleBlast.shields(player, new Vec3d(1, 0, 0)), "the shield is up toward the jet");
        fumarole.blast(0, player);
        context.assertEquals(player.getHealth(), health, "no damage");
        context.assertFalse(player.isOnFire(), "no fire");
        context.assertTrue(player.getVelocity().horizontalLength() < 0.2, "no shove: " + player.getVelocity());
        context.assertEquals(behind.getHealth(), pigHealth, "the steam stops on the shield");
        remove(context, player);
        behind.discard();
        fumarole.discard();
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

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fumarole_armour", tickLimit = 40)
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
            context.assertTrue(FumaroleBlast.armourStops(d3), "full diamond Protection III");
            context.assertTrue(FumaroleBlast.armourStops(n4), "full netherite Protection IV");
            context.assertFalse(FumaroleBlast.armourStops(d2), "diamond Protection II: not enough");
            context.assertFalse(FumaroleBlast.armourStops(i4), "iron: not enough armour");
            // fire resistance and heavy armour: nothing taken, but shoved all the same
            FumaroleEntity fumarole = facingEast(context, new BlockPos(1, 1, 8));
            fumarole.setTank(5);
            Vec3d nozzle = fumarole.nozzle(0);
            d3.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200));
            d3.refreshPositionAndAngles(nozzle.x + 5, floorY(context), nozzle.z, -90, 0); // back to the jet
            float health = d3.getHealth();
            context.assertEquals(FumaroleBlast.damageOn(d3), 0f, "no damage at all");
            fumarole.blast(0, d3);
            context.assertEquals(d3.getHealth(), health, "unhurt");
            context.assertTrue(d3.getVelocity().x > 1.0, "but shoved: " + d3.getVelocity());
            context.assertEquals(FumaroleBlast.damageOn(i4), FumaroleBlast.FIRE_DAMAGE + FumaroleBlast.PHYSICAL_DAMAGE, "iron: all of it");
            remove(context, d3, d2, n4, i4);
            fumarole.discard();
            context.complete();
        });
    }

    // ---------------------------------------------------------------- death, heads, save

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
            context.assertEquals(full.spill(world), FumaroleEntity.SPILL_MAX, "27 buckets: 3 sources");
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
    public void eachHeadHasItsOwnVentAndTarget(TestContext context) {
        floor(context, 8);
        FumaroleEntity fumarole = spawn(context, new BlockPos(4, 1, 4));
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 1));
        int heads = FumaroleEntity.HEADS.length;
        for (int head = 0; head < heads; head++) {
            fumarole.setVent(head, (byte) (head % 3));
            fumarole.setHeadTarget(head, head == heads - 1 ? pig : null);
        }
        for (int head = 0; head < heads; head++) {
            context.assertEquals(fumarole.getVent(head), (byte) (head % 3), "head " + head + "'s vent");
            context.assertTrue(fumarole.getHeadTarget(head) == (head == heads - 1 ? pig : null), "head " + head + "'s target");
        }
        pig.discard();
        fumarole.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itsTankTamingAndSaddleAreSaved(TestContext context) {
        floor(context, 8);
        FumaroleEntity fumarole = spawn(context, new BlockPos(4, 1, 4));
        ServerPlayerEntity owner = player(context, ItemStack.EMPTY), friend = player(context, ItemStack.EMPTY);
        fumarole.feedHead(friend, 2, new ItemStack(Items.MAGMA_CREAM));
        tame(fumarole, owner);
        fumarole.inventory.setStack(0, new ItemStack(Items.SADDLE));
        fumarole.setTank(17);
        NbtCompound nbt = new NbtCompound();
        fumarole.writeNbt(nbt);
        FumaroleEntity copy = ModEntities.FUMAROLE.create(context.getWorld());
        context.assertTrue(copy != null, "created");
        copy.readNbt(nbt);
        context.assertEquals(copy.getTank(), 17, "17 buckets back");
        context.assertTrue(copy.isTamed() && copy.isOwner(owner), "tamed, its owner back");
        context.assertTrue(copy.isSaddled(), "its saddle back");
        context.assertTrue(copy.trusts(2, friend) && !copy.trusts(0, friend), "its heads' trust back");
        remove(context, owner, friend);
        fumarole.discard();
        context.complete();
    }
}
