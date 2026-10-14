package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyParking;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResults;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.TestWait;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.EndCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.EndCartridgeItem.Ending;
import fr.lordfinn.steveparty.items.custom.cartridges.EndCartridgeItem.Ranking;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The End space (Finish Cartridge): a pawn stopping on it heads the party for its end as the cartridge says, and the
 * party is ranked by arrival or by stars.
 */
public class EndSpaceGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 5), END = new BlockPos(3, 1, 2);

    private record Board(PartyControllerEntity controller, PigEntity a, PigEntity b, BoardSpaceBlockEntity end) {
    }

    /** Two pawns, an End space, a party of 3 rounds at a's first turn. */
    private static Board board(TestContext context, Ending ending, Ranking ranking) {
        TestBoards.floor(context, 7);
        PigEntity a = TestBoards.token(context, new BlockPos(1, 1, 1), null);
        PigEntity b = TestBoards.token(context, new BlockPos(5, 1, 1), null);
        a.setAiDisabled(true);
        b.setAiDisabled(true);
        a.setCustomName(Text.literal("Pawn A"));
        b.setCustomName(Text.literal("Pawn B"));
        context.setBlockState(END, ModBlocks.TILE);
        BoardSpaceBlockEntity end = context.getBlockEntity(END);
        end.setStack(0, EndCartridgeItem.with(ModItems.END_CARTRIDGE, ending, ranking));
        context.expectBlockProperty(END, ABoardSpaceBlock.TILE_TYPE, BoardSpaceType.TILE_END);
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 3, a.getUuid(), b.getUuid());
        TestCleanup.atEnd(context, () -> {
            ((TokenizedEntityInterface) a).steveparty$setTokenized(false);
            ((TokenizedEntityInterface) b).steveparty$setTokenized(false);
            PartyParking.restoreSize(a);
            PartyParking.restoreSize(b);
            TestCleanup.removeIf(context, CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        });
        return new Board(controller, a, b, end);
    }

    /** The steps after the current one: "a"/"b" for a turn, "END" for the end, "?" for anything else. */
    private static List<String> next(Board board) {
        PartyData data = board.controller().getPartyData();
        List<String> next = new ArrayList<>();
        List<PartyStep> steps = data.getSteps();
        for (int i = data.getStepIndex() + 1; i < steps.size(); i++) {
            PartyStep step = steps.get(i);
            if (step instanceof TokenTurnPartyStep turn)
                next.add(turn.getTokenUUID().equals(board.a().getUuid()) ? "a" : turn.getTokenUUID().equals(board.b().getUuid()) ? "b" : "?");
            else next.add(step.getType() == PartyStepType.END ? "END" : "?");
        }
        return next;
    }

    private static UUID current(Board board) {
        return board.controller().getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn ? turn.getTokenUUID() : null;
    }

    /** a lands on the End space during its turn (the party then goes on to its next step). */
    private static void aArrives(Board board) {
        board.end().onDestinationReached(board.a(), board.controller());
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theRoundIsPlayedOutThenThePartyEnds(TestContext context) {
        Board board = board(context, Ending.FINISH_ROUND, Ranking.ARRIVAL);
        context.assertEquals(current(board), board.a().getUuid(), "a's turn");
        aArrives(board);
        context.assertEquals(board.controller().getPartyData().getArrivals(), List.of(board.a().getUuid()), "a arrived");
        context.assertEquals(current(board), board.b().getUuid(), "b plays the end of the round");
        context.assertEquals(next(board), List.of("END"), "then the end");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stopEndsThePartyAfterThisTurn(TestContext context) {
        Board board = board(context, Ending.AT_ONCE, Ranking.ARRIVAL);
        aArrives(board);
        context.assertTrue(board.controller().getPartyData().isAtEnd(), "the party is over");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oneMoreTurnForTheOthers(TestContext context) {
        Board board = board(context, Ending.ONE_MORE_TURN, Ranking.ARRIVAL);
        aArrives(board);
        context.assertEquals(current(board), board.b().getUuid(), "b ends the round");
        context.assertEquals(next(board), List.of("b", "END"), "one more turn for b only, then the end");
        context.complete();
    }

    /** Waiting: a is set aside on the End space, its turns gone; once b arrives too the party ends. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void arrivedPawnsWaitForTheOthers(TestContext context) {
        Board board = board(context, Ending.WAIT_FOR_ALL, Ranking.ARRIVAL);
        aArrives(board);
        context.assertEquals(current(board), board.b().getUuid(), "b plays");
        context.assertEquals(next(board), List.of("b", "b", "END"), "a's turns are gone, b plays on");
        // b arrives too
        board.end().onDestinationReached(board.b(), board.controller());
        context.assertTrue(board.controller().getPartyData().isAtEnd(), "everyone arrived: the party is over");
        // Ranked by arrival: a first, then b
        List<PartyResults.Place> places = PartyResults.ranking(board.controller(), context.getWorld());
        context.assertTrue(places.size() == 2 && places.get(0).name().equals(board.controller().getTokenDisplayName(context.getWorld(), board.a().getUuid()).getString()),
                "a first: " + places);
        context.complete();
    }

    /** Ranked by arrival, b arriving first wins though a plays first (by stars, a would: same stars, better roll). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theFirstToArriveWins(TestContext context) {
        Board board = board(context, Ending.FINISH_ROUND, Ranking.ARRIVAL);
        PartyControllerEntity controller = board.controller();
        // a plays without arriving, then b arrives
        controller.nextStep();
        context.assertEquals(current(board), board.b().getUuid(), "b's turn");
        board.end().onDestinationReached(board.b(), controller);
        String bName = controller.getTokenDisplayName(context.getWorld(), board.b().getUuid()).getString();
        context.assertEquals(PartyResults.ranking(controller, context.getWorld()).getFirst().name(), bName, "b, arrived, wins");
        controller.getPartyData().setEndRanking(Ranking.STARS.ordinal());
        context.assertTrue(!PartyResults.ranking(controller, context.getWorld()).getFirst().name().equals(bName), "by stars, a wins (turn order)");
        context.complete();
    }

    /** Several pawns waiting on the End space: smaller, turning slowly round its middle. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void pawnsWaitingTogetherAreSmallAndTurn(TestContext context) {
        Board board = board(context, Ending.WAIT_FOR_ALL, Ranking.ARRIVAL);
        // A third pawn keeps the party going while a and b wait
        PigEntity c = TestBoards.token(context, new BlockPos(5, 1, 5), null);
        c.setAiDisabled(true);
        PartyData data = board.controller().getPartyData();
        data.addToken(c.getUuid());
        data.getSteps().add(data.getSteps().size() - 1, new TokenTurnPartyStep(c.getUuid(), null));
        data.getSteps().add(data.getSteps().size() - 1, new TokenTurnPartyStep(c.getUuid(), null));
        TestCleanup.atEnd(context, () -> ((TokenizedEntityInterface) c).steveparty$setTokenized(false));
        aArrives(board);
        board.end().onDestinationReached(board.b(), board.controller());
        TestWait.when(context, () -> PartyParking.isSmall(board.a()) && PartyParking.isSmall(board.b()), 40, "a and b are small", () -> {
            double before = board.a().getX();
            context.waitAndRun(20, () -> {
                context.assertTrue(Math.abs(board.a().getX() - before) > 0.01, "a turns round the space");
                context.assertTrue(board.controller().getPartyData().isStarted(), "c still plays");
                context.complete();
            });
        });
    }
}
