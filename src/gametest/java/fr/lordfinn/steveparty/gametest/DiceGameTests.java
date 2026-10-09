package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.dice.DiceRollSequence;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.gametest.kit.TestWait;
import fr.lordfinn.steveparty.items.ModItems;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Dice: /kill and the void remove them, players hitting them still stop / restart / explode them. */
public class DiceGameTests implements FabricGameTest {
    private static final BlockPos DICE_POS = new BlockPos(2, 3, 2);

    private static DiceEntity spawnDice(TestContext context) {
        DiceEntity dice = context.spawnEntity(ModEntities.DICE_ENTITY, DICE_POS);
        dice.setNoGravity(true);
        return dice;
    }

    private static ServerPlayerEntity playerNextTo(TestContext context, boolean sneaking) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        TestPlayers.place(context, player, new Vec3d(DICE_POS.getX() + 0.5, DICE_POS.getY(), DICE_POS.getZ() - 1.5), 0, 0);
        player.setSneaking(sneaking);
        return player;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void killCommandRemovesDice(TestContext context) {
        DiceEntity dice = spawnDice(context);
        ServerWorld world = context.getWorld();
        // What /kill does
        dice.kill();
        context.assertTrue(dice.isRemoved(), "/kill removes the dice");

        DiceEntity other = spawnDice(context);
        // /damage ... minecraft:generic_kill, or anything that bypasses invulnerability
        other.damage(world.getDamageSources().genericKill(), Float.MAX_VALUE);
        context.assertTrue(other.isRemoved(), "a generic kill removes the dice");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void voidRemovesDice(TestContext context) {
        DiceEntity dice = spawnDice(context);
        ServerWorld world = context.getWorld();
        dice.refreshPositionAndAngles(dice.getX(), world.getBottomY() - 400, dice.getZ(), 0, 0);
        // Times out if the dice survives the void
        context.runAtEveryTick(() -> {
            if (dice.isRemoved()) context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void diceIsSyncedEveryTick(TestContext context) {
        // A moving dice synced every 3 ticks (+3 ticks of client easing) was drawn blocks behind its position
        context.assertTrue(ModEntities.DICE_ENTITY.getTrackTickInterval() == 1, "dice tracking interval");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void genericDamageDoesNotRemoveDice(TestContext context) {
        DiceEntity dice = spawnDice(context);
        ServerWorld world = context.getWorld();
        dice.damage(world.getDamageSources().generic(), 1000F);
        dice.damage(world.getDamageSources().inFire(), 1000F);
        context.assertFalse(dice.isRemoved(), "only kills that bypass invulnerability remove the dice");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void playerHitsStillStopAndExplodeDice(TestContext context) {
        DiceEntity dice = spawnDice(context);
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = playerNextTo(context, false);
        try {
            context.assertTrue(dice.isRolling(), "a new dice rolls");
            dice.damage(world.getDamageSources().playerAttack(player), 1F);
            context.assertFalse(dice.isRolling(), "a hit stops the dice");
            context.assertFalse(dice.isRemoved(), "a hit does not remove the dice");
            int value = dice.getRollValue();
            context.assertTrue(value >= DiceEntity.MIN && value <= DiceEntity.MAX, "rolled a value: " + value);

            dice.damage(world.getDamageSources().playerAttack(player), 1F);
            context.assertTrue(dice.isRolling(), "a second hit restarts the dice");
            context.assertFalse(dice.isRemoved(), "still there");

            ServerPlayerEntity sneaking = playerNextTo(context, true);
            try {
                dice.damage(world.getDamageSources().playerAttack(sneaking), 1F);
                context.assertTrue(dice.isRemoved(), "a sneaking hit explodes the dice");
            } finally {
                TestPlayers.remove(context, sneaking);
            }
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }
    // ---------------------------------------------------------------- a thrown die rolls until it is hit

    /** The dice {@code player} threw (found by their owner). */
    private static List<DiceEntity> diceOf(TestContext context, ServerPlayerEntity player) {
        return context.getWorld().getEntitiesByClass(DiceEntity.class, player.getBoundingBox().expand(24),
                dice -> dice.getOwner().map(owner -> owner.equals(player.getUuid())).orElse(false));
    }

    /** A player standing in the test, holding {@code stack}, looking at {@code pitch} (-90: straight up). */
    private static ServerPlayerEntity thrower(TestContext context, ItemStack stack, float pitch, boolean sneaking) {
        context.setBlockState(new BlockPos(4, 1, 4), Blocks.STONE);
        ServerPlayerEntity player = TestPlayers.mock(context);
        Vec3d pos = context.getAbsolute(new Vec3d(4.5, 2, 4.5));
        player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, pitch);
        player.setSneaking(sneaking);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        context.addFinalTask(() -> {
            diceOf(context, player).forEach(DiceEntity::discard);
            TestPlayers.remove(context, player);
        });
        return player;
    }

    /** A plain die thrown with the item keeps rolling, nothing asked, until a player hits it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "dice_throws")
    public void aThrownPlainDieRollsUntilItIsHit(TestContext context) {
        ServerPlayerEntity player = thrower(context, new ItemStack(ModItems.DEFAULT_DICE), -20, false);
        player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertEquals(diceOf(context, player).size(), 1, "one die thrown");
        DiceEntity dice = diceOf(context, player).getFirst();
        context.waitAndRun(120, () -> {
            context.assertTrue(!dice.isRemoved() && dice.isRolling() && !dice.isRollFinished(), "still rolling 6 seconds later, without a hit");
            context.assertEquals(dice.sequence().phase(), DiceRollSequence.Phase.ROLLING, "its roll goes on");
            context.assertTrue(DicePrompts.pending(player) == null, "nothing is asked for a plain die");
            dice.damage(context.getWorld().getDamageSources().playerAttack(player), 1F);
            context.assertTrue(!dice.isRolling() && dice.isRollFinished(), "a hit stops it");
            context.complete();
        });
    }

    /**
     * Thrown sneaking, straight up: the die comes back over its thrower (a sneaking throw aims at a player) and stays
     * there, touching them; it keeps rolling, a jump into it included: only a hit stops a die.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "dice_throws")
    public void aDieFallingBackOnItsSneakingThrowerKeepsRolling(TestContext context) {
        ServerPlayerEntity player = thrower(context, new ItemStack(ModItems.DEFAULT_DICE), -90, true);
        player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
        DiceEntity dice = diceOf(context, player).getFirst();
        context.assertTrue(dice.getTarget().map(target -> target.equals(player.getUuid())).orElse(false), "a sneaking throw aims at its thrower");
        context.waitAndRun(60, () -> {
            context.assertTrue(dice.squaredDistanceTo(player.getX(), player.getY() + player.getHeight(), player.getZ()) < 4, "the die came back over its thrower");
            context.assertTrue(dice.isRolling() && !dice.isRollFinished(), "it still rolls");
            // The thrower moves up into it, as if jumping: still no hit
            player.refreshPositionAndAngles(dice.getX(), dice.getY() - 1.2, dice.getZ(), 0, -90);
            context.waitAndRun(40, () -> {
                context.assertTrue(!dice.isRemoved() && dice.isRolling() && !dice.isRollFinished(), "touching the die is not hitting it");
                context.complete();
            });
        });
    }

    /** The click of the throw is not a hit: the thrower's hits are ignored for a moment after the throw. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "dice_throws")
    public void theThrowersClickAtThrowTimeDoesNotStopTheDie(TestContext context) {
        ServerPlayerEntity player = thrower(context, new ItemStack(ModItems.DOUBLE_DICE), -90, true);
        player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
        List<DiceEntity> thrown = diceOf(context, player);
        context.assertEquals(thrown.size(), 2, "the two dice of a Double Dice");
        ServerWorld world = context.getWorld();
        for (DiceEntity dice : thrown) dice.damage(world.getDamageSources().playerAttack(player), 1F); // sneaking: would burst them
        context.assertTrue(thrown.stream().noneMatch(DiceEntity::isRemoved), "a sneaking click right after the throw does not burst the dice");
        player.setSneaking(false);
        for (DiceEntity dice : thrown) dice.damage(world.getDamageSources().playerAttack(player), 1F);
        context.assertTrue(thrown.stream().allMatch(dice -> dice.isRolling() && !dice.isRollFinished()), "nor does a click stop them");
        context.assertTrue(thrown.getFirst().isInThrowGrace(player), "the thrower's hits don't count yet");

        // Another player may hit at once; the thrower too once the moment is over
        ServerPlayerEntity other = playerNextTo(context, false);
        try {
            context.assertTrue(!thrown.getFirst().isInThrowGrace(other), "someone else is not held back");
        } finally {
            TestPlayers.remove(context, other);
        }
        context.waitAndRun(DiceEntity.THROW_GRACE_TICKS + 2, () -> {
            context.assertTrue(thrown.stream().allMatch(DiceEntity::isRolling), "still rolling meanwhile");
            thrown.getLast().damage(world.getDamageSources().playerAttack(player), 1F);
            context.assertTrue(!thrown.getFirst().lead().isRolling() && !thrown.getFirst().isRollFinished(), "then the thrower's hit stops them, one after the other");
            TestWait.when(context, () -> thrown.getFirst().isRollFinished(),
                    DiceRollSequence.REVEAL_STEP_TICKS + DiceRollSequence.REVEAL_TOTAL_TICKS + 4, "the throw is revealed", () -> {
                context.assertTrue(thrown.stream().noneMatch(DiceEntity::isRolling), "both stopped");
                // The finished dice are unchanged: the next hit makes them go away at once
                thrown.getFirst().damage(world.getDamageSources().playerAttack(player), 1F);
                context.assertTrue(thrown.stream().allMatch(DiceEntity::isRemoved), "a hit on the finished dice makes them go away");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- how a thrown die flies

    /** Thrown at the floor, the die does not dig into it: it starts in the air and is tossed upward. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "dice_throws")
    public void aDieThrownAtTheFloorGoesUpInsteadOfIntoIt(TestContext context) {
        ServerPlayerEntity player = thrower(context, new ItemStack(ModItems.DEFAULT_DICE), 90, false);
        player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
        DiceEntity dice = diceOf(context, player).getFirst();
        context.assertTrue(context.getWorld().isSpaceEmpty(dice), "the die appears in the air, not in the floor");
        context.assertTrue(dice.getVelocity().y > 0, "it is tossed upward");
        context.waitAndRun(60, () -> {
            context.assertTrue(context.getWorld().isSpaceEmpty(dice), "it never went into the floor");
            context.assertTrue(dice.getY() >= player.getY(), "it floats above the floor its thrower stands on");
            context.complete();
        });
    }

    /**
     * Thrown at the floor toward someone (sneaking: its thrower), the die bounces softly: it never shoots far up or
     * away, and it lands like a die, with no fall (no fall distance, so no fall sound nor crash).
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "dice_throws")
    public void aDieThrownAtTheFloorBouncesSoftly(TestContext context) {
        ServerPlayerEntity player = thrower(context, new ItemStack(ModItems.DEFAULT_DICE), 60, true);
        player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
        DiceEntity dice = diceOf(context, player).getFirst();
        context.assertTrue(dice.getVelocity().y > 0 && dice.getVelocity().y < 0.3, "a soft upward bounce, not a strong one");
        double[] highest = {dice.getY()};
        for (int t = 1; t <= 40; t++) {
            context.runAtTick(t, () -> {
                highest[0] = Math.max(highest[0], dice.getY());
                context.assertTrue(dice.fallDistance == 0, "a die never builds up a fall");
            });
        }
        context.runAtTick(41, () -> {
            context.assertTrue(highest[0] < player.getEyeY() + 2.5, "it never shoots far up (highest " + highest[0] + ")");
            context.assertTrue(dice.squaredDistanceTo(player) < 16, "nor far away");
            context.complete();
        });
    }

    /** With no one to float to, a die thrown straight up rises a few blocks only, then comes back down a little. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140, batchId = "dice_throws")
    public void anUntargetedDieThrownUpComesBackDown(TestContext context) {
        ServerPlayerEntity player = thrower(context, new ItemStack(ModItems.DEFAULT_DICE), -90, false);
        player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
        DiceEntity dice = diceOf(context, player).getFirst();
        context.assertTrue(dice.getTarget().isEmpty(), "no mob around: no target");
        double[] highest = {dice.getY()};
        context.runAtEveryTick(() -> highest[0] = Math.max(highest[0], dice.getY()));
        context.waitAndRun(100, () -> {
            double eyes = player.getEyeY();
            context.assertTrue(highest[0] - eyes < 7, "it rose a few blocks only: " + (highest[0] - eyes));
            context.assertTrue(dice.getY() < highest[0] - 2, "then came back down");
            context.assertTrue(dice.getY() - eyes < 2.5, "it floats within reach: " + (dice.getY() - eyes));
            context.complete();
        });
    }

    // ---------------------------------------------------------------- the burst of a die

    private static PigEntity pigNextTo(TestContext context, DiceEntity dice) {
        PigEntity pig = context.spawnEntity(EntityType.PIG, DICE_POS.add(2, 0, 0));
        pig.setAiDisabled(true);
        pig.setNoGravity(true);
        return pig;
    }

    /** A die going away bursts into a firework that hurts no one. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aPlainDieBurstHurtsNoOne(TestContext context) {
        DiceEntity dice = spawnDice(context);
        PigEntity pig = pigNextTo(context, dice);
        dice.discard();
        context.waitAndRun(10, () -> {
            context.assertTrue(pig.isAlive() && pig.getHealth() == pig.getMaxHealth(), "the pig next to it is unhurt");
            context.complete();
        });
    }

    /** Firecracker module: the burst hurts (three times a firework) and throws back. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aFirecrackerDieBurstHurtsAndKnocksBack(TestContext context) {
        DiceEntity dice = spawnDice(context);
        ItemStack die = new ItemStack(ModItems.DEFAULT_DICE);
        DiceModules.set(die, Map.of(DiceModules.FIRECRACKER, 1));
        dice.setItemReference(die);
        PigEntity pig = pigNextTo(context, dice);
        double before = pig.getX();
        dice.discard();
        context.assertTrue(!pig.isAlive() || pig.getHealth() < pig.getMaxHealth(), "the pig is hurt");
        context.assertTrue(!pig.isAlive() || pig.getVelocity().x > 0.1, "and thrown back, away from the die");
        context.complete();
    }

    /**
     * The whole Firecracker chain, as played: the module crafted, set on a die at the crafting table, the die thrown
     * by a survival player (the die goes back to them), stopped, then hit away: its burst hurts and throws back.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120, batchId = "dice_throws")
    public void aCraftedFirecrackerDieThrownInSurvivalBlastsWhenItGoesAway(TestContext context) {
        ServerWorld world = context.getWorld();
        var recipes = world.getServer().getRecipeManager();
        ItemStack f = new ItemStack(ModItems.PINK_STAR_FRAGMENT);
        CraftingRecipeInput moduleGrid = CraftingRecipeInput.create(3, 3, List.of(
                f, new ItemStack(Items.TNT), f,
                f, new ItemStack(ModItems.BLANK_DICE_MODULE), f,
                f, f, f));
        ItemStack module = recipes.getFirstMatch(RecipeType.CRAFTING, moduleGrid, world)
                .map(e -> e.value().craft(moduleGrid, world.getRegistryManager())).orElseThrow();
        context.assertTrue(module.isOf(DiceModules.FIRECRACKER.item()), "the Firecracker module is crafted, got " + module);
        CraftingRecipeInput dieGrid = CraftingRecipeInput.create(2, 1, List.of(
                new ItemStack(ModItems.DEFAULT_DICE), module));
        ItemStack die = recipes.getFirstMatch(RecipeType.CRAFTING, dieGrid, world)
                .map(e -> e.value().craft(dieGrid, world.getRegistryManager())).orElseThrow();
        context.assertTrue(DiceModules.has(die, DiceModules.FIRECRACKER), "and set on a die");

        ServerPlayerEntity player = thrower(context, die, 0, false);
        player.changeGameMode(GameMode.SURVIVAL);
        PigEntity pig = context.spawnEntity(EntityType.PIG, new Vec3d(4.5, 2, 7.5));
        pig.setAiDisabled(true);
        pig.setNoGravity(true);
        player.getMainHandStack().use(world, player, Hand.MAIN_HAND);
        DiceEntity dice = diceOf(context, player).getFirst();
        context.assertTrue(player.getMainHandStack().isEmpty(), "a survival throw spends the die from the hand");
        context.waitAndRun(30, () -> {
            dice.damage(world.getDamageSources().playerAttack(player), 1F);
            context.assertTrue(dice.isRollFinished(), "the hit stops the die");
            dice.damage(world.getDamageSources().playerAttack(player), 1F);
            context.assertTrue(dice.isRemoved(), "the next hit makes it go away");
            context.assertTrue(!pig.isAlive() || pig.getHealth() < pig.getMaxHealth(), "its burst hurts the pig under it");
            context.assertTrue(player.getVelocity().horizontalLength() > 0.05, "and throws back its thrower nearby");
            boolean back = false;
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                back |= stack.isOf(ModItems.DEFAULT_DICE)
                        && DiceModules.has(stack, DiceModules.FIRECRACKER);
            }
            context.assertTrue(back, "the die, still a Firecracker, is back with its thrower");
            pig.discard();
            context.complete();
        });
    }
}
