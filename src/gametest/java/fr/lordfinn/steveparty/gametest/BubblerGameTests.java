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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.ZombieEntity;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.service.BoardActors;
import net.minecraft.entity.passive.CowEntity;
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
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * The Bubbler and its bubbles: how high a bubble rises (two blocks a level of its redstone signal, 30 without), puffs
 * carrying further up, hovering then the puff, a
 * rider's punch out, hits (never popping with Silk Touch), arrows popping and snowballs pushing, bubbles merging (and
 * several riders of one Bubbler ending in one bubble), the riders' keys shared, item bubbles, a bubble left still
 * popping (never while it travels, never in its Bubbler's column), walls, the ground and mobs popping it (never in its
 * column, never at its launch), its riders shooting out through it, Slow Falling once out; the Bubbled effect (a
 * rider's projectile bubbling what it hits for as long as its bubble lasts, a timed one's end popping the bubble, an
 * effect bubble left still popping and ending the effect, a bubbled player steering, bosses immune).
 */
public class BubblerGameTests implements SteveGameTest {
    /** Bubbles rise 30 blocks and puffs carry them a dozen blocks east. */
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
        context.assertTrue(BubbleRiding.launchHeight(0) == 30, "no signal: 30 blocks");
        for (int power = 1; power <= 15; power++) {
            context.assertTrue(BubbleRiding.launchHeight(power) == 2 * power, "signal " + power + ": " + 2 * power + " blocks");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPuffUpCarriesFurther(TestContext context) {
        Vec3d up = BubbleRiding.puff(BubbleRiding.CHARGE_MAX, new Vec3d(0, 1, 0), 1);
        Vec3d down = BubbleRiding.puff(BubbleRiding.CHARGE_MAX, new Vec3d(0, -1, 0), 1);
        context.assertTrue(Math.abs(up.y - BubbleRiding.PUFF_MAX * BubbleRiding.PUFF_LIFT) < 1.0E-6, "up: lifted " + up.y);
        context.assertTrue(Math.abs(down.y + BubbleRiding.PUFF_MAX) < 1.0E-6, "down: as strong as ever " + down.y);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbler_unpowered", tickLimit = 260)
    public void unpoweredABubbleRisesThirtyBlocksAndHovers(TestContext context) {
        clear(context, 1, 1, 1, 5, 36, 5);
        BlockPos abs = bubbler(context, new BlockPos(3, 1, 3));
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(3, 1, 3));
        context.waitAndRun(170, () -> {
            context.assertTrue(pig.getVehicle() instanceof BubbleEntity, "the pig is in a bubble");
            BubbleEntity bubble = (BubbleEntity) pig.getVehicle();
            double over = bubble.getY() - (abs.getY() + 1);
            context.assertTrue(Math.abs(over - 30) < 0.35, "30 blocks over the Bubbler: " + over);
            context.assertTrue(bubble.isHovering() && Math.abs(bubble.getVelocity().y) < 0.08, "hovering: " + bubble.getVelocity());
            pig.discard();
            bubble.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbler_signal", tickLimit = 260)
    public void aSignalOfFourteenSendsItTwentyEightBlocksUp(TestContext context) {
        clear(context, 1, 1, 1, 5, 34, 5);
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
            context.waitAndRun(170, () -> {
                context.assertTrue(pig.getVehicle() instanceof BubbleEntity, "the pig is in a bubble");
                BubbleEntity bubble = (BubbleEntity) pig.getVehicle();
                double over = bubble.getY() - (abs.getY() + 1);
                context.assertTrue(Math.abs(over - 28) < 0.35, "28 blocks over the Bubbler: " + over);
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

    // ---------------------------------------------------------------- strain

    /** The strain a bubble has at each of {@code puffs} puffs {@code apart} ticks apart, and the chance each one pops it. */
    private static float[] chancesOfPuffs(int puffs, int apart) {
        float[] chances = new float[puffs];
        float strain = 0;
        for (int i = 0; i < puffs; i++) {
            chances[i] = BubbleRiding.strainPopChance(strain);
            strain = Math.min(1, strain + BubbleRiding.STRAIN_PER_PUFF);
            strain = Math.max(0, strain - apart * BubbleRiding.STRAIN_DECAY);
        }
        return chances;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onePuffNeverPopsCalmPuffsAreSafeSpamGetsRisky(TestContext context) {
        context.assertTrue(BubbleRiding.strainPopChance(0) == 0, "a single puff never pops it");
        context.assertTrue(BubbleRiding.strainPopChance(BubbleRiding.STRAIN_PER_PUFF - 0.05f) < 0.2f, "nor one right after another");
        float[] calm = chancesOfPuffs(10, 40);
        for (float chance : calm) context.assertTrue(chance == 0, "a puff every 2 s: always safe");
        // as fast as the charge and its cooldown allow
        float[] spam = chancesOfPuffs(5, BubbleRiding.CHARGE_MIN + BubbleRiding.PUFF_COOLDOWN);
        context.assertTrue(spam[0] == 0 && spam[1] == 0, "the first two quick puffs are safe: " + spam[1]);
        context.assertTrue(spam[2] > 0.15f, "the third gets risky: " + spam[2]);
        context.assertTrue(spam[3] > spam[2] && spam[4] >= spam[3] && spam[4] > 0.5f, "and riskier: " + spam[3] + ", " + spam[4]);
        // drawn with a seeded random: spam pops bubbles, calm never does
        Random random = Random.create(42);
        int spamPops = 0, calmPops = 0;
        for (int trial = 0; trial < 1000; trial++) {
            for (float chance : spam) if (random.nextFloat() < chance) { spamPops++; break; }
            for (float chance : calm) if (chance > 0 && random.nextFloat() < chance) { calmPops++; break; }
        }
        context.assertTrue(calmPops == 0, "calm: never popped");
        context.assertTrue(spamPops > 700, "five quick puffs: most bubbles pop (" + spamPops + "/1000)");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_strain", tickLimit = 160)
    public void aPuffStrainsTheBubbleTheStrainEasesOff(TestContext context) {
        clear(context, 0, 1, 1, 26, 9, 7);
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, player, new BlockPos(2, 1, 4), 3);
        context.waitAndRun(40, () -> {
            context.assertTrue(bubble.strain() == 0, "calm");
            player.setYaw(-90);
            player.setPitch(0);
            player.setJumping(true);
            context.waitAndRun(BubbleRiding.CHARGE_MIN + 2, () -> player.setJumping(false));
            context.waitAndRun(BubbleRiding.CHARGE_MIN + 4, () -> {
                context.assertFalse(bubble.isRemoved(), "one puff never pops it");
                context.assertTrue(Math.abs(bubble.strain() - BubbleRiding.STRAIN_PER_PUFF) < 0.05f, "strained: " + bubble.strain());
            });
            context.waitAndRun(BubbleRiding.CHARGE_MIN + 4 + 50, () -> {
                context.assertTrue(bubble.strain() == 0, "calm again within a few seconds: " + bubble.strain());
                bubble.setStrain(1);
            });
            context.waitAndRun(BubbleRiding.CHARGE_MIN + 4 + 100, () -> {
                context.assertTrue(Math.abs(bubble.strain() - 0.5f) < 0.02f, "half eased off in 2.5 s: " + bubble.strain());
                TestPlayers.remove(context, player);
                bubble.discard();
                context.complete();
            });
        });
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

    /** Pushes {@code bubble} east, out of its Bubbler's column (about 3.5 blocks, at rest within 50 ticks). */
    private static void outOfColumn(TestContext context, BubbleEntity bubble) {
        bubble.push(new Vec3d(0.25 * bubble.mass(), 0, 0));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_idle", tickLimit = 120)
    public void leftStillFifteenSecondsItPops(TestContext context) {
        clear(context, 0, 1, 0, 14, 7, 6);
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        context.waitAndRun(30, () -> {
            context.assertTrue(bubble.inColumn() && bubble.getStillTicks() == 0, "waiting over its Bubbler: never still");
            outOfColumn(context, bubble);
        });
        context.waitAndRun(80, () -> {
            context.assertFalse(bubble.inColumn(), "out of its column");
            context.assertTrue(bubble.getStillTicks() > 0, "stopped, bobbing: still (" + bubble.getStillTicks() + ")");
            bubble.setStillTicks(BubbleRiding.IDLE_TICKS - 3);
            context.waitAndRun(1, () -> context.assertFalse(bubble.isRemoved(), "still there"));
            context.waitAndRun(6, () -> {
                context.assertTrue(bubble.isRemoved(), "15 s without moving: popped");
                context.assertTrue(rider.hasStatusEffect(StatusEffects.SLOW_FALLING), "its rider falls slowly");
                TestPlayers.remove(context, rider);
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_travel", tickLimit = 80)
    public void travellingItNeverPopsByItself(TestContext context) {
        clear(context, 0, 1, 0, 14, 7, 6);
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        context.waitAndRun(30, () -> {
            bubble.setStillTicks(BubbleRiding.IDLE_TICKS - 3);
            bubble.push(new Vec3d(0.3, 0, 0));
            context.waitAndRun(10, () -> {
                context.assertFalse(bubble.isRemoved(), "pushed, travelling: not popped");
                context.assertTrue(bubble.getStillTicks() < 10, "moving: never still (" + bubble.getStillTicks() + ")");
                TestPlayers.remove(context, rider);
                bubble.discard();
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_item_idle", tickLimit = 200)
    public void anItemBubbleLeftStillPopsToo(TestContext context) {
        clear(context, 1, 1, 1, 12, 36, 5);
        bubbler(context, new BlockPos(3, 1, 3));
        BlockPos abs = context.getAbsolutePos(new BlockPos(3, 1, 3));
        ItemEntity item = new ItemEntity(context.getWorld(), abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, new ItemStack(Items.EMERALD));
        context.getWorld().spawnEntity(item);
        context.waitAndRun(90, () -> {
            List<BubbleEntity> found = bubbles(context);
            context.assertTrue(found.size() == 1, "one item bubble");
            BubbleEntity bubble = found.getFirst();
            context.assertTrue(bubble.inColumn() && bubble.getStillTicks() == 0, "over its Bubbler: never still");
            outOfColumn(context, bubble);
            context.waitAndRun(60, () -> {
                context.assertFalse(bubble.inColumn(), "out of its column");
                bubble.setStillTicks(BubbleRiding.IDLE_TICKS - 1);
            });
            context.waitAndRun(64, () -> {
                context.assertTrue(bubble.isRemoved(), "left still: popped");
                List<ItemEntity> drops = context.getWorld().getEntitiesByClass(ItemEntity.class, bubble.getBoundingBox().expand(3),
                        drop -> drop.getStack().isOf(Items.EMERALD));
                context.assertTrue(drops.size() == 1, "its item drops");
                drops.forEach(Entity::discard);
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- touching

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_wall", tickLimit = 80)
    public void bumpingIntoAWallPopsIt(TestContext context) {
        clear(context, 0, 1, 0, 9, 7, 6);
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        for (int y = 1; y <= 7; y++) for (int z = 1; z <= 5; z++) context.setBlockState(new BlockPos(7, y, z), Blocks.STONE);
        context.waitAndRun(30, () -> {
            context.assertFalse(bubble.isRemoved(), "launched beside nothing: intact");
            bubble.push(new Vec3d(0.4, 0, 0));
            context.waitAndRun(20, () -> {
                context.assertTrue(bubble.isRemoved(), "into the wall: popped");
                context.assertTrue(rider.hasStatusEffect(StatusEffects.SLOW_FALLING), "its rider falls slowly");
                TestPlayers.remove(context, rider);
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_ground", tickLimit = 80)
    public void bumpingIntoTheGroundPopsIt(TestContext context) {
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        clear(context, 0, 1, 0, 12, 7, 6);
        context.waitAndRun(30, () -> {
            bubble.push(new Vec3d(0.35, -0.5, 0)); // down and out of its column
            context.waitAndRun(25, () -> {
                context.assertTrue(bubble.isRemoved(), "down onto the ground: popped");
                TestPlayers.remove(context, rider);
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_cow", tickLimit = 120)
    public void touchingACowPopsIt(TestContext context) {
        clear(context, 0, 1, 0, 14, 7, 6);
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        context.waitAndRun(30, () -> outOfColumn(context, bubble));
        context.waitAndRun(80, () -> {
            context.assertFalse(bubble.isRemoved() || bubble.inColumn(), "out of its column, alone: intact");
            CowEntity cow = context.spawnEntity(EntityType.COW, new BlockPos(3, 1, 3));
            cow.setAiDisabled(true);
            cow.setNoGravity(true);
            Vec3d center = bubble.center();
            cow.refreshPositionAndAngles(center.x + bubble.getSide() / 2, center.y - 0.5, center.z, 0, 0);
            context.waitAndRun(3, () -> {
                context.assertTrue(bubble.isRemoved(), "touching a cow: popped");
                cow.discard();
                TestPlayers.remove(context, rider);
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_launch_crowd", tickLimit = 80)
    public void theLaunchBesideAMobDoesNotPopIt(TestContext context) {
        clear(context, 1, 1, 1, 6, 21, 5);
        bubbler(context, new BlockPos(3, 1, 3));
        CowEntity cow = context.spawnEntity(EntityType.COW, new BlockPos(4, 1, 3));
        cow.setAiDisabled(true);
        cow.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(4, 1, 3)).getX() + 0.3, cow.getY(), cow.getZ(), 0, 0);
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(3, 1, 3));
        context.waitAndRun(40, () -> {
            context.assertTrue(pig.getVehicle() instanceof BubbleEntity, "the pig rose in its bubble beside the cow");
            Entity bubble = pig.getVehicle();
            pig.discard();
            bubble.discard();
            cow.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_shaft", tickLimit = 200)
    public void risingThroughANarrowShaftItNeverPops(TestContext context) {
        clear(context, 1, 1, 1, 5, 21, 5);
        bubbler(context, new BlockPos(3, 1, 3));
        // a shaft one block wide around the Bubbler, 12 blocks high: the bubble rubs its walls all the way up
        for (int y = 1; y <= 12; y++) {
            for (int x = 2; x <= 4; x++) for (int z = 2; z <= 4; z++) {
                if (x != 3 || z != 3) context.setBlockState(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, player, new BlockPos(3, 1, 3), 15);
        context.waitAndRun(150, () -> {
            context.assertFalse(bubble.isRemoved(), "up the shaft, rubbing its walls: intact");
            context.assertTrue(player.getVehicle() == bubble, "its rider still inside");
            context.assertTrue(bubble.inColumn(), "hovering in its column");
            TestPlayers.remove(context, player);
            bubble.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_column_cow", tickLimit = 60)
    public void inItsColumnTouchingACowNeverPopsIt(TestContext context) {
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        context.waitAndRun(30, () -> {
            CowEntity cow = context.spawnEntity(EntityType.COW, new BlockPos(3, 1, 3));
            cow.setAiDisabled(true);
            cow.setNoGravity(true);
            Vec3d center = bubble.center();
            cow.refreshPositionAndAngles(center.x + bubble.getSide() / 2, center.y - 0.5, center.z, 0, 0);
            context.waitAndRun(10, () -> {
                context.assertFalse(bubble.isRemoved(), "waiting in its column: a cow touching it pops nothing");
                cow.discard();
                TestPlayers.remove(context, rider);
                bubble.discard();
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- shooting from inside

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_shoot_out", tickLimit = 80)
    public void aRidersArrowFliesOutAnArrowFromOutsidePopsIt(TestContext context) {
        clear(context, 0, 1, 0, 14, 7, 6);
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        ServerWorld world = context.getWorld();
        context.waitAndRun(30, () -> {
            ArrowEntity out = new ArrowEntity(EntityType.ARROW, world);
            out.setOwner(rider);
            out.setPosition(rider.getEyePos());
            out.setVelocity(2, 0, 0);
            world.spawnEntity(out);
            context.waitAndRun(5, () -> {
                context.assertFalse(bubble.isRemoved(), "its rider's arrow flies out: intact");
                context.assertTrue(out.getX() > bubble.getBoundingBox().maxX, "the arrow is out");
                out.discard();
                Vec3d center = bubble.center();
                ArrowEntity in = new ArrowEntity(EntityType.ARROW, world);
                in.setPosition(bubble.getBoundingBox().minX - 2, center.y, center.z); // in the cleared air west of it
                in.setVelocity(2, 0, 0);
                world.spawnEntity(in);
                context.waitAndRun(6, () -> {
                    context.assertTrue(bubble.isRemoved(), "an arrow from outside pops it");
                    in.discard();
                    TestPlayers.remove(context, rider);
                    context.complete();
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void slowFallingLastsLongEnoughToLand(TestContext context) {
        context.assertTrue(BubbleRiding.slowFallingTicks(0) == BubbleRiding.SLOW_FALLING_MIN, "low: 3 s");
        int high = BubbleRiding.slowFallingTicks(40);
        context.assertTrue(high * BubbleRiding.SLOW_FALL_SPEED >= 40, "40 blocks up: long enough to land (" + high + " ticks)");
        int sky = BubbleRiding.slowFallingTicks(300);
        context.assertTrue(sky * BubbleRiding.SLOW_FALL_SPEED >= 300, "300 blocks up: long enough to land (" + sky + " ticks)");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_fall", tickLimit = 40)
    public void ridingItNoFallBuildsUp(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, player, new BlockPos(3, 1, 3), 2);
        context.waitAndRun(5, () -> {
            player.fallDistance = 60;
            context.waitAndRun(1, () -> {
                context.assertTrue(player.fallDistance == 0, "floating: no fall distance (" + player.fallDistance + ")");
                TestPlayers.remove(context, player);
                bubble.discard();
                context.complete();
            });
        });
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

    // ---------------------------------------------------------------- the Bubbled effect

    /** An arrow its rider shoots from inside {@code bubble} at {@code target}'s chest, 2 blocks a tick. */
    private static ArrowEntity shootFromInside(ServerWorld world, PlayerEntity rider, Entity target) {
        ArrowEntity arrow = new ArrowEntity(EntityType.ARROW, world);
        arrow.setOwner(rider);
        arrow.setPosition(rider.getEyePos());
        Vec3d aim = target.getPos().add(0, target.getHeight() * 0.6, 0).subtract(rider.getEyePos()).normalize().multiply(2);
        arrow.setVelocity(aim.x, aim.y + 0.04, aim.z);
        world.spawnEntity(arrow);
        return arrow;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbled_arrow", tickLimit = 100)
    public void aRidersArrowBubblesTheZombieItHitsAndStillHurtsIt(TestContext context) {
        clear(context, 0, 1, 0, 14, 13, 6);
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        ServerWorld world = context.getWorld();
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(9, 1, 3));
        zombie.setAiDisabled(true);
        context.waitAndRun(30, () -> {
            double startY = zombie.getY();
            shootFromInside(world, rider, zombie);
            context.waitAndRun(8, () -> {
                context.assertFalse(bubble.isRemoved(), "the arrow flew out of its rider's bubble");
                context.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "the arrow still hurts: " + zombie.getHealth());
                context.assertTrue(zombie.hasStatusEffect(ModEffects.BUBBLED), "the zombie is Bubbled");
                context.assertTrue(zombie.getStatusEffect(ModEffects.BUBBLED).isInfinite(), "as long as its bubble lasts");
                context.assertTrue(zombie.getVehicle() instanceof BubbleEntity caught && caught.isFromEffect(), "in a bubble of its own");
            });
            context.waitAndRun(40, () -> {
                context.assertTrue(zombie.getY() > startY + 1.5, "rising: " + (zombie.getY() - startY));
                Entity caught = zombie.getVehicle();
                zombie.discard();
                if (caught != null) caught.discard();
                TestPlayers.remove(context, rider);
                bubble.discard();
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbled_fire", tickLimit = 80)
    public void aFlamingBubbledArrowStillSetsFire(TestContext context) {
        clear(context, 0, 1, 0, 14, 13, 6);
        ServerPlayerEntity rider = TestPlayers.mock(context, GameMode.SURVIVAL);
        BubbleEntity bubble = riding(context, rider, new BlockPos(3, 1, 3), 2);
        ServerWorld world = context.getWorld();
        CowEntity cow = context.spawnEntity(EntityType.COW, new BlockPos(9, 1, 3));
        cow.setAiDisabled(true);
        context.waitAndRun(30, () -> {
            ArrowEntity arrow = shootFromInside(world, rider, cow);
            arrow.setOnFireFor(100);
            context.waitAndRun(8, () -> {
                context.assertTrue(cow.isOnFire(), "the flaming arrow set the cow on fire");
                context.assertTrue(cow.hasStatusEffect(ModEffects.BUBBLED), "and bubbled it");
                Entity caught = cow.getVehicle();
                cow.discard();
                if (caught != null) caught.discard();
                TestPlayers.remove(context, rider);
                bubble.discard();
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbled_end", tickLimit = 100)
    public void theEffectEndingPopsItsBubbleAndItsRiderFallsSlowly(TestContext context) {
        clear(context, 1, 1, 1, 5, 12, 5);
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(3, 1, 3));
        pig.setAiDisabled(true);
        pig.addStatusEffect(new StatusEffectInstance(ModEffects.BUBBLED, 40, 0));
        context.waitAndRun(5, () -> {
            context.assertTrue(pig.getVehicle() instanceof BubbleEntity bubble && bubble.isFromEffect(), "in a bubble");
            context.assertFalse(((BubbleEntity) pig.getVehicle()).inColumn(), "no Bubbler's column");
        });
        context.waitAndRun(30, () -> context.assertTrue(((BubbleEntity) pig.getVehicle()).weakness() > 0, "its last seconds: weakening"));
        context.waitAndRun(50, () -> {
            context.assertFalse(pig.hasVehicle(), "the effect over: popped");
            context.assertTrue(bubbles(context).isEmpty(), "no bubble left");
            context.assertTrue(pig.hasStatusEffect(StatusEffects.SLOW_FALLING), "it falls slowly");
            pig.discard();
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbled_idle", tickLimit = 100)
    public void anEndlessEffectBubbleLeftStillPopsAndEndsTheEffect(TestContext context) {
        clear(context, 1, 1, 1, 5, 12, 5);
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(3, 1, 3));
        pig.setAiDisabled(true);
        pig.addStatusEffect(new StatusEffectInstance(ModEffects.BUBBLED, StatusEffectInstance.INFINITE, 0));
        context.waitAndRun(60, () -> {
            context.assertTrue(pig.getVehicle() instanceof BubbleEntity bubble && bubble.isFromEffect(), "in a bubble");
            BubbleEntity bubble = (BubbleEntity) pig.getVehicle();
            context.assertTrue(bubble.weakness() == 0, "endless: not weakening");
            context.assertTrue(bubble.getStillTicks() > 0, "hovering, bobbing: still (" + bubble.getStillTicks() + ")");
            bubble.setStillTicks(BubbleRiding.IDLE_TICKS - 3);
            context.waitAndRun(1, () -> context.assertTrue(bubble.weakness() > 0, "its last seconds: weakening"));
            context.waitAndRun(5, () -> {
                context.assertTrue(bubble.isRemoved(), "left still: popped");
                context.assertFalse(pig.hasStatusEffect(ModEffects.BUBBLED), "its pop ends the effect");
                context.assertFalse(pig.hasVehicle(), "and it stays out");
                context.assertTrue(pig.hasStatusEffect(StatusEffects.SLOW_FALLING), "it falls slowly");
                pig.discard();
                context.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbled_player", tickLimit = 120)
    public void aBubbledPlayerSteersHisBubble(TestContext context) {
        clear(context, 0, 1, 1, 26, 12, 7);
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        BlockPos abs = context.getAbsolutePos(new BlockPos(2, 1, 4));
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, -90, 0);
        player.addStatusEffect(new StatusEffectInstance(ModEffects.BUBBLED, 200, 0));
        context.waitAndRun(30, () -> {
            context.assertTrue(player.getVehicle() instanceof BubbleEntity, "in a bubble");
            BubbleEntity bubble = (BubbleEntity) player.getVehicle();
            double startX = bubble.getX();
            player.setYaw(-90);
            player.setPitch(0);
            player.setJumping(true);
            context.waitAndRun(BubbleRiding.CHARGE_MAX + 2, () -> {
                player.setJumping(false);
                context.waitAndRun(16, () -> {
                    context.assertTrue(bubble.getX() - startX > 4, "puffed east: " + (bubble.getX() - startX));
                    context.assertTrue(player.getVehicle() == bubble, "still inside");
                    TestPlayers.remove(context, player);
                    bubble.discard();
                    context.complete();
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubbled_boss", tickLimit = 20)
    public void bossesAndBoardActorsAreNeverBubbled(TestContext context) {
        WitherEntity wither = context.spawnEntity(EntityType.WITHER, new BlockPos(3, 1, 3));
        wither.setAiDisabled(true);
        context.assertFalse(wither.addStatusEffect(new StatusEffectInstance(ModEffects.BUBBLED, 100, 0)), "a wither: never");
        context.assertFalse(wither.hasStatusEffect(ModEffects.BUBBLED), "no effect on the wither");
        wither.discard();
        ZombieEntity actor = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        actor.addCommandTag(BoardActors.TAG);
        context.assertFalse(actor.addStatusEffect(new StatusEffectInstance(ModEffects.BUBBLED, 100, 0)), "a board actor: never");
        actor.discard();
        context.complete();
    }
}
