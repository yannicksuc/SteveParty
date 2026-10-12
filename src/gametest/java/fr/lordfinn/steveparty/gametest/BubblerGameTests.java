package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.BubblerBlock;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEntity;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleRiding;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.enums.WireConnection;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * The Bubbler and its bubbles: how high a bubble rises (its redstone signal, 15 without), hovering then the puff, a
 * rider's punch out, hits (never popping with Silk Touch), arrows popping and snowballs pushing, bubbles merging (and
 * several riders of one Bubbler ending in one bubble), the riders' keys shared, item bubbles, the bubble's time, Slow
 * Falling once out.
 */
public class BubblerGameTests implements SteveGameTest {
    /** Bubbles rise 15 blocks and puffs carry them a dozen blocks east. */
    @Override
    public int landAround() {
        return 16;
    }

    /** A Bubbler on a stone floor at {@code at} (relative); its absolute position. */
    private static BlockPos bubbler(TestContext context, BlockPos at) {
        context.setBlockState(at.down(), Blocks.STONE);
        context.setBlockState(at, ModBlocks.BUBBLER);
        return context.getAbsolutePos(at);
    }

    private static List<BubbleEntity> bubbles(TestContext context) {
        BlockPos origin = context.getAbsolutePos(BlockPos.ORIGIN);
        return context.getWorld().getEntitiesByClass(BubbleEntity.class, new Box(origin).expand(48), bubble -> !bubble.isRemoved());
    }

    /** A player standing at {@code at} (relative), enclosed in a bubble rising {@code height} blocks over there. */
    private static BubbleEntity riding(TestContext context, ServerPlayerEntity player, BlockPos at, int height) {
        BlockPos abs = context.getAbsolutePos(at);
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
        BubbleEntity bubble = BubbleEntity.enclose(context.getWorld(), player, abs, height);
        context.assertTrue(bubble != null && player.getVehicle() == bubble, "the player rides his bubble");
        return bubble;
    }

