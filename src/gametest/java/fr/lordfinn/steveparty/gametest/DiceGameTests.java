package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Dice: /kill and the void remove them, players hitting them still stop / restart / explode them. */
public class DiceGameTests implements FabricGameTest {
    private static final BlockPos DICE_POS = new BlockPos(2, 3, 2);

    private static DiceEntity spawnDice(TestContext context) {
        DiceEntity dice = context.spawnEntity(ModEntities.DICE_ENTITY, DICE_POS);
        dice.setNoGravity(true);
        return dice;
    }

    private static ServerPlayerEntity playerNextTo(TestContext context, boolean sneaking) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        Vec3d pos = context.getAbsolute(new Vec3d(DICE_POS.getX() + 0.5, DICE_POS.getY(), DICE_POS.getZ() - 1.5));
        player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        player.setSneaking(sneaking);
        return player;
    }

    private static void disconnect(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void killCommandRemovesDice(TestContext context) {
        DiceEntity dice = spawnDice(context);
        ServerWorld world = context.getWorld();
        // What /kill does
        dice.kill(world);
        context.assertTrue(dice.isRemoved(), "/kill removes the dice");

        DiceEntity other = spawnDice(context);
        // /damage ... minecraft:generic_kill, or anything that bypasses invulnerability
        other.damage(world, world.getDamageSources().genericKill(), Float.MAX_VALUE);
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
        dice.damage(world, world.getDamageSources().generic(), 1000F);
        dice.damage(world, world.getDamageSources().inFire(), 1000F);
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
            dice.damage(world, world.getDamageSources().playerAttack(player), 1F);
            context.assertFalse(dice.isRolling(), "a hit stops the dice");
            context.assertFalse(dice.isRemoved(), "a hit does not remove the dice");
            int value = dice.getRollValue();
            context.assertTrue(value >= DiceEntity.MIN && value <= DiceEntity.MAX, "rolled a value: " + value);

            dice.damage(world, world.getDamageSources().playerAttack(player), 1F);
            context.assertTrue(dice.isRolling(), "a second hit restarts the dice");
            context.assertFalse(dice.isRemoved(), "still there");

            ServerPlayerEntity sneaking = playerNextTo(context, true);
            try {
                dice.damage(world, world.getDamageSources().playerAttack(sneaking), 1F);
                context.assertTrue(dice.isRemoved(), "a sneaking hit explodes the dice");
            } finally {
                disconnect(context, sneaking);
            }
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }
    // ---------------------------------------------------------------- a thrown die rolls until it is hit

    /** The dice {@code player} threw (found by their owner). */
    private static java.util.List<DiceEntity> diceOf(TestContext context, ServerPlayerEntity player) {
        return context.getWorld().getEntitiesByClass(DiceEntity.class, player.getBoundingBox().expand(24),
                dice -> dice.getOwner().map(owner -> owner.equals(player.getUuid())).orElse(false));
    }

    /** A player standing in the test, holding {@code stack}, looking at {@code pitch} (-90: straight up). */
    private static ServerPlayerEntity thrower(TestContext context, net.minecraft.item.ItemStack stack, float pitch, boolean sneaking) {
        context.setBlockState(new BlockPos(4, 1, 4), net.minecraft.block.Blocks.STONE);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        Vec3d pos = context.getAbsolute(new Vec3d(4.5, 2, 4.5));
        player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, pitch);
        player.setSneaking(sneaking);
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, stack);
        context.addFinalTask(() -> {
            diceOf(context, player).forEach(DiceEntity::discard);
            disconnect(context, player);
        });
        return player;
    }

    /** A plain die thrown with the item keeps rolling, nothing asked, until a player hits it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "dice_throws")
    public void aThrownPlainDieRollsUntilItIsHit(TestContext context) {
        ServerPlayerEntity player = thrower(context, new net.minecraft.item.ItemStack(fr.lordfinn.steveparty.items.ModItems.DEFAULT_DICE), -20, false);
        player.getMainHandStack().use(context.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND);
        context.assertEquals(diceOf(context, player).size(), 1, "one die thrown");
        DiceEntity dice = diceOf(context, player).getFirst();
        context.waitAndRun(120, () -> {
            context.assertTrue(!dice.isRemoved() && dice.isRolling() && !dice.isRollFinished(), "still rolling 6 seconds later, without a hit");
            context.assertEquals(dice.sequence().phase(), fr.lordfinn.steveparty.dice.DiceRollSequence.Phase.ROLLING, "its roll goes on");
            context.assertTrue(fr.lordfinn.steveparty.dice.DicePrompts.pending(player) == null, "nothing is asked for a plain die");
            dice.damage(context.getWorld(), context.getWorld().getDamageSources().playerAttack(player), 1F);
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
        ServerPlayerEntity player = thrower(context, new net.minecraft.item.ItemStack(fr.lordfinn.steveparty.items.ModItems.DEFAULT_DICE), -90, true);
        player.getMainHandStack().use(context.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND);
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
        ServerPlayerEntity player = thrower(context, new net.minecraft.item.ItemStack(fr.lordfinn.steveparty.items.ModItems.DOUBLE_DICE), -90, true);
        player.getMainHandStack().use(context.getWorld(), player, net.minecraft.util.Hand.MAIN_HAND);
        java.util.List<DiceEntity> thrown = diceOf(context, player);
        context.assertEquals(thrown.size(), 2, "the two dice of a Double Dice");
        ServerWorld world = context.getWorld();
        for (DiceEntity dice : thrown) dice.damage(world, world.getDamageSources().playerAttack(player), 1F); // sneaking: would burst them
        context.assertTrue(thrown.stream().noneMatch(DiceEntity::isRemoved), "a sneaking click right after the throw does not burst the dice");
        player.setSneaking(false);
        for (DiceEntity dice : thrown) dice.damage(world, world.getDamageSources().playerAttack(player), 1F);
        context.assertTrue(thrown.stream().allMatch(dice -> dice.isRolling() && !dice.isRollFinished()), "nor does a click stop them");
        context.assertTrue(thrown.getFirst().isInThrowGrace(player), "the thrower's hits don't count yet");

        // Another player may hit at once; the thrower too once the moment is over
        ServerPlayerEntity other = playerNextTo(context, false);
        try {
            context.assertTrue(!thrown.getFirst().isInThrowGrace(other), "someone else is not held back");
        } finally {
            disconnect(context, other);
        }
        context.waitAndRun(DiceEntity.THROW_GRACE_TICKS + 2, () -> {
            context.assertTrue(thrown.stream().allMatch(DiceEntity::isRolling), "still rolling meanwhile");
            thrown.getLast().damage(world, world.getDamageSources().playerAttack(player), 1F);
            context.assertTrue(thrown.stream().noneMatch(DiceEntity::isRolling) && thrown.getFirst().isRollFinished(), "then the thrower's hit stops them");
            context.complete();
        });
    }
}
