package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyRewind;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlock;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import fr.lordfinn.steveparty.board.LinkTargets;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The Party Rewinder (registry id step_controller): its buttons; linked with the Tile Linker Brush to any element of a party, what its pulses do to a party. */
public class StepControllerGameTests implements SteveGameTest {

    private static StepControllerBlockEntity stepController(TestContext context, BlockPos pos) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.STEP_CONTROLLER);
        return context.getBlockEntity(pos);
    }

    /** A Party Controller at {@code pos} whose party runs on the first of plain steps. */
    private static PartyControllerEntity runningParty(TestContext context, BlockPos pos) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(pos);
        UUID token = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(token);
        data.addStep(new PartyStep());
        data.addStep(new PartyStep());
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token))));
        data.setStepIndex(0);
        data.getSteps().getFirst().setStatus(PartyStep.Status.IN_PROGRESS);
        controller.setPartyData(data);
        return controller;
    }

    /**
     * Painted from the Step Controller to a board space, it drives the party of that space's board (not the nearest
     * one); painted again, the link is erased; painted the other way round (from the space), it is made too; undone, it
     * is gone. The brush reads it as a link target.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "step_controller_board")
    public void aStepControllerLinkedToABoardSpaceDrivesItsParty(TestContext context) {
        BlockPos space = new BlockPos(1, 1, 1), boardParty = new BlockPos(2, 1, 1), nearParty = new BlockPos(7, 1, 6), stepPos = new BlockPos(6, 1, 6);
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.CREATIVE);
        try {
            PartyControllerEntity linked = runningParty(context, boardParty);
            PartyControllerEntity near = runningParty(context, nearParty);
            BlockPos spaceAbs = BoardLinkingGameTests.tiles(context, ModBlocks.TILE, space).getFirst();
            StepControllerBlockEntity stepController = stepController(context, stepPos);
            BlockPos stepAbs = context.getAbsolutePos(stepPos);
            context.assertTrue(LinkTargets.accepts(context.getWorld(), spaceAbs) && LinkTargets.accepts(context.getWorld(), context.getAbsolutePos(boardParty)),
                    "a board space and a Party Controller are link targets");
            context.assertTrue(stepController.driven().party() == near, "not linked: the nearest party");

            ItemStack brush = BoardLinkingGameTests.brush(player);
            BoardLinkingGameTests.paint(player, brush, context, stepAbs, spaceAbs);
            GlobalPos expected = GlobalPos.create(context.getWorld().getRegistryKey(), spaceAbs);
            context.assertEquals(stepController.getLink(), expected, "linked to the board space");
            context.assertTrue(stepController.driven().party() == linked, "it drives the party of the space's board");
            context.assertTrue(stepController.trigger(), "a pulse");
            context.assertEquals(linked.getPartyData().getStepIndex(), 1, "the board's party went on");
            context.assertEquals(near.getPartyData().getStepIndex(), 0, "the nearest party was not touched");

            BoardLinkingGameTests.paint(player, brush, context, stepAbs, spaceAbs);
            context.assertTrue(stepController.getLink() == null, "painted again: erased");
            BoardLinkingGameTests.paint(player, brush, context, spaceAbs, stepAbs);
            context.assertEquals(stepController.getLink(), expected, "painted from the space to the controller: linked");
            context.assertTrue(TileLinkerBrush.undo(player, brush, true) && stepController.getLink() == null, "undone");
            context.assertTrue(TileLinkerBrush.undo(player, brush, false) && expected.equals(stepController.getLink()), "redone");
        } finally {
            TestPlayers.remove(context, player);
            TestCleanup.removeIf(context, boardParty, ModBlocks.PARTY_CONTROLLER);
            TestCleanup.removeIf(context, nearParty, ModBlocks.PARTY_CONTROLLER);
        }
        context.complete();
    }

    /**
     * « Next » passes a turn whose player has not rolled; « replay » plays that turn again from its start, as often as
     * pulsed; « previous » goes back to the turn before. A comparator reads the party's phase.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "step_controller_turns")
    public void aStepControllerPassesAndReplaysTurns(TestContext context) {
        BlockPos controllerPos = new BlockPos(2, 1, 2), stepPos = new BlockPos(6, 1, 2);
        try {
            context.setBlockState(controllerPos.down(), Blocks.STONE);
            context.setBlockState(controllerPos, ModBlocks.PARTY_CONTROLLER);
            PartyControllerEntity controller = context.getBlockEntity(controllerPos);
            UUID a = ((Entity) TestBoards.token(context, new BlockPos(4, 1, 4), null)).getUuid();
            UUID b = ((Entity) TestBoards.token(context, new BlockPos(5, 1, 5), null)).getUuid();
            PartyData data = new PartyData();
            data.addToken(a);
            data.addToken(b);
            data.addStep(new TokenTurnPartyStep(a, null));
            data.addStep(new TokenTurnPartyStep(b, null));
            data.addStep(new EndPartyStep(new ArrayList<>(List.of(a, b))));
            controller.setPartyData(data);
            controller.nextStep();
            context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && a.equals(turn.getTokenUUID()), "a's turn");

            StepControllerBlockEntity stepController = stepController(context, stepPos);
            context.assertEquals(stepController.getMode(), StepPulse.Mode.NEXT, "« next » first");
            context.assertTrue(stepController.trigger(), "next");
            context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && b.equals(turn.getTokenUUID()), "a's turn passed: b's turn");
            context.assertEquals(stepController.getComparatorOutput(), PartyControllerEntity.PHASE_TURN, "the comparator reads a turn");
            context.assertEquals(PartyRewind.turnsBack(controller), 1, "« previous » could go back 1 turn");
            context.assertEquals(key(StepPulse.preview(controller, StepPulse.Mode.REPLAY)), HINT + "replay.turn", "« replay »: b's turn");

            stepController.cycleMode();
            context.assertEquals(stepController.getMode(), StepPulse.Mode.REPLAY, "then « replay »");
            context.assertTrue(stepController.trigger(), "replay");
            PartyData replayed = controller.getPartyData();
            context.assertTrue(replayed.getSteps().size() == 3 && replayed.getStepIndex() == 1
                    && replayed.getCurrentStep() instanceof TokenTurnPartyStep turn && b.equals(turn.getTokenUUID())
                    && turn.getStatus() == PartyStep.Status.IN_PROGRESS, "b's turn starts again, no turn added");
            context.assertTrue(stepController.trigger() && controller.getPartyData().getStepIndex() == 1, "and again");

            stepController.cycleMode();
            context.assertEquals(stepController.getMode(), StepPulse.Mode.PREVIOUS, "then « previous »");
            context.assertTrue(stepController.trigger(), "previous");
            context.assertTrue(controller.getPartyData().getStepIndex() == 0
                    && controller.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn && a.equals(turn.getTokenUUID()), "back to a's turn");
            context.assertTrue(!stepController.trigger(), "nothing before the first turn");
            context.assertEquals(PartyRewind.turnsBack(controller), 0, "no turn to go back to");
            context.assertEquals(key(StepPulse.preview(controller, StepPulse.Mode.PREVIOUS)), HINT + "previous.none", "said so");
            stepController.cycleMode();
            context.assertEquals(stepController.getMode(), StepPulse.Mode.NEXT, "round the modes");
        } finally {
            TestCleanup.removeIf(context, controllerPos, ModBlocks.PARTY_CONTROLLER);
        }
        context.complete();
    }

    private static final String HINT = "block.steveparty.step_controller.hint.";

    private static String key(Text text) {
        return text.getContent() instanceof TranslatableTextContent translatable ? translatable.getKey() : text.getString();
    }

    /**
     * A click on the top picks the button under it, in thirds, « previous » on the left seen from the front, whichever
     * way the deck faces; below the top (the door, the sides), no button. Placed, it faces whoever placed it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aClickOnAButtonChoosesItsMode(TestContext context) {
        double top = 12.5 / 16, depth = 9.5 / 16;
        // Seen from the front: left, middle, right, as x (facing north) goes 12 → 8 → 4
        double[] across = {12 / 16.0, 8 / 16.0, 4 / 16.0};
        StepPulse.Mode[] modes = {StepPulse.Mode.PREVIOUS, StepPulse.Mode.REPLAY, StepPulse.Mode.NEXT};
        for (int i = 0; i < 3; i++) {
            double a = across[i];
            context.assertEquals(StepControllerBlock.buttonAt(Direction.NORTH, new Vec3d(a, top, depth)), modes[i], "north " + modes[i]);
            context.assertEquals(StepControllerBlock.buttonAt(Direction.SOUTH, new Vec3d(1 - a, top, 1 - depth)), modes[i], "south " + modes[i]);
            context.assertEquals(StepControllerBlock.buttonAt(Direction.EAST, new Vec3d(1 - depth, top, a)), modes[i], "east " + modes[i]);
            context.assertEquals(StepControllerBlock.buttonAt(Direction.WEST, new Vec3d(depth, top, 1 - a)), modes[i], "west " + modes[i]);
        }
        context.assertEquals(StepControllerBlock.buttonAt(Direction.NORTH, new Vec3d(0.5, 0.75, 1)), StepPulse.Mode.REPLAY, "the deck's top, behind the buttons");
        context.assertTrue(StepControllerBlock.buttonAt(Direction.NORTH, new Vec3d(0.5, 0.4, 0.3)) == null, "the door: no button");

        BlockPos pos = new BlockPos(1, 1, 1);
        StepControllerBlockEntity rewinder = stepController(context, pos);
        rewinder.selectMode(StepPulse.Mode.PREVIOUS, null);
        context.assertEquals(rewinder.getMode(), StepPulse.Mode.PREVIOUS, "chosen by its button");
        rewinder.selectMode(StepPulse.Mode.PREVIOUS, null);
        context.assertEquals(rewinder.getMode(), StepPulse.Mode.PREVIOUS, "the same button again: still « previous »");
        context.assertEquals(key(StepPulse.Mode.REPLAY.label().getSiblings().get(2)), "block.steveparty.step_controller.mode.replay", "named");
        context.assertEquals(rewinder.describe(StepPulse.Mode.NEXT).getString().isEmpty(), false, "says what it would do");
        context.complete();
    }
}
