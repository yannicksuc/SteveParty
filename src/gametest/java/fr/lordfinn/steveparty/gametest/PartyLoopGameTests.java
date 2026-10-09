package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.*;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** Party loop: bells, waiting bells, program cards, controller phase, piggy bank (the podiums: PodiumGameTests). */
public class PartyLoopGameTests implements SteveGameTest {

    private static PartyControllerEntity placeController(TestContext context, BlockPos pos) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_CONTROLLER);
        return context.getBlockEntity(pos);
    }

    private static PartyBellBlockEntity placeBell(TestContext context, BlockPos pos, PartyMoment moment, boolean waiting) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_BELL.getDefaultState()
                .with(PartyBellBlock.MOMENT, moment).with(PartyBellBlock.WAITING, waiting));
        return context.getBlockEntity(pos);
    }

    private static int comparator(TestContext context, BlockPos pos) {
        BlockState state = context.getBlockState(pos);
        return state.getComparatorOutput(context.getWorld(), context.getAbsolutePos(pos));
    }

    /** Two turns of unloaded tokens (they wait for their tokens: nothing moves on by itself). */
    private static PartyData twoTurns(UUID a, UUID b) {
        PartyData data = new PartyData();
        data.addToken(a);
        data.addToken(b);
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(a, null));
        data.addStep(new TokenTurnPartyStep(b, null));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(a, b))));
        return data;
    }

    /** A bell pulses at its moment, and its comparator gives the value of the moment (rank of the token). */
    // Each bell test in a batch of its own: a bell listens to the closest running controller within 64 blocks, so a
    // waiting bell of another test whose controller is already gone would pause this test's party
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_bell_pulse")
    public void bellPulsesAtItsMoment(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        BlockPos bellPos = new BlockPos(5, 1, 2);
        placeBell(context, bellPos, PartyMoment.TURN_START, false);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        controller.setPartyData(twoTurns(a, b));
        controller.nextStep();
        context.assertTrue(!context.getBlockState(bellPos).get(PartyBellBlock.POWERED), "silent before the turns");
        controller.nextStep();
        context.assertTrue(context.getBlockState(bellPos).get(PartyBellBlock.POWERED), "rings at the turn start");
        context.assertEquals(comparator(context, bellPos), 1, "rank of the first token");
        context.assertEquals(comparator(context, pos), PartyControllerEntity.PHASE_TURN, "controller phase: turn");
        context.waitAndRun(PartyBellBlock.PULSE_TICKS + 2, () -> {
            context.assertTrue(!context.getBlockState(bellPos).get(PartyBellBlock.POWERED), "pulse over");
            controller.nextStep();
            context.assertEquals(comparator(context, bellPos), 2, "rank of the second token");
            context.removeBlock(pos);
            context.complete();
        });
    }

    /** A waiting bell pauses the party at its moment until it receives a redstone signal. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "party_bell_waiting")
    public void waitingBellPausesUntilSignal(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        BlockPos bellPos = new BlockPos(5, 1, 2);
        placeBell(context, bellPos, PartyMoment.TURN_END, true);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PartyData data = twoTurns(a, b);
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        controller.nextStep(); // end of the turn of a: the bell waits
        context.assertTrue(data.getCurrentStep() instanceof EventPartyStep event && event.isTransition() && event.isWaitingForBell(),
                "waiting on a transition step");
        context.assertEquals(data.getSteps().size(), 5, "transition step inserted");
        context.assertEquals(comparator(context, pos), PartyControllerEntity.PHASE_WAITING, "controller phase: waiting");
        context.waitAndRun(PartyBellBlock.PULSE_TICKS + 4, () -> {
            context.assertTrue(data.getCurrentStep() instanceof EventPartyStep, "still waiting");
            context.setBlockState(bellPos.east(), Blocks.REDSTONE_BLOCK);
            context.waitAndRun(3, () -> {
                context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && b.equals(turn.getTokenUUID()),
                        "turn of b after the signal");
                context.assertEquals(data.getSteps().size(), 4, "transition step removed");
                context.removeBlock(pos);
                context.complete();
            });
        });
    }

    /** A waiting bell that is broken does not block the party. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "party_bell_broken")
    public void brokenWaitingBellReleasesTheParty(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        BlockPos bellPos = new BlockPos(5, 1, 2);
        placeBell(context, bellPos, PartyMoment.TURN_START, true);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PartyData data = twoTurns(a, b);
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        context.assertTrue(data.getCurrentStep() instanceof EventPartyStep, "waiting before the turn");
        context.removeBlock(bellPos);
        context.waitAndRun(45, () -> {
            context.assertTrue(data.getCurrentStep() instanceof TokenTurnPartyStep turn && a.equals(turn.getTokenUUID()),
                    "turn of a once no bell waits any more");
            context.removeBlock(pos);
            context.complete();
        });
    }

    private static ItemStack card(PartyCardItem.CardType type, int count) {
        return new ItemStack(switch (type) {
            case TURNS -> ModItems.PARTY_CARD_TURNS;
            case MINIGAME -> ModItems.PARTY_CARD_MINIGAME;
            case EVENT -> ModItems.PARTY_CARD_EVENT;
            case REPEAT -> ModItems.PARTY_CARD_REPEAT;
            case SEQUENCE_START -> ModItems.PARTY_CARD_SEQUENCE_START;
        }, count);
    }

    /** Repeat cards replay the cards since the previous repeat card; an empty program is the default party. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void programCardsExpand(TestContext context) {
        List<BasicGameGeneratorStep.ExpandedCard> expanded = BasicGameGeneratorStep.expand(List.of(
                card(PartyCardItem.CardType.TURNS, 1), card(PartyCardItem.CardType.MINIGAME, 1),
                card(PartyCardItem.CardType.REPEAT, 3), card(PartyCardItem.CardType.EVENT, 2),
                card(PartyCardItem.CardType.TURNS, 1), card(PartyCardItem.CardType.REPEAT, 2)), 10);
        List<PartyCardItem.CardType> types = expanded.stream().map(BasicGameGeneratorStep.ExpandedCard::type).toList();
        context.assertEquals(types, List.of(
                PartyCardItem.CardType.TURNS, PartyCardItem.CardType.MINIGAME,
                PartyCardItem.CardType.TURNS, PartyCardItem.CardType.MINIGAME,
                PartyCardItem.CardType.TURNS, PartyCardItem.CardType.MINIGAME,
                PartyCardItem.CardType.EVENT, PartyCardItem.CardType.TURNS,
                PartyCardItem.CardType.EVENT, PartyCardItem.CardType.TURNS), "expanded program");
        context.assertEquals(expanded.get(6).count(), 2, "event channel");
        context.assertEquals(BasicGameGeneratorStep.expand(List.of(), 3).size(), 6, "default: 3 x (turns, mini-game)");
        context.complete();
    }

    private static List<PartyCardItem.CardType> types(List<ItemStack> program) {
        return BasicGameGeneratorStep.expand(program, 10).stream().map(BasicGameGeneratorStep.ExpandedCard::type).toList();
    }

    /**
     * A Repeat card repeats from the nearest Sequence start card on its left: what is before that card is played
     * once. Without one it repeats from the previous Repeat card or the start of the program, as it always did.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aSequenceStartCardSaysWhereARepeatStartsFrom(TestContext context) {
        PartyCardItem.CardType turns = PartyCardItem.CardType.TURNS, game = PartyCardItem.CardType.MINIGAME, event = PartyCardItem.CardType.EVENT,
                repeat = PartyCardItem.CardType.REPEAT, start = PartyCardItem.CardType.SEQUENCE_START;
        // Without a Sequence start: from the beginning
        context.assertEquals(types(List.of(card(event, 1), card(turns, 1), card(game, 1), card(repeat, 3))),
                List.of(event, turns, game, event, turns, game, event, turns, game), "no sequence start: everything is repeated");
        // With one: what is before it is played once
        context.assertEquals(types(List.of(card(event, 1), card(start, 1), card(turns, 1), card(game, 1), card(repeat, 3))),
                List.of(event, turns, game, turns, game, turns, game), "the event before the sequence start is played once");
        // A stack of several Sequence start cards is the same as one
        context.assertEquals(types(List.of(card(event, 1), card(start, 5), card(turns, 1), card(repeat, 2))),
                List.of(event, turns, turns), "a stack of sequence starts: one sequence start");
        // Two loops, each with its start: independent
        context.assertEquals(types(List.of(card(start, 1), card(turns, 1), card(repeat, 3), card(start, 1), card(game, 1), card(repeat, 2))),
                List.of(turns, turns, turns, game, game), "two independent loops");
        // A second Repeat without a start of its own repeats since the previous Repeat, as before
        context.assertEquals(types(List.of(card(start, 1), card(turns, 1), card(repeat, 2), card(game, 1), card(repeat, 2))),
                List.of(turns, turns, game, game), "a repeat after a repeat loops its own cards");
        // A start between a Repeat and the next one: the cards between that Repeat and the start are played once
        context.assertEquals(types(List.of(card(turns, 1), card(repeat, 2), card(event, 1), card(start, 1), card(game, 1), card(repeat, 2))),
                List.of(turns, turns, event, game, game), "the nearest sequence start on its left");
        // A Sequence start without Repeat after it does nothing
        context.assertEquals(types(List.of(card(turns, 1), card(start, 1), card(game, 1))), List.of(turns, game), "a sequence start alone changes nothing");
        context.assertEquals(types(List.of(card(turns, 1), card(game, 1), card(repeat, 2), card(start, 1))), List.of(turns, game, turns, game),
                "nor at the end of the program");
        // Alone in the program, it is still the default party
        context.assertEquals(BasicGameGeneratorStep.expand(List.of(card(start, 1)), 4), BasicGameGeneratorStep.expand(List.of(), 4),
                "only a sequence start: the default party");

        // What the dashboard says of a program follows its sequences
        BasicGameGeneratorStep.Summary summary = BasicGameGeneratorStep.summary(
                List.of(card(event, 1), card(start, 1), card(turns, 1), card(game, 1), card(repeat, 5)), 10);
        context.assertEquals(summary, new BasicGameGeneratorStep.Summary(5, 5, 1), "5 turns, 5 mini-games, 1 event");
        context.assertEquals(BasicGameGeneratorStep.summary(List.of(card(event, 1), card(turns, 1), card(game, 1), card(repeat, 5)), 10),
                new BasicGameGeneratorStep.Summary(5, 5, 5), "without the sequence start the event is repeated too");
        context.assertEquals(BasicGameGeneratorStep.summary(List.of(), 10), new BasicGameGeneratorStep.Summary(10, 10, 0), "the default party: 10 rounds");
        context.complete();
    }

    /** The generator builds the party from the program cards of the controller. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void generatorFollowsTheProgram(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        controller.getProgram().setStack(0, card(PartyCardItem.CardType.TURNS, 1));
        controller.getProgram().setStack(1, card(PartyCardItem.CardType.EVENT, 4));
        controller.getProgram().setStack(2, card(PartyCardItem.CardType.REPEAT, 2));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(a);
        data.addToken(b);
        data.addStep(new BasicGameGeneratorStep());
        controller.setPartyData(data);
        controller.nextStep();
        // generator, (a, b, event) x 2, end
        List<PartyStep> steps = data.getSteps();
        context.assertEquals(steps.size(), 8, "steps generated");
        context.assertTrue(steps.get(3) instanceof EventPartyStep event && event.getChannel() == 4 && !event.isTransition(), "event card");
        context.assertTrue(steps.get(6) instanceof EventPartyStep, "repeated event card");
        context.assertTrue(steps.get(7) instanceof EndPartyStep, "end");
        context.assertEquals(data.getRoundAt(4), 2, "second round");
        context.removeBlock(pos);
        context.complete();
    }

    /** The comparator of the controller: 0 idle, 5 once the party is over. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void controllerComparatorGivesThePhase(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        context.assertEquals(comparator(context, pos), PartyControllerEntity.PHASE_IDLE, "idle");
        UUID a = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(a);
        data.addStep(new PartyStep());
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(a))));
        controller.setPartyData(data);
        controller.nextStep();
        context.assertEquals(comparator(context, pos), PartyControllerEntity.PHASE_PREPARING, "preparing");
        controller.nextStep();
        context.assertEquals(comparator(context, pos), PartyControllerEntity.PHASE_END, "over");
        context.removeBlock(pos);
        context.complete();
    }

    private static ItemStack cartridge(TestContext context, BlockPos chestPos, int selection, ItemStack... items) {
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        cartridge.set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(items)));
        cartridge.set(ModComponents.INVENTORY_POS, context.getAbsolutePos(chestPos));
        cartridge.set(ModComponents.SELECTION_STATE, selection);
        return cartridge;
    }

    /** "All items" is all-or-nothing: a star is only sold if the player can pay all its coins. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void piggyBankSellsAllOrNothing(TestContext context) {
        BlockPos chestPos = new BlockPos(1, 1, 1);
        context.setBlockState(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = context.getBlockEntity(chestPos);
        chest.setStack(0, new ItemStack(ModItems.PARTY_STAR, 1));
        ItemStack price = new ItemStack(Items.GOLD_NUGGET, 20);
        price.set(ModComponents.IS_NEGATIVE, true);
        ItemStack cartridge = cartridge(context, chestPos, 1, new ItemStack(ModItems.PARTY_STAR, 1), price);
        ServerPlayerEntity player = TestPlayers.mock(context);
        player.getInventory().insertStack(new ItemStack(Items.GOLD_NUGGET, 10));
        int[] cycle = {0};
        context.assertTrue(!CartridgeTransfers.apply(context.getWorld(), cartridge, player, () -> cycle[0], i -> cycle[0] = i),
                "not enough coins: refused");
        context.assertEquals(CartridgeTransfers.countMatching(new ItemStack(Items.GOLD_NUGGET), player.getInventory()), 10, "coins kept");
        context.assertEquals(chest.getStack(0).getCount(), 1, "star kept");
        player.getInventory().insertStack(new ItemStack(Items.GOLD_NUGGET, 15));
        context.assertTrue(CartridgeTransfers.apply(context.getWorld(), cartridge, player, () -> cycle[0], i -> cycle[0] = i),
                "bought");
        context.assertEquals(CartridgeTransfers.countMatching(new ItemStack(Items.GOLD_NUGGET), player.getInventory()), 5, "20 coins paid");
        context.assertEquals(CartridgeTransfers.countMatching(new ItemStack(ModItems.PARTY_STAR), player.getInventory()), 1, "star received");
        context.assertEquals(CartridgeTransfers.countMatching(new ItemStack(Items.GOLD_NUGGET), chest), 20, "coins in the chest");
        context.complete();
    }
}
