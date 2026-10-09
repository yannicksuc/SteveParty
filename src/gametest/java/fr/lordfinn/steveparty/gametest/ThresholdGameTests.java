package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Kind;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Landing;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Played;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.ThresholdCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ThresholdCartridgeItem.Operator;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.service.TurnMoves;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The Threshold obstacle Cartridge: every condition, a token stopped (its steps lost, a landing) or getting over, the
 * next move leaving the obstacle without a new test, a check point obstacle, a token stopping on it untested.
 */
public class ThresholdGameTests implements FabricGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 7);
    /** Board spaces 2 blocks apart along x. */
    private static final List<BlockPos> PATH = List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1),
            new BlockPos(7, 1, 1), new BlockPos(9, 1, 1), new BlockPos(11, 1, 1), new BlockPos(13, 1, 1));

    private static void space(TestContext context, BlockPos pos, Block block, ItemStack cartridge, BlockPos... to) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, block);
        List<BlockPos> destinations = new ArrayList<>();
        for (BlockPos next : to) destinations.add(context.getAbsolutePos(next));
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(destinations, ""));
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        space.setStack(0, cartridge);
    }

    /** The path, the obstacle {@code obstacle} at index {@code at} (a check point if {@code checkPoint}). */
    private static void path(TestContext context, int at, ItemStack obstacle, boolean checkPoint) {
        for (int i = 0; i < PATH.size(); i++) {
            BlockPos[] next = i + 1 < PATH.size() ? new BlockPos[]{PATH.get(i + 1)} : new BlockPos[0];
            boolean special = i == at;
            space(context, PATH.get(i), special && checkPoint ? ModBlocks.CHECK_POINT : ModBlocks.TILE,
                    special ? obstacle : new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), next);
        }
    }

    private static PigEntity token(TestContext context, BlockPos on) {
        PigEntity pig = context.spawnMob(EntityType.PIG, on);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        atEnd(context, () -> TurnMoves.forget(pig.getUuid()));
        return pig;
    }

    private static PartyControllerEntity party(TestContext context, MobEntity token) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(token.getUuid());
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(token.getUuid(), null));
        data.addStep(new TokenTurnPartyStep(token.getUuid(), null));
        data.addStep(new TokenTurnPartyStep(token.getUuid(), null));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        // A party left running would go on looking for star spaces around it (other tests' boards)
        atEnd(context, () -> context.removeBlock(CONTROLLER));
        return controller;
    }

    /** What to undo when the test ends (a test has one final task: they are run together). */
    private static final java.util.Map<TestContext, List<Runnable>> AT_END = new java.util.WeakHashMap<>();

    static void atEnd(TestContext context, Runnable task) {
        List<Runnable> tasks = AT_END.get(context);
        if (tasks == null) {
            List<Runnable> created = new ArrayList<>();
            AT_END.put(context, created);
            context.addFinalTask(() -> created.forEach(Runnable::run));
            tasks = created;
        }
        tasks.add(task);
    }

    private static List<Played> record(TestContext context) {
        List<Played> played = new ArrayList<>();
        Consumer<Played> listener = played::add;
        TileFeedback.LISTENERS.add(listener);
        atEnd(context, () -> TileFeedback.LISTENERS.remove(listener));
        return played;
    }

    private static boolean landed(List<Played> played, TestContext context, BlockPos at, Landing landing) {
        BlockPos absolute = context.getAbsolutePos(at);
        return played.stream().anyMatch(event -> event.kind() == Kind.LAND && event.tile().equals(absolute) && event.landing() == landing);
    }

    private static boolean isOn(TestContext context, MobEntity token, BlockPos at) {
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        return on != null && on.getPos().equals(context.getAbsolutePos(at));
    }

    private static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
            return;
        }
        context.assertTrue(ticks > 0, "timed out: " + what);
        context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
    }

    /** Rolls {@code faces} for {@code token} (as a die would: see TokenMovementService) and moves it. */
    private static void roll(TestContext context, MobEntity token, int... faces) {
        int total = 0;
        List<Integer> list = new ArrayList<>();
        for (int face : faces) {
            total += face;
            list.add(face);
        }
        BoardSpaceBlockEntity from = BoardSpaces.boardSpaceOf(token);
        TurnMoves.record(token, total, list, from == null ? null : from.getPos());
        TokenMovementService.moveEntityOnBoard(token, total);
    }

    private static int steps(MobEntity token) {
        return ((TokenizedEntityInterface) token).steveparty$getNbSteps();
    }

    private static ItemStack obstacle(Operator operator, int value) {
        return ThresholdCartridgeItem.with(ModItems.THRESHOLD_CARTRIDGE, operator, value);
    }

    // ---------------------------------------------------------------- the conditions

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyConditionReadsTheRoll(TestContext context) {
        TurnMoves.Roll seven = new TurnMoves.Roll(7, List.of(3, 4), null);
        context.assertTrue(Operator.AT_LEAST.passes(seven, 7) && !Operator.AT_LEAST.passes(seven, 8), "≥");
        context.assertTrue(Operator.AT_MOST.passes(seven, 7) && !Operator.AT_MOST.passes(seven, 6), "≤");
        context.assertTrue(Operator.MORE.passes(seven, 6) && !Operator.MORE.passes(seven, 7), ">");
        context.assertTrue(Operator.LESS.passes(seven, 8) && !Operator.LESS.passes(seven, 7), "<");
        context.assertTrue(Operator.EXACTLY.passes(seven, 7) && !Operator.EXACTLY.passes(seven, 6), "=");
        // Modules and power-ups count: the total walked, whatever the faces
        context.assertTrue(Operator.AT_LEAST.passes(new TurnMoves.Roll(10, List.of(3, 4), null), 10), "the total with its bonus");
        context.assertTrue(!Operator.DOUBLE.passes(seven, 0), "no pair");
        context.assertTrue(Operator.DOUBLE.passes(new TurnMoves.Roll(8, List.of(4, 4), null), 0), "a pair");
        context.assertTrue(Operator.DOUBLE.passes(new TurnMoves.Roll(9, List.of(4, 1, 4), null), 0), "a pair among three dice");
        context.assertTrue(!Operator.DOUBLE.passes(new TurnMoves.Roll(6, List.of(6), null), 0), "one die never makes a double");
        context.assertTrue(!Operator.TRIPLE.passes(new TurnMoves.Roll(9, List.of(4, 1, 4), null), 0), "a pair is no triple");
        context.assertTrue(Operator.TRIPLE.passes(new TurnMoves.Roll(6, List.of(2, 2, 2), null), 0), "three of a kind");
        // The cartridge: ≥ 7 by default, its label
        ItemStack plain = new ItemStack(ModItems.THRESHOLD_CARTRIDGE);
        context.assertEquals(ThresholdCartridgeItem.operator(plain), Operator.AT_LEAST, "≥ by default");
        context.assertEquals(ThresholdCartridgeItem.value(plain), ThresholdCartridgeItem.DEFAULT_VALUE, "7 by default");
        context.assertEquals(ThresholdCartridgeItem.label(plain), net.minecraft.text.Text.translatable("gui.steveparty.threshold.at_least", 7), "its label");
        context.assertEquals(ThresholdCartridgeItem.label(obstacle(Operator.LESS, 4)), net.minecraft.text.Text.translatable("gui.steveparty.threshold.less", 4), "another label");
        context.complete();
    }

    // ---------------------------------------------------------------- on the board

    /** ≥ 5, a roll of 4: stopped on the obstacle, the steps left lost, a « Stop » landing; the turn ends there. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void aRollTooLowIsStoppedOnTheObstacle(TestContext context) {
        path(context, 2, obstacle(Operator.AT_LEAST, 5), false);
        List<Played> played = record(context);
        PigEntity pig = token(context, PATH.get(0));
        PartyControllerEntity controller = party(context, pig);
        context.waitAndRun(2, () -> {
            roll(context, pig, 1, 3);
            when(context, () -> controller.getPartyData().getStepIndex() >= 2, 200, "the turn ends", () -> {
                context.assertTrue(isOn(context, pig, PATH.get(2)), "stopped on the obstacle");
                context.assertEquals(steps(pig), 0, "its steps left are lost");
                context.assertTrue(landed(played, context, PATH.get(2), Landing.STOP), "a « Stop » landing");
                context.complete();
            });
        });
    }

    /** ≥ 5, a roll of 5: it gets over and walks its 5 steps. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void aRollHighEnoughGetsOver(TestContext context) {
        path(context, 2, obstacle(Operator.AT_LEAST, 5), false);
        List<Played> played = record(context);
        PigEntity pig = token(context, PATH.get(0));
        PartyControllerEntity controller = party(context, pig);
        context.waitAndRun(2, () -> {
            roll(context, pig, 5);
            when(context, () -> controller.getPartyData().getStepIndex() >= 2, 250, "the turn ends", () -> {
                context.assertTrue(isOn(context, pig, PATH.get(5)), "5 spaces further");
                context.assertTrue(!landed(played, context, PATH.get(2), Landing.STOP), "no stop on the obstacle");
                context.complete();
            });
        });
    }

    /** Stopped on a Double obstacle, its next move leaves it without a new test, even with a single die. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void theNextMoveLeavesTheObstacleWithoutATest(TestContext context) {
        path(context, 1, obstacle(Operator.DOUBLE, 0), false);
        PigEntity pig = token(context, PATH.get(0));
        PartyControllerEntity controller = party(context, pig);
        context.waitAndRun(2, () -> {
            roll(context, pig, 2, 3);
            when(context, () -> controller.getPartyData().getStepIndex() >= 2, 200, "the first turn ends", () -> {
                context.assertTrue(isOn(context, pig, PATH.get(1)), "stopped: no pair");
                roll(context, pig, 2);
                when(context, () -> controller.getPartyData().getStepIndex() >= 3, 200, "the second turn ends", () -> {
                    context.assertTrue(isOn(context, pig, PATH.get(3)), "it left the obstacle and walked its 2 steps");
                    context.complete();
                });
            });
        });
    }

    /** A pair gets over a Double obstacle; a triple obstacle stops the same pair. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void doublesAndTriples(TestContext context) {
        path(context, 1, obstacle(Operator.DOUBLE, 0), false);
        PigEntity pig = token(context, PATH.get(0));
        context.waitAndRun(2, () -> {
            roll(context, pig, 2, 2);
            when(context, () -> steps(pig) == 0 && isOn(context, pig, PATH.get(4)), 200, "a pair gets over", () -> {
                // A triple obstacle on the next space: the same pair is stopped there
                BoardSpaceBlockEntity next = context.getBlockEntity(PATH.get(5));
                ItemStack triple = obstacle(Operator.TRIPLE, 0);
                triple.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(List.of(context.getAbsolutePos(PATH.get(6))), ""));
                next.setStack(0, triple);
                roll(context, pig, 1, 1);
                when(context, () -> steps(pig) == 0 && isOn(context, pig, PATH.get(5)), 200, "a pair is stopped by a triple", () -> {
                    context.waitAndRun(10, () -> {
                        context.assertTrue(isOn(context, pig, PATH.get(5)), "still on the triple obstacle");
                        context.complete();
                    });
                });
            });
        });
    }

    /** On a check point: stopped there, the token lands on it (the turn ends with it on the check point). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void aCheckPointObstacleStopsToo(TestContext context) {
        path(context, 2, obstacle(Operator.AT_LEAST, 6), true);
        List<Played> played = record(context);
        PigEntity pig = token(context, PATH.get(0));
        PartyControllerEntity controller = party(context, pig);
        context.waitAndRun(2, () -> {
            roll(context, pig, 4);
            when(context, () -> controller.getPartyData().getStepIndex() >= 2, 200, "the turn ends", () -> {
                context.assertTrue(isOn(context, pig, PATH.get(2)), "stopped on the check point");
                context.assertTrue(landed(played, context, PATH.get(2), Landing.STOP), "a landing on the check point");
                context.complete();
            });
        });
    }

    /** A token whose roll ends exactly on the obstacle stops there untested: a plain landing. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void stoppingOnItIsNoTest(TestContext context) {
        path(context, 2, obstacle(Operator.AT_LEAST, 9), false);
        List<Played> played = record(context);
        PigEntity pig = token(context, PATH.get(0));
        PartyControllerEntity controller = party(context, pig);
        context.waitAndRun(2, () -> {
            roll(context, pig, 2);
            when(context, () -> controller.getPartyData().getStepIndex() >= 2, 200, "the turn ends", () -> {
                context.assertTrue(isOn(context, pig, PATH.get(2)), "on the obstacle");
                context.assertTrue(landed(played, context, PATH.get(2), Landing.DEFAULT), "a plain landing");
                context.complete();
            });
        });
    }
}
