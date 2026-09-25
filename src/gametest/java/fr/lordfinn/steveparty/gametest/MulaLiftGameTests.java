package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaLift;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Carried by Mulas at night (MulaLift): the lift rule, only at night, the effects that move the player, letting go and
 * the dawn giving slow falling, and no damage to anyone. The night tests set the time: each in its own batch.
 */
public class MulaLiftGameTests implements FabricGameTest {

    private static List<MulaEntity> leashed(TestContext context, ServerPlayerEntity player, int... hungers) {
        List<MulaEntity> mulas = new ArrayList<>();
        for (int i = 0; i < hungers.length; i++) {
            MulaEntity m = context.spawnEntity(ModEntities.MULA_ENTITY, new BlockPos(1 + i, 5, 3));
            m.setHunger(hungers[i]);
            m.attachLeash(player, true);
            mulas.add(m);
        }
        return mulas;
    }

    private static ServerPlayerEntity player(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        BlockPos at = context.getAbsolutePos(new BlockPos(2, 2, 2));
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        return player;
    }

    private static int hunger(double fullness) {
        return (int) Math.round(fullness * MulaEntity.MAX_HUNGER);
    }

    /** 1 at 70% lifts, 1 at 50% doesn't, 2 at 40% lift, 3 at 0% lift, 2 at 0% don't. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void liftCombinations(TestContext context) {
        context.assertTrue(MulaLift.lifts(MulaLift.liftOf(0.7)), "one Mula 70% full lifts");
        context.assertTrue(!MulaLift.lifts(MulaLift.liftOf(0.5)), "one Mula 50% full doesn't");
        context.assertTrue(MulaLift.lifts(2 * MulaLift.liftOf(0.4)), "two Mulas 40% full lift");
        context.assertTrue(MulaLift.lifts(3 * MulaLift.liftOf(0.0)), "three empty Mulas lift");
        context.assertTrue(!MulaLift.lifts(2 * MulaLift.liftOf(0.0)), "two empty Mulas don't");
        context.complete();
    }

    private static void night(TestContext context, long timeOfDay, Runnable test) {
        ServerWorld world = context.getWorld();
        long before = world.getTimeOfDay();
        world.setTimeOfDay(timeOfDay);
        context.waitAndRun(25, () -> {
            try {
                test.run();
            } finally {
                world.setTimeOfDay(before);
            }
        });
    }

    /** At night, three small Mulas carry their holder (Levitation to rise); no damage to them. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80, batchId = "mula_lift_night")
    public void atNightEnoughLiftCarries(TestContext context) {
        ServerPlayerEntity player = player(context);
        List<MulaEntity> mulas = leashed(context, player, 0, 0, 0);
        night(context, 14000, () -> {
            try {
                context.assertTrue(MulaLift.isCarried(player), "carried");
                context.assertTrue(player.hasStatusEffect(StatusEffects.LEVITATION)
                        || player.hasStatusEffect(StatusEffects.SLOW_FALLING), "lifted by vanilla effects, no flight");
                for (MulaEntity m : mulas) {
                    context.assertTrue(m.isCarrying() && m.getHealth() == m.getMaxHealth(), "happy and unhurt");
                }
            } finally {
                mulas.forEach(MulaEntity::discard);
                context.getWorld().getServer().getPlayerManager().remove(player);
            }
            context.complete();
        });
    }

    /** At night, two small Mulas are not enough: no lift, tethered above, no damage. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80, batchId = "mula_lift_night_short")
    public void atNightTooLittleLiftDoesNot(TestContext context) {
        ServerPlayerEntity player = player(context);
        List<MulaEntity> mulas = leashed(context, player, 0, hunger(0.2));
        night(context, 14000, () -> {
            try {
                context.assertTrue(!MulaLift.isCarried(player) && !player.hasStatusEffect(StatusEffects.LEVITATION), "not lifted");
                for (MulaEntity m : mulas) {
                    context.assertTrue(!m.isCarrying() && m.getHealth() == m.getMaxHealth(), "tethered, unhurt");
                }
            } finally {
                mulas.forEach(MulaEntity::discard);
                context.getWorld().getServer().getPlayerManager().remove(player);
            }
            context.complete();
        });
    }

    /** By day, even one big Mula doesn't lift anyone. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80, batchId = "mula_lift_day")
    public void byDayNoLift(TestContext context) {
        ServerPlayerEntity player = player(context);
        List<MulaEntity> mulas = leashed(context, player, hunger(0.9));
        night(context, 6000, () -> {
            try {
                context.assertTrue(!MulaLift.isCarried(player) && !player.hasStatusEffect(StatusEffects.LEVITATION), "no lift by day");
            } finally {
                mulas.forEach(MulaEntity::discard);
                context.getWorld().getServer().getPlayerManager().remove(player);
            }
            context.complete();
        });
    }

    /** Letting go of the leads, or the dawn, gives slow falling down to the ground (and no fall damage). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120, batchId = "mula_lift_release")
    public void lettingGoOrDawnGivesSlowFalling(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(context);
        List<MulaEntity> mulas = leashed(context, player, hunger(0.9));
        long before = world.getTimeOfDay();
        world.setTimeOfDay(14000);
        context.runAtTick(25, () -> context.assertTrue(MulaLift.isCarried(player), "carried"));
        context.runAtTick(26, () -> mulas.forEach(m -> m.detachLeash(true, false)));
        context.runAtTick(40, () -> {
            context.assertTrue(!MulaLift.isCarried(player), "let go");
            context.assertTrue(player.hasStatusEffect(StatusEffects.SLOW_FALLING), "slow falling after letting go");
            context.assertTrue(!player.hasStatusEffect(StatusEffects.LEVITATION), "no more rising");
            context.assertTrue(player.fallDistance == 0, "no fall damage to come");
            mulas.forEach(m -> m.attachLeash(player, true));
        });
        context.runAtTick(60, () -> {
            context.assertTrue(MulaLift.isCarried(player), "carried again");
            world.setTimeOfDay(1000); // dawn
        });
        context.runAtTick(90, () -> {
            try {
                context.assertTrue(!MulaLift.isCarried(player), "set down at dawn");
                context.assertTrue(player.hasStatusEffect(StatusEffects.SLOW_FALLING), "gently: slow falling");
            } finally {
                world.setTimeOfDay(before);
                mulas.forEach(MulaEntity::discard);
                world.getServer().getPlayerManager().remove(player);
            }
            context.complete();
        });
    }
}
