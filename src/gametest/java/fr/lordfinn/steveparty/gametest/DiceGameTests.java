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
}
