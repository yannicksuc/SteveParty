package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.utils.ScorePopupStack;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * The score popups of a goal pole base: points summed per holder on the server and sent at most every
 * {@link GoalPoleBaseBlockEntity#POPUP_INTERVAL} ticks; on the client, one popup per holder that counts up.
 */
public class GoalPolePopupGameTests implements SteveGameTest {
    private static final BlockPos BASE = new BlockPos(2, 1, 2);

    private static GoalPoleBaseBlockEntity placeBase(TestContext context) {
        context.setBlockState(BASE, ModBlocks.GOAL_POLE_BASE.getDefaultState().with(GoalPoleBaseBlock.FACING, Direction.NORTH));
        GoalPoleNetwork.processPending();
        return (GoalPoleBaseBlockEntity) context.getWorld().getBlockEntity(context.getAbsolutePos(BASE));
    }

    /** 30 points in half a second (a fast objective, like distance walked): at most 2 updates, carrying the 30. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void fastPointsAreSummedIntoFewPopupUpdates(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context);
        for (int tick = 1; tick <= 10; tick++) {
            context.runAtTick(tick, () -> {
                for (int i = 0; i < 3; i++) base.credit("Alex", 1, null);
            });
        }
        context.runAtTick(30, () -> {
            context.assertTrue(base.getTotal() == 30, "30 points counted, got " + base.getTotal());
            context.assertTrue(base.getPopupUpdates() >= 1 && base.getPopupUpdates() <= 2, "≤ 2 updates, got " + base.getPopupUpdates());
            context.assertTrue(base.getPopupPoints() == 30, "the updates carry 30, got " + base.getPopupPoints());
            context.complete();
        });
    }

    /** A point every tick for 2 seconds: one update per interval at most; a lone point still shows on its own. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void popupUpdatesAreRateLimited(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context);
        for (int tick = 1; tick <= 40; tick++) {
            context.runAtTick(tick, () -> {
                base.credit("Alex", 1, null);
                base.credit("Steve", 1, null);
            });
        }
        int most = 40 / GoalPoleBaseBlockEntity.POPUP_INTERVAL + 1;
        context.runAtTick(60, () -> {
            context.assertTrue(base.getPopupUpdates() <= most, "≤ " + most + " updates, got " + base.getPopupUpdates());
            context.assertTrue(base.getPopupPoints() == 80, "every point carried, got " + base.getPopupPoints());
            base.credit("Alex", 1, null);
        });
        context.runAtTick(62, () -> {
            context.assertTrue(base.getPopupPoints() == 81, "a lone point is sent right away, got " + base.getPopupPoints());
            context.complete();
        });
    }

    /** Points taken away (here by a command on the base's objective) are sent too, as a negative change. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void lostPointsAreSentToo(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context);
        context.runAtTick(1, () -> base.credit("Alex", 5, null));
        context.runAtTick(20, () -> {
            context.assertTrue(base.getPopupPoints() == 5, "+5 sent, got " + base.getPopupPoints());
            Scoreboard scoreboard = context.getWorld().getServer().getScoreboard();
            ScoreboardObjective objective = scoreboard.getNullableObjective(base.getObjectiveName());
            context.assertTrue(objective != null, "base objective");
            scoreboard.getOrCreateScore(ScoreHolder.fromName("Alex"), objective).setScore(2);
        });
        context.runAtTick(25, () -> {
            context.assertTrue(base.getTotal() == 2, "down to 2, got " + base.getTotal());
            context.assertTrue(base.getPopupUpdates() == 2 && base.getPopupPoints() == 2, "−3 sent, got "
                    + base.getPopupUpdates() + " updates, " + base.getPopupPoints() + " points");
            context.complete();
        });
    }

    /** Client side: a holder's popup still showing counts up and lives on; past 5 popups, the oldest fades out. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void popupsMergePerHolderAndAreCapped(TestContext context) {
        ScorePopupStack stack = new ScorePopupStack();
        stack.add("Alex", 1);
        context.assertTrue(stack.popups().size() == 1 && stack.popups().get(0).text().equals("+1"), "a lone +1");
        for (int i = 0; i < 30; i++) stack.tick();
        stack.add("Alex", 4);
        context.assertTrue(stack.popups().size() == 1 && stack.popups().get(0).text().equals("+5"), "merged into +5");
        context.assertTrue(stack.popups().get(0).scale(0) > 1f || stack.popups().get(0).scale(0.5f) > 1f, "bumps");
        // Its life was given back: still there after another 30 ticks
        for (int i = 0; i < 30; i++) stack.tick();
        context.assertTrue(stack.popups().size() == 1, "life refreshed");
        // A loss is a popup of its own
        stack.add("Alex", -3);
        context.assertTrue(stack.popups().size() == 2 && stack.popups().get(1).text().equals("−3"), "−3 apart");
        // Cap: 5 showing at most, the oldest leaves first
        for (String holder : new String[]{"B", "C", "D", "E"}) stack.add(holder, 1);
        context.assertTrue(stack.shownCount() == 5 && stack.popups().get(0).isPushedOut(), "the oldest is pushed out");
        for (int i = 0; i < ScorePopupStack.PUSHED_OUT_LIFE; i++) stack.tick();
        context.assertTrue(stack.popups().size() == 5, "it is gone, got " + stack.popups().size());
        for (int i = 0; i < ScorePopupStack.LIFE; i++) stack.tick();
        context.assertTrue(stack.isEmpty(), "all faded");
        context.complete();
    }
}
