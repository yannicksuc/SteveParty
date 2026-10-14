package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.EndPipe;
import fr.lordfinn.steveparty.blocks.custom.PartyController.EventTimeOut;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EventPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGameMode;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGameModeChoice;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepFactory;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDispositionGenerator;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeSolid;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.gametest.kit.TestWait;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.EventCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.MiniGameCartridgeItem;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Event (card and space): a time out of the party, the players out of its pipes and back at its end, ended by its
 * hourglass or a Step Controller. The Mini-game space: a mini-game slipped in after the turn, its teams by its mode.
 */
public class EventSpaceGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 5), SPACE = new BlockPos(3, 1, 2);

    private record Board(PartyControllerEntity controller, PigEntity a, PigEntity b, BoardSpaceBlockEntity space) {
    }

    /** Two pawns (a's of {@code owner}), a space holding {@code cartridge}, a party of 2 rounds at a's first turn. */
    private static Board board(TestContext context, ItemStack cartridge, UUID owner) {
        TestBoards.floor(context, 7);
        PigEntity a = TestBoards.token(context, new BlockPos(1, 1, 1), owner);
        PigEntity b = TestBoards.token(context, new BlockPos(5, 1, 1), null);
        a.setAiDisabled(true);
        b.setAiDisabled(true);
        context.setBlockState(SPACE, ModBlocks.TILE);
        BoardSpaceBlockEntity space = context.getBlockEntity(SPACE);
        space.setStack(0, cartridge);
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 2, a.getUuid(), b.getUuid());
        TestCleanup.atEnd(context, () -> {
            ((TokenizedEntityInterface) a).steveparty$setTokenized(false);
            ((TokenizedEntityInterface) b).steveparty$setTokenized(false);
            TestCleanup.removeIf(context, CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        });
        return new Board(controller, a, b, space);
    }

    private static boolean turnOf(PartyControllerEntity controller, PigEntity token) {
        return controller.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn && turn.getTokenUUID().equals(token.getUuid());
    }

    /** A pipe standing on stone at ({@code x}, 2, {@code z}): a mouth on top. */
    private static BlockPos pipe(TestContext context, int x, int z) {
        context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos pos = new BlockPos(x, 2, z);
        context.setBlockState(pos, ModBlocks.PIPES[PipeKind.OPAQUE.ordinal()][0].getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        return pos;
    }

    // ---------------------------------------------------------------- the event

    /** An event of 1 second: the party waits for its hourglass, then the next turn comes, in the same round. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void theHourglassEndsTheEvent(TestContext context) {
        Board board = board(context, ItemStack.EMPTY, null);
        PartyControllerEntity controller = board.controller();
        PartyData data = controller.getPartyData();
        data.getSteps().add(data.getStepIndex() + 1, EventPartyStep.eventSpace(1, List.of(), 1));
        int bRound = data.getRoundAt(data.getStepIndex() + 2);
        controller.nextStep();
        context.assertTrue(data.getCurrentStep() instanceof EventPartyStep event && event.isTimed(), "the event runs, its hourglass turned");
        context.assertEquals(bRound, 1, "the event slipped in does not split the round");
        context.waitAndRun(10, () -> {
            context.assertTrue(data.getCurrentStep() instanceof EventPartyStep, "half way: still the event");
            TestWait.when(context, () -> turnOf(controller, board.b()), 40, "b's turn after the hourglass", context::complete);
        });
    }

    /** A pawn stops on an Event space: its player comes out of the pipe; a Step Controller's pulse brings him back. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void theEventSpaceSendsThePlayersOutAndBack(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.ADVENTURE);
        TestPlayers.place(context, player, 1.5, 1, 6.5);
        TestPlayers.removeAtEnd(context, player);
        BlockPos pipe = pipe(context, 6, 6);
        ItemStack cartridge = EventCartridgeItem.with(ModItems.EVENT_CARTRIDGE, 1, 1);
        EventTimeOut.setPipes(cartridge, List.of(new EndPipe(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(pipe)), Direction.UP)));
        Board board = board(context, cartridge, player.getUuid());
        context.expectBlockProperty(SPACE, ABoardSpaceBlock.TILE_TYPE, BoardSpaceType.TILE_EVENT);
        PartyControllerEntity controller = board.controller();
        Vec3d before = player.getPos();
        board.space().onDestinationReached(board.a(), controller);
        context.assertTrue(controller.getPartyData().getCurrentStep() instanceof EventPartyStep event && event.hasPlayersOut()
                && event.getSeconds() == 30, "a 30 s event with its player out");
        context.assertTrue(player.getPos().distanceTo(Vec3d.ofCenter(context.getAbsolutePos(pipe))) < 2.5, "the player came out of the pipe");
        context.waitAndRun(5, () -> {
            context.assertTrue(StepPulse.apply(controller, StepPulse.Mode.NEXT), "the Step Controller ends the event");
            context.assertTrue(turnOf(controller, board.b()), "b's turn");
            context.assertTrue(player.getPos().distanceTo(before) < 0.5, "the player is back where he stood");
            context.complete();
        });
    }

    /** The Event card of the program gives its pipes and its hourglass to its step; saved and read back. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theEventCardGivesItsPipesAndHourglass(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(pos);
        ItemStack card = new ItemStack(ModItems.PARTY_CARD_EVENT, 3);
        EndPipe pipe = new EndPipe(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(new BlockPos(5, 2, 5))), Direction.UP);
        EventTimeOut.setPipes(card, List.of(pipe));
        card.set(ModComponents.EVENT_SECONDS, 60);
        controller.getProgram().setStack(0, new ItemStack(ModItems.PARTY_CARD_TURNS));
        controller.getProgram().setStack(1, card);
        PartyData data = new PartyData();
        data.addToken(UUID.randomUUID());
        data.addStep(new BasicGameGeneratorStep());
        controller.setPartyData(data);
        controller.nextStep();
        PartyStep step = data.getSteps().get(2);
        context.assertTrue(step instanceof EventPartyStep event && event.getChannel() == 3 && event.getSeconds() == 60
                && event.getPipes().equals(List.of(pipe)) && !event.isInterlude(), "the card's event: " + step.toNbt());
        PartyStep loaded = PartyStepFactory.get(step.toNbt());
        context.assertTrue(loaded instanceof EventPartyStep event && event.getSeconds() == 60 && event.getPipes().equals(List.of(pipe)),
                "read back from its save");
        context.assertEquals(EventTimeOut.nextDuration(0), 30, "the card's hourglass: none, then 30 s");
        context.assertEquals(EventTimeOut.nextDuration(300), 0, "after 5 min: none again");
        context.removeBlock(pos);
        context.complete();
    }

    // ---------------------------------------------------------------- the mini-game space

    /** A pawn stops on a Mini-game space: a mini-game in its mode is played right after the turn, in the round. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theMiniGameSpaceSlipsAMiniGameIn(TestContext context) {
        UUID owner = UUID.randomUUID();
        Board board = board(context, MiniGameCartridgeItem.with(ModItems.MINIGAME_CARTRIDGE, MiniGameMode.VS), owner);
        context.expectBlockProperty(SPACE, ABoardSpaceBlock.TILE_TYPE, BoardSpaceType.TILE_MINIGAME);
        PartyControllerEntity controller = board.controller();
        PartyData data = controller.getPartyData();
        int index = data.getStepIndex();
        board.space().onDestinationReached(board.a(), controller);
        // No mini-game in the catalogue: skipped at once, b plays
        PartyStep slipped = data.getSteps().get(index + 1);
        context.assertTrue(slipped instanceof MiniGamePartyStep miniGame && miniGame.getMode() == MiniGameMode.VS
                && owner.equals(miniGame.getLander()) && miniGame.isInterlude(), "a VS mini-game of a's player after its turn");
        context.assertTrue(turnOf(controller, board.b()), "then b's turn");
        context.assertEquals(data.getRoundAt(data.getStepIndex()), 1, "still the first round");
        PartyStep loaded = PartyStepFactory.get(slipped.toNbt());
        context.assertTrue(loaded instanceof MiniGamePartyStep miniGame && miniGame.getMode() == MiniGameMode.VS && owner.equals(miniGame.getLander()),
                "its mode and player read back from its save");
        context.complete();
    }

    /** VS: the one who landed against the others; free for all: everyone; a mode no page plays: nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theModesMakeTheirTeams(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        BlockPos blue = pipe(context, 1, 1), red = pipe(context, 3, 1), green = pipe(context, 5, 1);
        // A page for two teams (blue and red pipes), a page for free for all (a green pipe)
        ItemStack teams = new ItemStack(ModItems.MINI_GAME_PAGE), ffa = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID teamsId = MiniGamePages.ensureId(teams), ffaId = MiniGamePages.ensureId(ffa);
        MiniGamePages.toggleLink(server, teamsId, global(context, blue), Direction.UP, MiniGamePipeRole.TEAM_A);
        MiniGamePages.toggleLink(server, teamsId, global(context, red), Direction.UP, MiniGamePipeRole.TEAM_B);
        MiniGamePages.update(server, MiniGamePages.get(server, teamsId).withFormats(List.of(MiniGameFormat.blank())));
        MiniGamePages.toggleLink(server, ffaId, global(context, green), Direction.UP, MiniGamePipeRole.PLAYERS);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        List<TeamDispositionGenerator.Seat> seats = new ArrayList<>();
        for (UUID player : List.of(a, b, c)) seats.add(new TeamDispositionGenerator.Seat(player, ABoardSpaceBehavior.Status.NEUTRAL));
        List<ItemStack> pages = List.of(teams, ffa);

        Map<TeamDisposition, List<ItemStack>> vs = MiniGameModeChoice.ofMode(MiniGameMode.VS, b, seats, pages, Map.of(), server);
        context.assertEquals(vs.size(), 1, "one way in VS");
        TeamDisposition duel = vs.keySet().iterator().next();
        context.assertEquals(duel.getTeamA(), Set.of(b), "b, who landed, alone");
        context.assertEquals(duel.getTeamB(), Set.of(a, c), "against the others");
        context.assertTrue(vs.get(duel).size() == 1 && vs.get(duel).getFirst() == teams, "the two-team page");

        Map<TeamDisposition, List<ItemStack>> all = MiniGameModeChoice.ofMode(MiniGameMode.FFA, b, seats, pages, Map.of(), server);
        context.assertTrue(all.size() == 1 && all.keySet().iterator().next().isFreeForAll(), "free for all");
        context.assertTrue(all.values().iterator().next().size() == 1 && all.values().iterator().next().getFirst() == ffa, "the free for all page");

        context.assertTrue(MiniGameModeChoice.ofMode(MiniGameMode.VS, b, seats, List.of(ffa), Map.of(), server).isEmpty(),
                "no page plays VS: nothing (the board decides)");
        context.complete();
    }

    private static GlobalPos global(TestContext context, BlockPos relative) {
        return GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(relative));
    }
}
