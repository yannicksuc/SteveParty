package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.*;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The moments a Party Antenna listens to: each one rings once, at its time, with its value; the filter keeps only the
 * values asked for; the waiting mode holds the party there. Each test in a batch of its own: an antenna listens to the
 * closest running party within 64 blocks.
 */
public class AntennaMomentsGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 1);

    private static PartyBellBlockEntity antenna(TestContext context, BlockPos pos, PartyMoment moment, boolean waiting) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_BELL.getDefaultState()
                .with(PartyBellBlock.MOMENT, moment).with(PartyBellBlock.WAITING, waiting));
        TestCleanup.atEnd(context, () -> TestCleanup.removeIf(context, pos, ModBlocks.PARTY_BELL));
        return context.getBlockEntity(pos);
    }

    private static PartyControllerEntity controller(TestContext context, PartyData data) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        TestCleanup.atEnd(context, () -> TestCleanup.removeIf(context, CONTROLLER, ModBlocks.PARTY_CONTROLLER));
        controller.setPartyData(data);
        return controller;
    }

    /** It heard its moment once more since {@code before} (a heard-at time), with {@code value}, and rang. */
    private static void assertHeard(TestContext context, PartyBellBlockEntity antenna, int value, String what) {
        context.assertTrue(antenna.getHeardAt() >= 0, what + ": heard");
        context.assertEquals(antenna.getHeardValue(), value, what + ": value");
        context.assertTrue(antenna.heardPassed(), what + ": rang");
    }

    // ---------------------------------------------------------------- party

    /**
     * Final rounds: at the start of each of the last 3 rounds, the rounds left (3, 2, 1), not before; an antenna
     * filtered on « = 1 » only rings at the last one.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "antenna_final_rounds")
    public void finalRoundsCountDown(TestContext context) {
        UUID a = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(a);
        data.addStep(new PartyStep());
        for (int round = 0; round < 4; round++) {
            data.addStep(new TokenTurnPartyStep(a, null));
            data.addStep(new PartyStep());
        }
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(a))));
        PartyBellBlockEntity all = antenna(context, new BlockPos(5, 1, 1), PartyMoment.FINAL_ROUNDS, false);
        PartyBellBlockEntity last = antenna(context, new BlockPos(5, 1, 3), PartyMoment.FINAL_ROUNDS, false);
        last.setFilter(PartyBellBlockEntity.Filter.EQUAL, 1);
        PartyControllerEntity controller = controller(context, data);
        controller.nextStep();
        controller.nextStep(); // round 1 of 4
        context.assertTrue(all.getHeardAt() < 0, "silent at the first of 4 rounds");
        int[] expected = {3, 2, 1};
        for (int left : expected) {
            controller.nextStep(); // the step between rounds
            controller.nextStep(); // the next round
            assertHeard(context, all, left, "round with " + left + " left");
        }
        context.assertTrue(last.heardPassed() && last.getValue() == 1, "the filtered one rang at the last round");
        context.complete();
    }

    /** Party Rewinder: « replay » rings the rewind moment, 1 turn undone. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "antenna_rewind")
    public void rewindRingsTheTurnsUndone(TestContext context) {
        Scene scene = scene(context);
        PartyBellBlockEntity antenna = antenna(context, new BlockPos(7, 1, 1), PartyMoment.REWIND, false);
        context.assertTrue(StepPulse.apply(scene.party(), StepPulse.Mode.REPLAY), "replayed");
        assertHeard(context, antenna, 1, "rewind");
        context.complete();
    }

    // ---------------------------------------------------------------- events

    /** The end of an Event card's time out rings « event over » with its channel. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60, batchId = "antenna_event_over")
    public void eventOverRingsItsChannel(TestContext context) {
        UUID a = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(a);
        data.addStep(new PartyStep());
        data.addStep(EventPartyStep.eventCard(3));
        data.addStep(new TokenTurnPartyStep(a, null));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(a))));
        PartyBellBlockEntity antenna = antenna(context, new BlockPos(5, 1, 1), PartyMoment.EVENT_OVER, false);
        PartyControllerEntity controller = controller(context, data);
        controller.nextStep();
        controller.nextStep(); // the event: it goes on by itself a tick later
        context.assertTrue(antenna.getHeardAt() < 0, "silent while the event runs");
        when(context, () -> data.getCurrentStep() instanceof TokenTurnPartyStep, 40, "the event went on", () -> {
            assertHeard(context, antenna, 3, "event over");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- coins and stars

    /** Coins won, then lost: how many each time; nothing at the first look. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80, batchId = "antenna_coins")
    public void coinsWonAndLost(TestContext context) {
        Scene scene = scene(context);
        PartyBellBlockEntity won = antenna(context, new BlockPos(7, 1, 1), PartyMoment.COINS_WON, false);
        PartyBellBlockEntity lost = antenna(context, new BlockPos(7, 1, 3), PartyMoment.COINS_LOST, false);
        context.waitAndRun(8, () -> {
            context.assertTrue(won.getHeardAt() < 0 && lost.getHeardAt() < 0, "nothing at the first look");
            scene.a().getInventory().insertStack(scene.coin().copyWithCount(3));
            when(context, () -> won.getHeardAt() >= 0, 30, "coins won heard", () -> {
                assertHeard(context, won, 3, "coins won");
                InventoryUtils.take(scene.a().getInventory(), scene.coin(), 2);
                when(context, () -> lost.getHeardAt() >= 0, 30, "coins lost heard", () -> {
                    assertHeard(context, lost, 2, "coins lost");
                    context.complete();
                });
            });
        });
    }

    /** A Party Star won: the player's stars after; and he leads now: his rank in the order. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80, batchId = "antenna_star_leader")
    public void starWonMakesANewLeader(TestContext context) {
        Scene scene = scene(context);
        PartyBellBlockEntity star = antenna(context, new BlockPos(7, 1, 1), PartyMoment.STAR_WON, false);
        PartyBellBlockEntity leader = antenna(context, new BlockPos(7, 1, 3), PartyMoment.NEW_LEADER, false);
        context.waitAndRun(8, () -> {
            scene.b().getInventory().insertStack(scene.party().getCurrency(PartyCurrency.STAR).copyWithCount(1));
            when(context, () -> star.getHeardAt() >= 0 && leader.getHeardAt() >= 0, 30, "star and leader heard", () -> {
                assertHeard(context, star, 1, "star won");
                assertHeard(context, leader, 2, "b leads (second in the order)");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- the board

    /**
     * A fork: « crossroads » with its 2 paths; the path chosen south (3); then the token lands on a plain space (its
     * role as a router says it: 15).
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "antenna_board")
    public void forkPathAndLanding(TestContext context) {
        BlockPos start = new BlockPos(1, 1, 3), fork = new BlockPos(3, 1, 3), east = new BlockPos(5, 1, 3), south = new BlockPos(3, 1, 5);
        TestBoards.floor(context, 8);
        tile(context, start, fork);
        tile(context, fork, east, south);
        tile(context, east);
        tile(context, south);
        PigEntity pig = context.spawnMob(EntityType.PIG, start);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        TestCleanup.atEnd(context, () -> token.steveparty$setTokenized(false));
        PartyData data = new PartyData();
        data.addToken(pig.getUuid());
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(pig.getUuid(), null));
        data.addStep(new TokenTurnPartyStep(pig.getUuid(), null));
        PartyBellBlockEntity crossroads = antenna(context, new BlockPos(7, 1, 1), PartyMoment.CROSSROADS, false);
        PartyBellBlockEntity path = antenna(context, new BlockPos(7, 1, 3), PartyMoment.PATH_CHOSEN, false);
        PartyBellBlockEntity lands = antenna(context, new BlockPos(7, 1, 5), PartyMoment.TOKEN_LANDS, false);
        PartyControllerEntity controller = controller(context, data);
        controller.nextStep();
        controller.nextStep();
        context.waitAndRun(2, () -> {
            TokenMovementService.moveEntityOnBoard(pig, 2);
            when(context, () -> crossroads.getHeardAt() >= 0, 100, "the fork heard", () -> {
                assertHeard(context, crossroads, 2, "crossroads");
                context.assertTrue(path.getHeardAt() < 0 && lands.getHeardAt() < 0, "nothing chosen yet");
                TokenMovementService.moveEntityOnTileToDestination(context.getWorld(), context.getAbsolutePos(fork),
                        new BoardSpaceDestination(context.getAbsolutePos(south), true), pig.getUuid());
                assertHeard(context, path, 3, "south");
                when(context, () -> lands.getHeardAt() >= 0, 150, "the landing heard", () -> {
                    assertHeard(context, lands, 15, "a plain space");
                    context.complete();
                });
            });
        });
    }

    private static void tile(TestContext context, BlockPos pos, BlockPos... to) {
        context.setBlockState(pos, ModBlocks.TILE);
        ItemStack cartridge = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        List<BlockPos> destinations = new ArrayList<>();
        for (BlockPos next : to) destinations.add(context.getAbsolutePos(next));
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(destinations, ""));
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        tile.setStack(0, cartridge);
    }

    // ---------------------------------------------------------------- mini-games

    /** A mini-game step about to leave (countdown over), with {@code players} participants (nobody online). */
    private static MiniGamePartyStep miniGame(TestContext context, int players) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Type", PartyStepType.MINI_GAME.name());
        nbt.putString("Status", PartyStep.Status.IN_PROGRESS.name());
        nbt.putString("Phase", MiniGamePartyStep.Phase.COUNTDOWN.name());
        nbt.putBoolean("MiniGameChosen", true);
        NbtList participants = new NbtList();
        for (int i = 0; i < players; i++) participants.add(NbtString.of(UUID.randomUUID().toString()));
        nbt.put("Participants", participants);
        MiniGamePartyStep step = new MiniGamePartyStep(nbt);
        PartyData data = new PartyData();
        data.addStep(step);
        data.setStepIndex(0);
        controller(context, data);
        return step;
    }

    /** A waiting « mini-game start » antenna holds the departure: the number of players heard, still in its countdown. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "antenna_minigame_start")
    public void miniGameStartHoldsTheDeparture(TestContext context) {
        PartyBellBlockEntity antenna = antenna(context, new BlockPos(5, 1, 1), PartyMoment.MINIGAME_START, true);
        MiniGamePartyStep step = miniGame(context, 2);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        step.depart(controller);
        assertHeard(context, antenna, 2, "mini-game start");
        context.assertTrue(antenna.isHolding() && step.isWaitingForBell(), "the departure waits for its signal");
        context.assertEquals(step.getPhase(), MiniGamePartyStep.Phase.COUNTDOWN, "nobody left yet");
        // Gone before the antenna: nothing to release at the end
        context.removeBlock(CONTROLLER);
        context.complete();
    }

    /** A waiting « practice round » antenna holds the practice: the number of players heard. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "antenna_practice")
    public void practiceRoundHoldsThePractice(TestContext context) {
        PartyBellBlockEntity antenna = antenna(context, new BlockPos(5, 1, 1), PartyMoment.PRACTICE_ROUND, true);
        MiniGamePartyStep step = miniGame(context, 3);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        step.startPractice(controller);
        assertHeard(context, antenna, 3, "practice round");
        context.assertTrue(antenna.isHolding() && step.isWaitingForBell(), "the practice waits for its signal");
        context.removeBlock(CONTROLLER);
        context.complete();
    }

    // ---------------------------------------------------------------- waiting, filter, settings

    /** Two antennas wait at a moment: the party goes on once both had their signal, not at the first. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "antenna_wait_all")
    public void thePartyWaitsForEveryAntenna(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(a);
        data.addToken(b);
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(a, null));
        data.addStep(new TokenTurnPartyStep(b, null));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(a, b))));
        BlockPos first = new BlockPos(5, 1, 1), second = new BlockPos(5, 1, 5);
        antenna(context, first, PartyMoment.TURN_END, true);
        antenna(context, second, PartyMoment.TURN_END, true);
        PartyControllerEntity controller = controller(context, data);
        controller.nextStep();
        controller.nextStep();
        controller.nextStep(); // a's turn ends: both antennas hold the party
        context.waitAndRun(PartyBellBlock.PULSE_TICKS + 4, () -> {
            context.setBlockState(first.east(), Blocks.REDSTONE_BLOCK);
            context.waitAndRun(3, () -> {
                context.assertTrue(data.getCurrentStep() instanceof EventPartyStep event && event.isWaitingForBell(), "still held by the second");
                context.setBlockState(second.east(), Blocks.REDSTONE_BLOCK);
                context.waitAndRun(3, () -> {
                    context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && b.equals(turn.getTokenUUID()), "b's turn");
                    context.complete();
                });
            });
        });
    }

    /** Copied settings: the moment and the filter, never the waiting mode. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "antenna_copy")
    public void copiedSettingsLeaveTheWaitingMode(TestContext context) {
        PartyBellBlockEntity source = antenna(context, new BlockPos(1, 1, 1), PartyMoment.MINIGAME_END, true);
        source.setFilter(PartyBellBlockEntity.Filter.AT_LEAST, 2);
        PartyBellBlockEntity target = antenna(context, new BlockPos(3, 1, 1), PartyMoment.TURN_START, false);
        target.pasteSettings(source.copySettings());
        PartyBellBlockEntity pasted = context.getBlockEntity(new BlockPos(3, 1, 1));
        context.assertEquals(pasted.getMoment(), PartyMoment.MINIGAME_END, "moment copied");
        context.assertTrue(pasted.getFilter() == PartyBellBlockEntity.Filter.AT_LEAST && pasted.getFilterValue() == 2, "filter copied");
        context.assertTrue(!context.getBlockState(new BlockPos(3, 1, 1)).get(PartyBellBlock.WAITING), "never the waiting mode");
        context.complete();
    }

    // ---------------------------------------------------------------- scene

    /** Two players and their pawns, a party of two rounds at a's first turn (as the turn journal's tests). */
    private record Scene(PartyControllerEntity party, ServerPlayerEntity a, ServerPlayerEntity b, ItemStack coin) {
    }

    private static Scene scene(TestContext context) {
        TestBoards.floor(context, 8);
        ServerPlayerEntity a = TestPlayers.joined(context, "am", "A", GameMode.SURVIVAL, 6.5, 1, 6.5);
        ServerPlayerEntity b = TestPlayers.joined(context, "am", "B", GameMode.SURVIVAL, 6.5, 1, 7.5);
        TestCleanup.atEnd(context, () -> TestPlayers.removeIfOnline(context, a, b));
        PigEntity pawnA = TestBoards.token(context, new BlockPos(3, 1, 3), a.getUuid());
        PigEntity pawnB = TestBoards.token(context, new BlockPos(5, 1, 3), b.getUuid());
        pawnA.setAiDisabled(true);
        pawnB.setAiDisabled(true);
        PartyControllerEntity party = TestBoards.party(context, CONTROLLER, 2, pawnA.getUuid(), pawnB.getUuid());
        TestCleanup.atEnd(context, () -> {
            party.getBankItems().clear();
            TestCleanup.removeIf(context, CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        });
        return new Scene(party, a, b, party.getCurrency(PartyCurrency.COIN));
    }
}
