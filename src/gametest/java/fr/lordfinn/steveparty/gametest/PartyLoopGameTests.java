package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.*;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import fr.lordfinn.steveparty.items.custom.teleportation_books.TeleportingTarget;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/** Party loop: bells, waiting bells, program cards, controller phase, ranking, piggy bank, mini-game pads, podium. */
public class PartyLoopGameTests implements FabricGameTest {

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
    @GameTest(templateName = EMPTY_STRUCTURE)
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
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
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
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
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

    /** The generator builds the party from the program cards of the controller. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void generatorFollowsTheProgram(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        controller.getSettings().setStack(PartyControllerEntity.PROGRAM_FIRST_SLOT, card(PartyCardItem.CardType.TURNS, 1));
        controller.getSettings().setStack(PartyControllerEntity.PROGRAM_FIRST_SLOT + 1, card(PartyCardItem.CardType.EVENT, 4));
        controller.getSettings().setStack(PartyControllerEntity.PROGRAM_FIRST_SLOT + 2, card(PartyCardItem.CardType.REPEAT, 2));
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

    /** Ranking: stars first, then coins, counted in the inventories with the items chosen in the controller. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rankingCountsStarsThenCoins(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        PartyControllerEntity controller = placeController(context, pos);
        controller.getSettings().setStack(PartyControllerEntity.SLOT_COIN, new ItemStack(Items.GOLD_NUGGET));
        controller.getSettings().setStack(PartyControllerEntity.SLOT_STAR, new ItemStack(ModItems.POWER_STAR));
        ServerPlayerEntity rich = context.createMockCreativeServerPlayerInWorld();
        ServerPlayerEntity starry = context.createMockCreativeServerPlayerInWorld();
        rich.getInventory().insertStack(new ItemStack(Items.GOLD_NUGGET, 50));
        starry.getInventory().insertStack(new ItemStack(Items.GOLD_NUGGET, 3));
        starry.getInventory().insertStack(new ItemStack(ModItems.POWER_STAR, 1));
        UUID tokenRich = UUID.randomUUID(), tokenStarry = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(tokenRich);
        data.addToken(tokenStarry);
        data.addStep(new TokenTurnPartyStep(tokenRich, rich.getUuid()));
        data.addStep(new TokenTurnPartyStep(tokenStarry, starry.getUuid()));
        controller.setPartyData(data);
        List<PartyData.ScoreEntry> ranking = controller.computeRanking();
        context.assertEquals(ranking.size(), 2, "two players");
        context.assertEquals(ranking.get(0).player(), starry.getUuid(), "one star beats 50 coins");
        context.assertEquals(ranking.get(0).coins(), 3, "coins counted");
        context.assertEquals(ranking.get(1).coins(), 50, "coins of the second");
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
        chest.setStack(0, new ItemStack(ModItems.POWER_STAR, 1));
        ItemStack price = new ItemStack(Items.GOLD_NUGGET, 20);
        price.set(ModComponents.IS_NEGATIVE, true);
        ItemStack cartridge = cartridge(context, chestPos, 1, new ItemStack(ModItems.POWER_STAR, 1), price);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.getInventory().insertStack(new ItemStack(Items.GOLD_NUGGET, 10));
        int[] cycle = {0};
        context.assertTrue(!CartridgeTransfers.apply(context.getWorld(), cartridge, player, () -> cycle[0], i -> cycle[0] = i),
                "not enough coins: refused");
        context.assertEquals(PartyControllerEntity.countItems(player, new ItemStack(Items.GOLD_NUGGET)), 10, "coins kept");
        context.assertEquals(chest.getStack(0).getCount(), 1, "star kept");
        player.getInventory().insertStack(new ItemStack(Items.GOLD_NUGGET, 15));
        context.assertTrue(CartridgeTransfers.apply(context.getWorld(), cartridge, player, () -> cycle[0], i -> cycle[0] = i),
                "bought");
        context.assertEquals(PartyControllerEntity.countItems(player, new ItemStack(Items.GOLD_NUGGET)), 5, "20 coins paid");
        context.assertEquals(PartyControllerEntity.countItems(player, new ItemStack(ModItems.POWER_STAR)), 1, "star received");
        context.assertEquals(CartridgeTransfers.countMatching(new ItemStack(Items.GOLD_NUGGET), chest), 20, "coins in the chest");
        context.complete();
    }

    /** Arrival pads: own team first (team A = smaller team = pads with the smaller capacity), then any player seats. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void miniGamePadsFollowTheTeams(TestContext context) {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(), p4 = UUID.randomUUID();
        BlockPos padSmall = new BlockPos(0, 0, 0), padBig = new BlockPos(1, 0, 0), padAny = new BlockPos(2, 0, 0);
        Map<BlockPos, List<TeleportingTarget>> pads = new LinkedHashMap<>();
        // The books say "team B" for the small side: capacities decide, like when the mini-game was chosen
        pads.put(padSmall, List.of(new TeleportingTarget(TeleportingTarget.Group.PLAYER_TEAM_B, 1, 0)));
        pads.put(padBig, List.of(new TeleportingTarget(TeleportingTarget.Group.PLAYER_TEAM_A, 2, 0)));
        pads.put(padAny, List.of(new TeleportingTarget(TeleportingTarget.Group.PLAYERS, 0, 0)));
        TeamDisposition disposition = new TeamDisposition(Set.of(p1), Set.of(p2, p3));
        Map<UUID, BlockPos> assignments = MiniGameTeleports.assign(pads, disposition, List.of(p1, p2, p3, p4), Map.of());
        context.assertEquals(assignments.get(p1), padSmall, "smaller team on the smaller pads");
        context.assertEquals(assignments.get(p2), padBig, "bigger team");
        context.assertEquals(assignments.get(p3), padBig, "bigger team, second seat");
        context.assertEquals(assignments.get(p4), padAny, "not in a team: any player seat");
        // A new player: the taken seats stay taken
        UUID p5 = UUID.randomUUID();
        Map<UUID, BlockPos> more = MiniGameTeleports.assign(pads, new TeamDisposition(Set.of(p1, p5), Set.of(p2, p3)),
                List.of(p5), assignments);
        context.assertEquals(more.get(p5), padAny, "team pad full: any player seat");
        context.complete();
    }

    /** A participant stepping on a podium ("first arrived") wins the mini-game being played. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void podiumEndsTheMiniGame(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        PartyControllerEntity controller = placeController(context, pos);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        UUID token = UUID.randomUUID();
        NbtCompound miniGameNbt = new NbtCompound();
        miniGameNbt.putString("Type", PartyStepType.MINI_GAME.name());
        miniGameNbt.putString("Status", PartyStep.Status.IN_PROGRESS.name());
        miniGameNbt.putString("Phase", MiniGamePartyStep.Phase.PLAYING.name());
        miniGameNbt.putBoolean("MiniGameChosen", true);
        NbtList participants = new NbtList();
        participants.add(NbtString.of(player.getUuid().toString()));
        miniGameNbt.put("Participants", participants);
        MiniGamePartyStep miniGame = (MiniGamePartyStep) PartyStepFactory.get(miniGameNbt);
        PartyData data = new PartyData();
        data.addToken(token);
        data.addStep(new PartyStep());
        data.addStep(miniGame);
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token))));
        data.setStepIndex(1);
        controller.setPartyData(data);
        context.assertTrue(miniGame.isPlaying(), "mini-game being played");

        BlockPos podiumPos = new BlockPos(4, 1, 4);
        context.setBlockState(podiumPos, ModBlocks.PODIUM);
        PodiumBlockEntity podium = context.getBlockEntity(podiumPos);
        context.assertEquals(podium.getMode(), PodiumBlockEntity.Mode.FIRST_ARRIVED, "default mode");
        BlockPos abs = context.getAbsolutePos(podiumPos);
        player.setPosition(abs.getX() + 0.5, abs.getY() + 0.75, abs.getZ() + 0.5);
        context.waitAndRun(6, () -> {
            context.assertEquals(miniGame.getPhase(), MiniGamePartyStep.Phase.FINISHED, "finished");
            context.assertEquals(miniGame.getWinners(), List.of(player.getUuid()), "the player won");
            context.assertEquals(controller.getLastWinners(), List.of(player.getUuid()), "winners remembered");
            context.waitAndRun(70, () -> {
                context.assertTrue(data.isAtEnd(), "the party went on");
                context.removeBlock(pos);
                context.complete();
            });
        });
    }
}
