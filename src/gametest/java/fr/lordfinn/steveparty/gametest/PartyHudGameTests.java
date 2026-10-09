package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.StartRollsStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The live state of a party sent to its party HUDs ({@link PartyLiveData}): the current turn (roll, steps left), and
 * the standings (names, colours, owners, stars, coins, power-ups). Each test in a batch of its own: the mock players
 * share one name.
 */
public class PartyHudGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 5);

    private static PartyLiveData capture(TestContext context, PartyControllerEntity controller) {
        return PartyLiveData.capture(controller, context.getWorld());
    }

    /** The current turn: nothing rolled yet, then the roll, then the steps left while the token walks. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_hud_turn")
    public void liveDataFollowsTheCurrentTurn(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PigEntity pig = TestBoards.token(context, new BlockPos(2, 1, 2), player.getUuid());
            pig.setCustomName(Text.literal("Cochonou"));
            ((TokenizedEntityInterface) pig).steveparty$setTokenColor(0x3366FF);
            UUID absent = UUID.randomUUID();
            PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 1, pig.getUuid(), absent);
            TokenTurnPartyStep turn = (TokenTurnPartyStep) controller.getPartyData().getCurrentStep();

            PartyLiveData live = capture(context, controller);
            context.assertEquals(live.roll(), 0, "not rolled yet");
            context.assertEquals(live.stepsLeft(), 0, "no steps yet");
            context.assertTrue(!live.moving() && !live.shopping(), "waiting for the roll");
            context.assertEquals(live.absentSeconds(), -1, "its token is here");
            context.assertEquals(live.standings().size(), 2, "one standing per token");

            PartyLiveData.Standing first = live.standings().getFirst();
            context.assertEquals(first.token(), pig.getUuid(), "the turn order");
            context.assertEquals(first.tokenName(), "Cochonou", "the token's name");
            context.assertEquals(first.owner(), Optional.of(player.getUuid()), "its owner");
            context.assertEquals(first.ownerName(), player.getNameForScoreboard(), "its owner's name");
            context.assertEquals(first.color(), 0x3366FF, "the Tokenizer Wand's colour");
            context.assertTrue(first.online(), "its owner is connected");
            PartyLiveData.Standing second = live.standings().get(1);
            context.assertEquals(second.tokenName(), absent.toString().substring(0, 8), "an unloaded token: the start of its UUID");
            context.assertTrue(second.owner().isEmpty() && second.color() == -1, "unknown owner, no colour");

            DiceEntity dice = context.spawnEntity(ModEntities.DICE_ENTITY, new BlockPos(2, 2, 3));
            turn.onDiceRoll(dice, player.getUuid(), 5, controller);
            context.assertEquals(capture(context, controller).roll(), 5, "the roll");
            ((TokenizedEntityInterface) pig).steveparty$setNbSteps(3);
            live = capture(context, controller);
            context.assertEquals(live.stepsLeft(), 3, "steps left");
            context.assertTrue(live.moving(), "walking");
            context.assertTrue(live.sameAs(capture(context, controller)), "nothing changed: same state");
            ((TokenizedEntityInterface) pig).steveparty$setNbSteps(2);
            context.assertTrue(!live.sameAs(capture(context, controller)), "one step walked: a new state");

            // The next turn starts from nothing
            ((TokenizedEntityInterface) pig).steveparty$setNbSteps(0);
            controller.nextStep();
            live = capture(context, controller);
            context.assertEquals(live.roll(), 0, "next turn: not rolled");
            context.assertEquals(live.absentSeconds(), (int) (TokenTurnPartyStep.ABSENT_TURN_DELAY_TICKS / 20), "its token is absent: the countdown");
            context.setBlockState(CONTROLLER, Blocks.AIR);
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    /**
     * The standings: the party's stars and coins in the owner's inventory (the items picked in the controller's
     * settings, same components), and the power-ups they hold (Double and Triple dice, forged dice; a plain die is no
     * power-up), one stack per kind with the number held.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_hud_standings")
    public void standingsCountTheStarsCoinsAndPowerUps(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PigEntity pig = TestBoards.token(context, new BlockPos(2, 1, 2), player.getUuid());
            PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 1, pig.getUuid(), UUID.randomUUID());
            player.getInventory().setStack(10, new ItemStack(ModItems.PARTY_STAR, 2));
            player.getInventory().setStack(11, new ItemStack(ModItems.PARTY_STAR));
            player.getInventory().setStack(14, new ItemStack(Items.NETHER_STAR, 7));
            player.getInventory().setStack(12, new ItemStack(ModItems.COIN, 20));
            ItemStack renamed = new ItemStack(ModItems.COIN, 5);
            renamed.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Fake coin"));
            player.getInventory().setStack(13, renamed);

            PartyLiveData live = capture(context, controller);
            context.assertTrue(live.starItem().isOf(ModItems.PARTY_STAR) && live.coinItem().isOf(ModItems.COIN), "the default currencies, for the icons");
            PartyLiveData.Standing standing = live.standings().getFirst();
            context.assertEquals(standing.stars(), 3, "the Party Stars held (nether stars are no longer the party's star)");
            context.assertEquals(standing.coins(), 20, "the plain coins held (not the renamed ones)");
            context.assertEquals(live.standings().get(1).stars(), 0, "an unknown owner holds nothing");

            // Other items picked: the counts follow
            controller.setCurrency(PartyCurrency.COIN, renamed);
            context.assertEquals(capture(context, controller).standings().getFirst().coins(), 5, "the renamed nuggets are the coins now");

            ItemStack forged = DiceFacesComponent.createDie(List.of(new ItemStack(ModItems.DICE_FACES.get(1))));
            player.getInventory().setStack(0, new ItemStack(ModItems.DOUBLE_DICE, 2));
            player.getInventory().setStack(3, new ItemStack(ModItems.DOUBLE_DICE));
            player.getInventory().setStack(4, new ItemStack(ModItems.TRIPLE_DICE));
            player.getInventory().setStack(5, new ItemStack(ModItems.DEFAULT_DICE));
            player.getInventory().setStack(6, forged);
            player.getInventory().setStack(7, new ItemStack(Items.DIAMOND, 12));

            List<ItemStack> powerUps = capture(context, controller).standings().getFirst().powerUps();
            context.assertEquals(powerUps.size(), 3, "three kinds of power-ups: " + powerUps);
            context.assertTrue(powerUps.getFirst().isOf(ModItems.DOUBLE_DICE) && powerUps.getFirst().getCount() == 3,
                    "the Double dice, counted together, first (the most numerous)");
            context.assertTrue(powerUps.stream().anyMatch(stack -> stack.isOf(ModItems.TRIPLE_DICE)), "the Triple die");
            context.assertTrue(powerUps.stream().anyMatch(stack -> ItemStack.areItemsAndComponentsEqual(stack, forged)), "the forged die");
            context.assertTrue(!PartyLiveData.isPowerUp(new ItemStack(ModItems.DEFAULT_DICE)), "a plain die is no power-up");

            context.setBlockState(CONTROLLER, Blocks.AIR);
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    /** Ranks: the most stars first, the most coins between equal stars; the same stars and coins share their rank. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ranksByStarsThenCoinsAndShareTies(TestContext context) {
        List<PartyLiveData.Standing> standings = new ArrayList<>();
        int[][] held = {{3, 10}, {5, 0}, {3, 10}, {0, 50}, {3, 12}};
        for (int[] starsCoins : held)
            standings.add(new PartyLiveData.Standing(UUID.randomUUID(), "", Optional.empty(), "", -1, true, starsCoins[0], starsCoins[1], List.of()));
        int[] ranks = PartyLiveData.ranks(standings);
        context.assertTrue(Arrays.equals(ranks, new int[]{3, 1, 3, 5, 2}), "ranks " + Arrays.toString(ranks));
        context.complete();
    }

    /**
     * A pawn renamed during the party (a name tag): its new name is sent at once (not at the next step), and the party
     * remembers it for while the pawn is unloaded.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_hud_rename")
    public void renamedPawnIsSentAtOnce(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PigEntity pig = TestBoards.token(context, new BlockPos(2, 1, 2), player.getUuid());
            pig.setCustomName(Text.literal("Rose"));
            PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 1, pig.getUuid(), UUID.randomUUID());
            controller.addInterestedPlayer(player);
            controller.syncLiveData(context.getWorld());
            context.assertEquals(controller.getLastLiveData().standings().getFirst().tokenName(), "Rose", "its name");
            pig.setCustomName(Text.literal("Truffe"));
            context.assertEquals(controller.getLastLiveData().standings().getFirst().tokenName(), "Truffe", "renamed: sent at once");
            boolean remembered = controller.getPartyData().getSteps().stream().anyMatch(step -> step instanceof TokenTurnPartyStep turn
                    && pig.getUuid().equals(turn.getTokenUUID()) && turn.getTokenDisplayName(null).getString().equals("Truffe"));
            context.assertTrue(remembered, "remembered by the party for while it is unloaded");
            context.setBlockState(CONTROLLER, Blocks.AIR);
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    /**
     * The standings show the pawn's name: its own (a custom name), else its player's, else what it is.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_hud_pawn_name")
    public void standingsShowThePawnsName(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PigEntity named = TestBoards.token(context, new BlockPos(2, 1, 2), player.getUuid());
            named.setCustomName(Text.literal("Cochonou"));
            PigEntity plain = TestBoards.token(context, new BlockPos(3, 1, 2), player.getUuid());
            PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 1, named.getUuid(), plain.getUuid());
            context.assertEquals(PartyLiveData.standingOf(controller, context.getWorld(), named.getUuid()).tokenName(), "Cochonou", "its own name");
            context.assertEquals(PartyLiveData.standingOf(controller, context.getWorld(), plain.getUuid()).tokenName(), player.getNameForScoreboard(),
                    "no name of its own: its player's");
            PigEntity nobody = TestBoards.token(context, new BlockPos(4, 1, 2), null);
            context.assertEquals(PartyLiveData.pawnName(controller, context.getWorld(), nobody.getUuid(), nobody, ""), nobody.getName().getString(),
                    "no name, no player: what it is");
            context.assertTrue(PartyLiveData.standingOf(controller, context.getWorld(), named.getUuid()).bonuses().isEmpty(), "no bonuses yet");
            context.setBlockState(CONTROLLER, Blocks.AIR);
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    /**
     * Before the party's steps exist (the turn order rolls), the live state carries what the program will play, for
     * the turn bar's strip; once the steps are generated, they say it: nothing more is sent.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_hud_program")
    public void programIsSentUntilTheStepsExist(TestContext context) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PartyData data = new PartyData();
        data.setNbTurn(2);
        data.addToken(a);
        data.addToken(b);
        data.addStep(new StartRollsStep());
        data.addStep(new BasicGameGeneratorStep());
        controller.setPartyData(data);
        controller.nextStep();
        context.assertTrue(data.getCurrentStep() instanceof StartRollsStep, "rolling for the turn order");

        PartyLiveData live = capture(context, controller);
        List<PartyDashboardData.StepKind> kinds = live.program().steps().stream().map(PartyDashboardData.TimelineStep::kind).toList();
        context.assertEquals(kinds, List.of(PartyDashboardData.StepKind.TURNS, PartyDashboardData.StepKind.MINI_GAME,
                PartyDashboardData.StepKind.TURNS, PartyDashboardData.StepKind.MINI_GAME, PartyDashboardData.StepKind.END),
                "the default party of two rounds, after the turn order rolls");
        context.assertEquals(live.program().steps().get(2).round(), 2, "the rounds");
        context.assertEquals(live.program().more(), 0, "all of it");
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        PartyLiveData.PACKET_CODEC.encode(buf, live);
        PartyLiveData received = PartyLiveData.PACKET_CODEC.decode(buf);
        context.assertEquals(received.program(), live.program(), "sent to the clients");
        context.assertTrue(live.sameAs(received), "the same state");

        // A long program: its first steps, the others counted
        data.setNbTurn(40);
        live = capture(context, controller);
        context.assertEquals(live.program().steps().size(), PartyLiveData.MAX_PROGRAM_STEPS, "a long program: its first steps");
        context.assertEquals(live.program().steps().size() + live.program().more(), 40 * 2 + 1, "the others are counted");
        context.assertTrue(!live.sameAs(received), "another program: a new state");

        // The turns exist: the program is not sent any more
        context.assertTrue(capture(context, TestBoards.party(context, CONTROLLER, 1, a, b)).program().steps().isEmpty(), "a turn: the steps say what comes");
        context.setBlockState(CONTROLLER, Blocks.AIR);
        context.complete();
    }

    /**
     * The live state is sent to the interested players only when it changed; the steps' packet carries it too, up to
     * date.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_hud_sync")
    public void liveStateIsSentOnlyWhenItChanged(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            PigEntity pig = TestBoards.token(context, new BlockPos(2, 1, 2), player.getUuid());
            PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 1, pig.getUuid(), UUID.randomUUID());
            controller.addInterestedPlayer(player);
            controller.syncLiveData(context.getWorld());
            PartyLiveData sent = controller.getLastLiveData();
            context.assertTrue(sent != PartyLiveData.EMPTY && sent.standings().size() == 2, "sent once the party runs");
            controller.syncLiveData(context.getWorld());
            context.assertTrue(controller.getLastLiveData() == sent, "nothing changed: nothing sent");
            ((TokenizedEntityInterface) pig).steveparty$setNbSteps(4);
            controller.syncLiveData(context.getWorld());
            context.assertEquals(controller.getLastLiveData().stepsLeft(), 4, "a change: sent");
            ((TokenizedEntityInterface) pig).steveparty$setNbSteps(0);
            controller.nextStep();
            context.assertEquals(controller.getLastLiveData().stepsLeft(), 0, "a new step: sent with it, up to date");
            context.setBlockState(CONTROLLER, Blocks.AIR);
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }
}