    /**
     * Air from ({@code x0}, {@code y0}, {@code z0}) to ({@code x1}, {@code y1}, {@code z1}) (relative, included): the
     * runner walls each template in, and these bubbles fly beyond it (each test runs alone in its batch).
     */
    private static void clear(TestContext context, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            BlockPos at = new BlockPos(x, y, z);
            if (!context.getBlockState(at).isAir()) context.setBlockState(at, Blocks.AIR);
        }
    }

    private static void discard(List<BubbleEntity> bubbles) {
        for (BubbleEntity bubble : bubbles) bubble.discard();
    }

    // ---------------------------------------------------------------- launch

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theLaunchHeightIsTheSignal(TestContext context) {
        context.assertTrue(BubbleRiding.launchHeight(0) == 15, "no signal: 15 blocks");
        for (int power = 1; power <= 15; power++) {
            context.assertTrue(BubbleRiding.launchHeight(power) == power, "signal " + power + ": " + power + " blocks");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbler_unpowered", tickLimit = 200)
    public void unpoweredABubbleRisesFifteenBlocksAndHovers(TestContext context) {
        clear(context, 1, 1, 1, 5, 21, 5);
        BlockPos abs = bubbler(context, new BlockPos(3, 1, 3));
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(3, 1, 3));
        context.waitAndRun(130, () -> {
            context.assertTrue(pig.getVehicle() instanceof BubbleEntity, "the pig is in a bubble");
            BubbleEntity bubble = (BubbleEntity) pig.getVehicle();
            double over = bubble.getY() - (abs.getY() + 1);
            context.assertTrue(Math.abs(over - 15) < 0.35, "15 blocks over the Bubbler: " + over);
            context.assertTrue(bubble.isHovering() && Math.abs(bubble.getVelocity().y) < 0.08, "hovering: " + bubble.getVelocity());
            pig.discard();
            bubble.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbler_signal", tickLimit = 200)
    public void aSignalOfFourteenSendsItFourteenBlocksUp(TestContext context) {
        clear(context, 1, 1, 1, 5, 21, 5);
        BlockPos abs = bubbler(context, new BlockPos(3, 1, 3));
        // a redstone block, then two wires: 15, 14 into the Bubbler
        for (int x = 0; x < 3; x++) context.setBlockState(new BlockPos(x, 0, 3), Blocks.STONE);
        BlockState line = Blocks.REDSTONE_WIRE.getDefaultState().with(RedstoneWireBlock.WIRE_CONNECTION_WEST, WireConnection.SIDE)
                .with(RedstoneWireBlock.WIRE_CONNECTION_EAST, WireConnection.SIDE);
        context.setBlockState(new BlockPos(2, 1, 3), line);
        context.setBlockState(new BlockPos(1, 1, 3), line);
        context.setBlockState(new BlockPos(0, 1, 3), Blocks.REDSTONE_BLOCK);
        context.waitAndRun(2, () -> {
            int power = context.getWorld().getBlockState(abs).get(BubblerBlock.POWER);
            context.assertTrue(power == 14, "the Bubbler gets 14: " + power);
            PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(3, 1, 3));
            context.waitAndRun(130, () -> {
                context.assertTrue(pig.getVehicle() instanceof BubbleEntity, "the pig is in a bubble");
                BubbleEntity bubble = (BubbleEntity) pig.getVehicle();
                double over = bubble.getY() - (abs.getY() + 1);
                context.assertTrue(Math.abs(over - 14) < 0.35, "14 blocks over the Bubbler: " + over);
                pig.discard();
                bubble.discard();
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbler_sneak")
    public void aSneakingPlayerWalksOverIt(TestContext context) {
        bubbler(context, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        player.setSneaking(true);
        context.assertFalse(BubbleEntity.canEnclose(player), "sneaking: no bubble");
        player.setSneaking(false);
        context.assertTrue(BubbleEntity.canEnclose(player), "walking: a bubble");
        TestPlayers.remove(context, player);
        context.complete();
    }

    // ---------------------------------------------------------------- steering

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_puff", tickLimit = 160)
    public void itHoversThenAPuffPropelsItWhereTheRiderLooks(TestContext context) {
        clear(context, 0, 1, 1, 26, 9, 7);
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, player, new BlockPos(2, 1, 4), 3);
        BlockPos abs = context.getAbsolutePos(new BlockPos(2, 1, 4));
        context.waitAndRun(60, () -> {
            context.assertTrue(bubble.isHovering() && Math.abs(bubble.getY() - (abs.getY() + 4)) < 0.35,
                    "hovering 3 blocks up: " + (bubble.getY() - abs.getY() - 1));
            double startX = bubble.getX();
            player.setYaw(-90); // east, level
            player.setPitch(0);
            player.setJumping(true);
            context.waitAndRun(BubbleRiding.CHARGE_MAX + 2, () -> {
                context.assertTrue(bubble.chargeOf(player) == BubbleRiding.CHARGE_MAX, "charged: " + bubble.chargeOf(player));
                context.assertTrue(Math.abs(bubble.getX() - startX) < 0.3, "still while charging");
                player.setJumping(false);
                context.waitAndRun(16, () -> {
                    context.assertFalse(bubble.isHovering(), "puffed: it floats freely");
                    context.assertTrue(bubble.getX() - startX > 5, "carried east: " + (bubble.getX() - startX));
                    context.assertTrue(Math.abs(bubble.getZ() - (abs.getZ() + 0.5)) < 0.5, "straight east");
                    context.assertTrue(player.getVehicle() == bubble, "still inside");
                    TestPlayers.remove(context, player);
                    bubble.discard();
                    context.complete();
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyRidersKeysCountAveraged(TestContext context) {
        PlayerEntity a = context.createMockPlayer(GameMode.SURVIVAL), b = context.createMockPlayer(GameMode.SURVIVAL);
        a.setYaw(-90);
        b.setYaw(-90);
        a.forwardSpeed = 1;
        b.forwardSpeed = 1;
        Vec3d alone = BubbleRiding.drift(List.of(a));
        Vec3d agreeing = BubbleRiding.drift(List.of(a, b));
        context.assertTrue(alone.x > 0 && Math.abs(alone.z) < 1.0E-6, "forward, facing east: east " + alone);
        context.assertTrue(agreeing.distanceTo(alone) < 1.0E-6, "two agreeing: as fast as one " + agreeing);
        b.forwardSpeed = -1;
        context.assertTrue(BubbleRiding.drift(List.of(a, b)).length() < 1.0E-6, "opposite keys cancel");
        b.forwardSpeed = 0;
        context.assertTrue(BubbleRiding.drift(List.of(a, b)).distanceTo(alone.multiply(0.5)) < 1.0E-6, "one idle rider halves it");
        // a puff, shared by every rider: a bubble of two goes half as far
        Vec3d east = new Vec3d(1, 0, 0);
        double one = BubbleRiding.puff(BubbleRiding.CHARGE_MAX, east, 1).x, two = BubbleRiding.puff(BubbleRiding.CHARGE_MAX, east, 2).x;
        context.assertTrue(Math.abs(two - one / 2) < 1.0E-6, "a puff pushes a bubble of two half as far");
        context.assertTrue(BubbleRiding.puffStrength(BubbleRiding.CHARGE_MIN - 1) == 0, "too short a charge: no puff");
        context.complete();
    }

    // ---------------------------------------------------------------- popping

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_punch")
    public void aRidersPunchPopsItAndHeFallsSlowly(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, player, new BlockPos(3, 1, 3), 6);
        context.waitAndRun(20, () -> {
            player.attack(bubble);
            context.assertTrue(bubble.isRemoved(), "popped by its rider's punch");
            context.assertFalse(player.hasVehicle(), "the rider is out");
            context.assertTrue(player.hasStatusEffect(StatusEffects.SLOW_FALLING), "he falls slowly");
            context.assertTrue(player.getStatusEffect(StatusEffects.SLOW_FALLING).getDuration() >= BubbleRiding.SLOW_FALLING_MIN - 1,
                    "at least 3 s");
            TestPlayers.remove(context, player);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_silk")
    public void silkTouchHitsPushItButNeverPopIt(TestContext context) {
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        ServerWorld world = context.getWorld();
        PlayerEntity hitter = context.createMockPlayer(GameMode.SURVIVAL);
        BlockPos abs = context.getAbsolutePos(new BlockPos(0, 1, 3));
        hitter.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, -90, 0);
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.addEnchantment(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        hitter.setStackInHand(Hand.MAIN_HAND, pickaxe);
        for (int i = 0; i < 60; i++) bubble.damage(world.getDamageSources().playerAttack(hitter), 7);
        context.assertFalse(bubble.isRemoved(), "60 Silk Touch hits: never popped");
        context.assertTrue(bubble.getVelocity().x > 0.1, "pushed away from the hitter: " + bubble.getVelocity());
        context.assertFalse(bubble.isHovering(), "pushed: it floats freely");
        // a bare hand: a hit sooner or later pops it (a 1 in 4 chance each)
        hitter.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        for (int i = 0; i < 60 && !bubble.isRemoved(); i++) bubble.damage(world.getDamageSources().playerAttack(hitter), 1);
        context.assertTrue(bubble.isRemoved(), "bare hands pop it");
        context.assertTrue(rider.hasStatusEffect(StatusEffects.SLOW_FALLING), "its rider falls slowly");
        TestPlayers.remove(context, rider);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_arrow")
    public void anArrowPopsItASnowballPushesIt(TestContext context) {
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        ServerWorld world = context.getWorld();
        SnowballEntity snowball = new SnowballEntity(EntityType.SNOWBALL, world);
        snowball.setVelocity(0, 0, 1.5);
        context.assertTrue(bubble.damage(world.getDamageSources().thrown(snowball, null), 0), "a snowball hits it");
        context.assertFalse(bubble.isRemoved(), "a snowball never pops it");
        context.assertTrue(bubble.getVelocity().z > 0.3 && Math.abs(bubble.getVelocity().x) < 1.0E-6, "pushed the snowball's way: " + bubble.getVelocity());
        ArrowEntity arrow = new ArrowEntity(EntityType.ARROW, world);
        bubble.damage(world.getDamageSources().arrow(arrow, null), 2);
        context.assertTrue(bubble.isRemoved(), "an arrow pops it");
        context.assertFalse(rider.hasVehicle(), "its rider is out");
        TestPlayers.remove(context, rider);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_snowball_flight", tickLimit = 80)
    public void aThrownSnowballPushesItAway(TestContext context) {
        clear(context, 0, 1, 0, 14, 7, 6);
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(6, 1, 3), 1);
        context.waitAndRun(30, () -> {
            Vec3d center = bubble.center();
            double startX = bubble.getX();
            SnowballEntity snowball = new SnowballEntity(EntityType.SNOWBALL, context.getWorld());
            snowball.setPosition(center.x - 4, center.y, center.z);
            snowball.setVelocity(1.5, 0, 0);
            snowball.setNoGravity(true);
            context.getWorld().spawnEntity(snowball);
            context.waitAndRun(10, () -> {
                context.assertFalse(bubble.isRemoved(), "a snowball never pops it");
                context.assertTrue(bubble.getX() - startX > 0.5, "pushed east: " + (bubble.getX() - startX));
                TestPlayers.remove(context, rider);
                bubble.discard();
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_time", tickLimit = 40)
    public void itPopsByItselfInTime(TestContext context) {
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        bubble.setLife(BubbleRiding.LIFETIME - 3);
        context.waitAndRun(1, () -> context.assertFalse(bubble.isRemoved(), "still there"));
        context.waitAndRun(6, () -> {
            context.assertTrue(bubble.isRemoved(), "its time up: popped");
            context.assertTrue(rider.hasStatusEffect(StatusEffects.SLOW_FALLING), "its rider falls slowly");
            TestPlayers.remove(context, rider);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void slowFallingLastsLongEnoughToLand(TestContext context) {
        context.assertTrue(BubbleRiding.slowFallingTicks(0) == BubbleRiding.SLOW_FALLING_MIN, "low: 3 s");
        int high = BubbleRiding.slowFallingTicks(40);
        context.assertTrue(high * BubbleRiding.SLOW_FALL_SPEED >= 40, "40 blocks up: long enough to land (" + high + " ticks)");
        context.complete();
    }

    // ---------------------------------------------------------------- merging

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_merge", tickLimit = 40)
    public void twoOccupiedBubblesTouchingMergeIntoABiggerOne(TestContext context) {
        ServerPlayerEntity a = TestPlayers.mock(context, GameMode.SURVIVAL), b = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity first = riding(context, a, new BlockPos(3, 1, 3), 4);
        BubbleEntity second = riding(context, b, new BlockPos(4, 1, 3), 4);
        float single = first.getSide();
        context.waitAndRun(3, () -> {
            List<BubbleEntity> left = bubbles(context);
            context.assertTrue(left.size() == 1, "one bubble left: " + left.size());
            BubbleEntity merged = left.getFirst();
            context.assertTrue(a.getVehicle() == merged && b.getVehicle() == merged, "both ride it");
            context.assertTrue(merged.getPassengerList().size() == 2, "two riders");
            context.assertTrue(Math.abs(merged.getSide() - BubbleRiding.side(1.8f, 2)) < 1.0E-3 && merged.getSide() > single,
                    "bigger: " + merged.getSide() + " vs " + single);
            context.assertFalse(a.hasStatusEffect(StatusEffects.SLOW_FALLING), "merging pops nothing");
            context.assertTrue(first.isRemoved() != second.isRemoved(), "one took the other in");
            TestPlayers.remove(context, a, b);
            discard(left);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbler_crowd", tickLimit = 80)
    public void severalMobsOnOneBubblerEndInOneBigBubble(TestContext context) {
        bubbler(context, new BlockPos(3, 1, 3));
        PigEntity first = context.spawnEntity(EntityType.PIG, new BlockPos(3, 1, 3));
        PigEntity second = context.spawnEntity(EntityType.PIG, new BlockPos(3, 1, 3));
        PigEntity third = context.spawnEntity(EntityType.PIG, new BlockPos(3, 1, 3));
        context.waitAndRun(20, () -> {
            Entity bubble = first.getVehicle();
            context.assertTrue(bubble instanceof BubbleEntity, "in a bubble");
            context.assertTrue(second.getVehicle() == bubble && third.getVehicle() == bubble, "all three in the same one");
            context.assertTrue(bubbles(context).size() == 1, "one bubble");
            context.assertTrue(((BubbleEntity) bubble).getSide() > BubbleRiding.side(0.9f, 1) * 1.4f, "a big one");
            first.discard();
            second.discard();
            third.discard();
            bubble.discard();
            context.complete();
        });
    }

    // ---------------------------------------------------------------- items

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_item", tickLimit = 80)
    public void aDroppedItemFloatsInABubbleAndDropsWhenItPops(TestContext context) {
        bubbler(context, new BlockPos(3, 1, 3));
        BlockPos abs = context.getAbsolutePos(new BlockPos(3, 1, 3));
        ItemEntity item = new ItemEntity(context.getWorld(), abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, new ItemStack(Items.DIAMOND, 3));
        context.getWorld().spawnEntity(item);
        context.waitAndRun(20, () -> {
            context.assertTrue(item.isRemoved(), "taken into a bubble");
            List<BubbleEntity> found = bubbles(context);
            context.assertTrue(found.size() == 1, "one bubble");
            BubbleEntity bubble = found.getFirst();
            context.assertTrue(bubble.getItem().isOf(Items.DIAMOND) && bubble.getItem().getCount() == 3, "holding the diamonds");
            context.assertTrue(bubble.getY() > abs.getY() + 2, "rising");
            ServerWorld world = context.getWorld();
            SnowballEntity snowball = new SnowballEntity(EntityType.SNOWBALL, world);
            snowball.setVelocity(1, 0, 0);
            bubble.damage(world.getDamageSources().thrown(snowball, null), 0);
            context.assertFalse(bubble.isRemoved(), "pushed, not popped");
            bubble.pop();
            List<ItemEntity> drops = world.getEntitiesByClass(ItemEntity.class, bubble.getBoundingBox().expand(2), drop -> drop.getStack().isOf(Items.DIAMOND));
            context.assertTrue(drops.size() == 1 && drops.getFirst().getStack().getCount() == 3, "the diamonds drop, all 3");
            drops.forEach(Entity::discard);
            context.complete();
        });
    }
}
