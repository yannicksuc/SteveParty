package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.*;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
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
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

/** Party loop: bells, waiting bells, program cards, controller phase, piggy bank, podium. */
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
        chest.setStack(0, new ItemStack(ModItems.POWER_STAR, 1));
        ItemStack price = new ItemStack(Items.GOLD_NUGGET, 20);
        price.set(ModComponents.IS_NEGATIVE, true);
        ItemStack cartridge = cartridge(context, chestPos, 1, new ItemStack(ModItems.POWER_STAR, 1), price);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
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
        context.assertEquals(CartridgeTransfers.countMatching(new ItemStack(ModItems.POWER_STAR), player.getInventory()), 1, "star received");
        context.assertEquals(CartridgeTransfers.countMatching(new ItemStack(Items.GOLD_NUGGET), chest), 20, "coins in the chest");
        context.complete();
    }

    /** Winners designated by a redstone signal on a podium: on the podium, team, rank, else the nearest. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void podiumSignalDesignatesWinners(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        List<UUID> players = List.of(a, b, c);
        TeamDisposition teams = new TeamDisposition(Set.of(a), Set.of(b, c));
        context.assertEquals(PodiumBlockEntity.designateWinners(players, List.of(c), teams, 1, a), List.of(c), "on the podium wins");
        context.assertEquals(PodiumBlockEntity.designateWinners(players, List.of(), null, 2, a), List.of(b), "rank 2");
        context.assertEquals(PodiumBlockEntity.designateWinners(players, List.of(), null, 4, c), List.of(c), "more than the players: nearest");
        context.assertEquals(PodiumBlockEntity.designateWinners(players, List.of(), teams, 1, c), List.of(a), "team A");
        context.assertEquals(PodiumBlockEntity.designateWinners(players, List.of(), teams, 2, a), List.of(b, c), "team B");
        context.assertEquals(PodiumBlockEntity.designateWinners(players, List.of(), teams, 9, b), List.of(b, c), "nearest and his team");
        context.assertEquals(PodiumBlockEntity.designateWinners(players, List.of(), null, 9, null), List.of(), "nobody");
        context.complete();
    }

    private record PlayedMiniGame(PartyControllerEntity controller, MiniGamePartyStep miniGame, PartyData data) {
    }

    /** A controller at {@code pos} whose mini-game is being played by {@code player}. */
    private static PlayedMiniGame playedMiniGame(TestContext context, BlockPos pos, ServerPlayerEntity player) {
        PartyControllerEntity controller = placeController(context, pos);
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

        return new PlayedMiniGame(controller, miniGame, data);
    }

    /** A participant stepping on a podium ("first arrived") wins the mini-game being played. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void podiumEndsTheMiniGame(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        PlayedMiniGame played = playedMiniGame(context, pos, player);
        PartyControllerEntity controller = played.controller();
        MiniGamePartyStep miniGame = played.miniGame();
        PartyData data = played.data();

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

    /** Stacked podiums make one column: the top one is stood on, the bottom one keeps the settings and the banner. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void podiumColumn(TestContext context) {
        BlockPos bottom = new BlockPos(2, 1, 2);
        context.setBlockState(bottom, ModBlocks.GOLD_PODIUM.getDefaultState().with(PodiumBlock.FACING, Direction.EAST).with(PodiumBlock.FULL, true));
        context.assertTrue(context.getBlockState(bottom).get(PodiumBlock.TOP), "alone: its own top");
        context.setBlockState(bottom.up(), ModBlocks.GOLD_PODIUM.getDefaultState().with(PodiumBlock.FACING, Direction.EAST));
        context.assertFalse(context.getBlockState(bottom).get(PodiumBlock.TOP), "covered: no longer the top");
        context.assertTrue(context.getBlockState(bottom.up()).get(PodiumBlock.TOP), "the slab is the top");
        BlockPos absBottom = context.getAbsolutePos(bottom);
        context.assertEquals(PodiumBlock.topOf(context.getWorld(), absBottom), absBottom.up(), "top of the column");
        PodiumBlockEntity master = context.getBlockEntity(bottom);
        context.assertTrue(PodiumBlock.master(context.getWorld(), absBottom.up()) == master, "the bottom block keeps the settings");
        TileStampComponent stamp = TileStampComponent.of(new byte[256], DyeColor.BLUE);
        master.setBannerStamp(stamp);
        context.assertEquals(PodiumBlock.master(context.getWorld(), absBottom.up()).getBannerStamp(), stamp, "banner of the column");

        // Someone on the top: the whole column pulses
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        BlockPos top = absBottom.up();
        player.setPosition(top.getX() + 0.5, top.getY() + 0.6, top.getZ() + 0.5);
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(bottom).get(PodiumBlock.POWERED), "bottom powered");
            context.assertTrue(context.getBlockState(bottom.up()).get(PodiumBlock.POWERED), "top powered");
            context.assertEquals(context.getWorld().getEmittedRedstonePower(absBottom, Direction.EAST), 15, "signal out of the bottom's side");
            context.assertEquals(comparator(context, bottom), 1, "one player on it");
            context.removeBlock(bottom.up());
            context.assertTrue(context.getBlockState(bottom).get(PodiumBlock.TOP), "uncovered: the top again");
        });
    }

    /**
     * A column looks like one piece: the plinth at its foot only, and its banner hanging over one block of height from
     * its top (across the top slab and the block under it), whatever it is made of; a slab alone has a label.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void podiumColumnBanner(TestContext context) {
        BlockPos bottom = new BlockPos(2, 1, 2);
        BlockState slab = ModBlocks.GOLD_PODIUM.getDefaultState();
        BlockState full = ModBlocks.SILVER_PODIUM.getDefaultState().with(PodiumBlock.FULL, true);

        placePodium(context, bottom, slab);
        context.assertFalse(PodiumBlock.bannerHangs(context.getBlockState(bottom)), "a slab alone: a label");
        placePodium(context, bottom, full);
        context.assertTrue(PodiumBlock.bannerHangs(context.getBlockState(bottom)), "a full block: the banner hangs");
        context.assertTrue(context.getBlockState(bottom).get(PodiumBlock.BASE), "alone: its plinth");

        // Full block + slab of another kind: the slab's banner goes on over the block under it
        placePodium(context, bottom.up(), slab);
        BlockState top = context.getBlockState(bottom.up());
        context.assertTrue(top.get(PodiumBlock.TOP) && !top.get(PodiumBlock.BASE), "the slab rests on the column");
        context.assertTrue(PodiumBlock.bannerHangs(top), "a slab on a full block: the banner hangs");
        context.assertEquals(context.getBlockState(bottom).get(PodiumBlock.BANNER_TAIL), PodiumBlock.BannerTail.GOLD, "lower half of the gold banner");
        context.assertTrue(context.getBlockState(bottom).get(PodiumBlock.BASE), "the foot of the column keeps its plinth");

        // Two full blocks: the banner is on the upper one only, which has no plinth
        placePodium(context, bottom.up(), full);
        context.assertEquals(context.getBlockState(bottom).get(PodiumBlock.BANNER_TAIL), PodiumBlock.BannerTail.NONE, "no banner under a full block");
        context.assertFalse(context.getBlockState(bottom.up()).get(PodiumBlock.BASE), "no plinth on a block resting on a podium");

        // Two full blocks + slab: the banner is on the slab and the block under it, not lower
        placePodium(context, bottom.up(2), slab);
        context.assertEquals(context.getBlockState(bottom.up()).get(PodiumBlock.BANNER_TAIL), PodiumBlock.BannerTail.GOLD, "under the top slab");
        context.assertEquals(context.getBlockState(bottom).get(PodiumBlock.BANNER_TAIL), PodiumBlock.BannerTail.NONE, "not lower");
        context.assertFalse(context.getBlockState(bottom.up()).get(PodiumBlock.TOP), "covered");
        context.removeBlock(bottom.up(2));
        context.assertEquals(context.getBlockState(bottom.up()).get(PodiumBlock.BANNER_TAIL), PodiumBlock.BannerTail.NONE, "slab removed");
        context.assertTrue(context.getBlockState(bottom.up()).get(PodiumBlock.TOP), "the top again");

        // A slab under another podium leaves a gap: the one above stands on its own
        placePodium(context, bottom.up(), slab);
        placePodium(context, bottom.up(2), slab);
        context.assertTrue(context.getBlockState(bottom.up(2)).get(PodiumBlock.BASE), "nothing full under it");
        context.assertFalse(PodiumBlock.bannerHangs(context.getBlockState(bottom.up(2))), "a label");
        context.complete();
    }

    /** Like /setblock: the block takes its place in the column, its neighbours follow. */
    private static void placePodium(TestContext context, BlockPos pos, BlockState state) {
        BlockPos abs = context.getAbsolutePos(pos);
        context.getWorld().setBlockState(abs, net.minecraft.block.Block.postProcessState(state, context.getWorld(), abs));
    }

    /** Silver and bronze give their place without ending the mini-game; the gold podium ends it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void podiumPlaces(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        PlayedMiniGame played = playedMiniGame(context, pos, player);
        BlockPos silver = new BlockPos(4, 1, 1), gold = new BlockPos(4, 1, 4);
        context.setBlockState(silver, ModBlocks.SILVER_PODIUM);
        context.setBlockState(gold, ModBlocks.GOLD_PODIUM);
        BlockPos abs = context.getAbsolutePos(silver);
        player.setPosition(abs.getX() + 0.5, abs.getY() + 0.6, abs.getZ() + 0.5);
        context.waitAndRun(6, () -> {
            context.assertTrue(played.miniGame().isPlaying(), "the silver place doesn't end it");
            BlockPos absGold = context.getAbsolutePos(gold);
            player.setPosition(absGold.getX() + 0.5, absGold.getY() + 0.6, absGold.getZ() + 0.5);
            context.waitAndRun(6, () -> {
                context.assertEquals(played.miniGame().getPhase(), MiniGamePartyStep.Phase.FINISHED, "gold ends it");
                context.assertEquals(played.miniGame().getWinners(), List.of(player.getUuid()), "the player won");
                context.removeBlock(pos);
                context.complete();
            });
        });
    }
}
