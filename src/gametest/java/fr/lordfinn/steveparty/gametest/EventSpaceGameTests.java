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
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.items.custom.cartridges.MiniGameCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeEdit;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.PageSlotModule;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Event (card and space): a time out of the party, the players out of its pipes and back at its end, ended by its
 * hourglass or a Step Controller. The Mini-game space: a mini-game slipped in after the turn, its teams by its format.
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

    /** A pawn stops on a Mini-game space: a mini-game in its format is played right after the turn, in the round. */
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
                && owner.equals(miniGame.getLander()) && miniGame.isInterlude(), "a 1 vs all mini-game of a's player after its turn");
        context.assertTrue(turnOf(controller, board.b()), "then b's turn");
        context.assertEquals(data.getRoundAt(data.getStepIndex()), 1, "still the first round");
        PartyStep loaded = PartyStepFactory.get(slipped.toNbt());
        context.assertTrue(loaded instanceof MiniGamePartyStep miniGame && miniGame.getMode() == MiniGameMode.VS && owner.equals(miniGame.getLander()),
                "its format and player read back from its save");
        // Several formats and a page: saved too
        UUID page = UUID.randomUUID();
        MiniGamePartyStep picked = MiniGamePartyStep.ofSpace(new ArrayList<>(), List.of(MiniGameMode.DUEL, MiniGameMode.FFA), owner, page);
        context.assertEquals(picked.getMode(), MiniGameMode.CHOICE, "several formats: the player's pick");
        PartyStep read = PartyStepFactory.get(picked.toNbt());
        context.assertTrue(read instanceof MiniGamePartyStep miniGame && miniGame.getChoices().equals(List.of(MiniGameMode.DUEL, MiniGameMode.FFA))
                && page.equals(miniGame.getPage()) && miniGame.getOpponent() == null, "its formats and its page read back");
        context.complete();
    }

    /** The menu's formats: 1 vs all by default; the 1 vs 1 picked or drawn; never none; several: the player's pick. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theCartridgeSetsItsFormats(TestContext context) {
        ItemStack cartridge = new ItemStack(ModItems.MINIGAME_CARTRIDGE);
        context.assertEquals(MiniGameCartridgeItem.formats(cartridge), List.of(MiniGameMode.VS), "1 vs all by default");
        context.assertEquals(MiniGameCartridgeItem.mode(cartridge), MiniGameMode.VS, "one format: that one");
        List<CartridgeModule> modules = CartridgeMenus.modules(cartridge);
        ChoiceModule formats = (ChoiceModule) module(modules, "formats");
        CartridgeModule opponent = module(modules, "opponent");
        context.assertTrue(formats.isToggles() && !formats.accepts(cartridge, 0), "the formats: toggles, never all off");
        context.assertTrue(!opponent.enabled(cartridge), "no 1 vs 1: its opponent can't be set");

        formats.set(new CartridgeEdit(cartridge, null, 0), formats.clicked(formats.get(cartridge), 0));
        context.assertEquals(MiniGameCartridgeItem.formats(cartridge), List.of(MiniGameMode.DUEL, MiniGameMode.VS), "1 vs 1 switched on");
        context.assertEquals(MiniGameCartridgeItem.mode(cartridge), MiniGameMode.CHOICE, "two formats: the player picks");
        context.assertTrue(opponent.enabled(cartridge), "a 1 vs 1: its opponent can be set");
        opponent.set(new CartridgeEdit(cartridge, null, 0), 1);
        context.assertEquals(MiniGameCartridgeItem.formats(cartridge).getFirst(), MiniGameMode.DUEL_RANDOM, "its opponent drawn");
        context.assertEquals(MiniGameCartridgeItem.formats(MiniGameCartridgeItem.with(ModItems.MINIGAME_CARTRIDGE, MiniGameMode.DUEL_RANDOM, MiniGameMode.TEAMS)),
                List.of(MiniGameMode.DUEL_RANDOM, MiniGameMode.TEAMS), "made for the tests the same way");

        // Its page: the one on the cursor (only its id, nothing taken); a page never written is refused; right click: none
        PageSlotModule slot = (PageSlotModule) module(modules, "page");
        ItemStack page = new ItemStack(ModItems.MINI_GAME_PAGE), blank = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID id = MiniGamePages.ensureId(page);
        slot.set(new CartridgeEdit(cartridge, null, 0, page), PageSlotModule.CURSOR);
        context.assertEquals(MiniGameCartridgeItem.page(cartridge), id, "the page kept");
        context.assertEquals(page.getCount(), 1, "the page stays on the cursor");
        context.assertTrue(!PageSlotModule.accepts(blank), "a page never written is refused");
        slot.set(new CartridgeEdit(cartridge, null, 0, blank), PageSlotModule.CURSOR);
        context.assertEquals(MiniGameCartridgeItem.page(cartridge), id, "a blank page changes nothing");
        slot.set(new CartridgeEdit(cartridge, null, 0), PageSlotModule.EMPTY);
        context.assertTrue(MiniGameCartridgeItem.page(cartridge) == null, "emptied: the catalogue again");
        context.complete();
    }

    private static CartridgeModule module(List<CartridgeModule> modules, String id) {
        return modules.stream().filter(module -> module.id().equals(id)).findFirst().orElseThrow();
    }

    /** 1 vs all, free for all, 1 vs 1, the board's teams: their teams; a format no page plays: nothing (the board decides). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theFormatsMakeTheirTeams(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        Pages pages = pages(context);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        List<TeamDispositionGenerator.Seat> seats = new ArrayList<>();
        for (UUID player : List.of(a, b, c)) seats.add(new TeamDispositionGenerator.Seat(player, ABoardSpaceBehavior.Status.NEUTRAL));
        List<ItemStack> both = List.of(pages.teams(), pages.ffa());

        Map<TeamDisposition, List<ItemStack>> vs = MiniGameModeChoice.ofMode(MiniGameMode.VS, b, null, seats, both, Map.of(), server);
        context.assertEquals(vs.size(), 1, "one way in 1 vs all");
        TeamDisposition one = vs.keySet().iterator().next();
        context.assertEquals(one.getTeamA(), Set.of(b), "b, who landed, alone");
        context.assertEquals(one.getTeamB(), Set.of(a, c), "against the others");
        context.assertTrue(vs.get(one).size() == 1 && vs.get(one).getFirst() == pages.teams(), "the two-team page");

        Map<TeamDisposition, List<ItemStack>> all = MiniGameModeChoice.ofMode(MiniGameMode.FFA, b, null, seats, both, Map.of(), server);
        context.assertTrue(all.size() == 1 && all.keySet().iterator().next().isFreeForAll(), "free for all");
        context.assertTrue(all.values().iterator().next().size() == 1 && all.values().iterator().next().getFirst() == pages.ffa(), "the free for all page");

        for (MiniGameMode duel : List.of(MiniGameMode.DUEL, MiniGameMode.DUEL_RANDOM)) {
            Map<TeamDisposition, List<ItemStack>> ofTwo = MiniGameModeChoice.ofMode(duel, b, c, seats, both, Map.of(), server);
            context.assertEquals(ofTwo.size(), 1, duel + ": one way");
            TeamDisposition two = ofTwo.keySet().iterator().next();
            context.assertTrue(two.getTeamA().equals(Set.of(b)) && two.getTeamB().equals(Set.of(c)) && two.teamOf(a) < 0,
                    duel + ": b against c, a out of it");
            context.assertTrue(MiniGameModeChoice.ofMode(duel, b, null, seats, both, Map.of(), server).isEmpty(), duel + ": no opponent, nothing");
            context.assertTrue(MiniGameModeChoice.ofMode(duel, b, d, seats, both, Map.of(), server).isEmpty(), duel + ": an opponent out of the party, nothing");
        }

        // The board's teams: those of two teams or more, never everyone for themselves
        Map<TeamDisposition, List<ItemStack>> board = new LinkedHashMap<>();
        TeamDisposition twoTeams = new TeamDisposition(new LinkedHashSet<>(Set.of(a)), new LinkedHashSet<>(List.of(b, c)));
        board.put(twoTeams, List.of(pages.teams()));
        board.put(TeamDisposition.freeForAll(List.of(a, b, c)), List.of(pages.ffa()));
        Map<TeamDisposition, List<ItemStack>> teams = MiniGameModeChoice.ofMode(MiniGameMode.TEAMS, b, null, seats, both, board, server);
        context.assertEquals(teams.keySet(), Set.of(twoTeams), "the board's teams, without free for all");

        context.assertTrue(MiniGameModeChoice.ofMode(MiniGameMode.VS, b, null, seats, List.of(pages.ffa()), Map.of(), server).isEmpty(),
                "no page plays 1 vs all: nothing (the board decides)");
        context.complete();
    }

    /**
     * Two formats on: the player who landed picks one in the chat (not one that is off), then the opponent of his 1 vs 1
     * among the others; only they two play, on the space's page.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void theLanderPicksTheFormatThenHisOpponent(TestContext context) {
        Duel duel = duel(context, MiniGameMode.DUEL, MiniGameMode.FFA);
        PartyControllerEntity controller = duel.controller();
        ServerPlayerEntity lander = duel.players().get(0), picked = duel.players().get(2);
        MiniGamePartyStep step = duel.step();
        context.assertTrue(step.getMode() == MiniGameMode.CHOICE && MiniGameModeChoice.isAsked(lander.getUuid()), "he is asked the format");
        context.assertTrue(!MiniGameModeChoice.choose(duel.players().get(1), MiniGameMode.FFA), "only he picks");
        context.assertTrue(!MiniGameModeChoice.choose(lander, MiniGameMode.VS), "not a format that is off");
        context.assertTrue(MiniGameModeChoice.choose(lander, MiniGameMode.DUEL), "1 vs 1 picked");
        context.assertTrue(step.getMode() == MiniGameMode.DUEL && MiniGameModeChoice.isAsked(lander.getUuid()), "then he is asked his opponent");
        context.assertTrue(!MiniGameModeChoice.chooseOpponent(lander, lander.getUuidAsString()), "not himself");
        context.assertTrue(MiniGameModeChoice.chooseOpponent(lander, picked.getUuidAsString()), "the third player picked");
        context.assertEquals(step.getOpponent(), picked.getUuid(), "his opponent");
        TeamDisposition teams = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(controller.catalogue);
        context.assertTrue(teams != null && teams.getTeamA().equals(Set.of(lander.getUuid())) && teams.getTeamB().equals(Set.of(picked.getUuid())),
                "he against the one he picked: " + teams);
        context.assertEquals(Set.copyOf(step.getParticipants()), Set.of(lander.getUuid(), picked.getUuid()), "only they two play, the other watches");
        context.complete();
    }

    /** A 1 vs 1 against a random player: nobody is asked, one of the others is drawn, only they two play. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void aRandomOneVsOneDrawsTheOpponent(TestContext context) {
        Duel duel = duel(context, MiniGameMode.DUEL_RANDOM);
        ServerPlayerEntity lander = duel.players().getFirst();
        MiniGamePartyStep step = duel.step();
        context.assertTrue(!MiniGameModeChoice.isAsked(lander.getUuid()), "nobody is asked");
        context.assertTrue(step.getOpponent() != null && !step.getOpponent().equals(lander.getUuid())
                && duel.players().stream().anyMatch(player -> player.getUuid().equals(step.getOpponent())), "one of the others drawn");
        context.assertEquals(step.getParticipants().size(), 2, "only they two play");
        context.complete();
    }

    /** The two pages of the tests: two teams (blue and red pipes), free for all (a green pipe). */
    private record Pages(ItemStack teams, ItemStack ffa) {
    }

    private static Pages pages(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        BlockPos blue = pipe(context, 1, 1), red = pipe(context, 3, 1), green = pipe(context, 5, 1);
        ItemStack teams = new ItemStack(ModItems.MINI_GAME_PAGE), ffa = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID teamsId = MiniGamePages.ensureId(teams), ffaId = MiniGamePages.ensureId(ffa);
        MiniGamePages.toggleLink(server, teamsId, global(context, blue), Direction.UP, MiniGamePipeRole.TEAM_A);
        MiniGamePages.toggleLink(server, teamsId, global(context, red), Direction.UP, MiniGamePipeRole.TEAM_B);
        MiniGamePages.update(server, MiniGamePages.get(server, teamsId).withFormats(List.of(MiniGameFormat.blank())));
        MiniGamePages.toggleLink(server, ffaId, global(context, green), Direction.UP, MiniGamePipeRole.PLAYERS);
        return new Pages(teams, ffa);
    }

    private record Duel(PartyControllerEntity controller, MiniGamePartyStep step, List<ServerPlayerEntity> players) {
    }

    /**
     * Three players, a pawn each, the first one's on a Mini-game space of {@code formats} always playing the two-team
     * page: the first pawn stops there, its mini-game starts.
     */
    private static Duel duel(TestContext context, MiniGameMode... formats) {
        TestBoards.floor(context, 7);
        Pages pages = pages(context);
        List<ServerPlayerEntity> players = new ArrayList<>();
        List<PigEntity> tokens = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ServerPlayerEntity player = TestPlayers.joined(context, "MgSp", "P" + i, GameMode.ADVENTURE, 1.5 + 2 * i, 1, 6.5);
            TestPlayers.removeAtEnd(context, player);
            players.add(player);
            PigEntity token = TestBoards.token(context, new BlockPos(1 + 2 * i, 1, 3), player.getUuid());
            token.setAiDisabled(true);
            tokens.add(token);
        }
        ItemStack cartridge = MiniGameCartridgeItem.with(ModItems.MINIGAME_CARTRIDGE, formats);
        cartridge.set(ModComponents.CARTRIDGE_MINI_GAME_PAGE, pages.teams().get(ModComponents.MINI_GAME_PAGE));
        context.setBlockState(SPACE, ModBlocks.TILE);
        BoardSpaceBlockEntity space = context.getBlockEntity(SPACE);
        space.setStack(0, cartridge);
        PartyControllerEntity controller = TestBoards.party(context, CONTROLLER, 2, tokens.stream().map(PigEntity::getUuid).toArray(UUID[]::new));
        // An empty catalogue: only the space's page is played
        controller.catalogue = new ItemStack(ModItems.MINI_GAMES_CATALOGUE);
        TestCleanup.atEnd(context, () -> {
            tokens.forEach(token -> ((TokenizedEntityInterface) token).steveparty$setTokenized(false));
            TestCleanup.removeIf(context, CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        });
        space.onDestinationReached(tokens.getFirst(), controller);
        if (!(controller.getPartyData().getCurrentStep() instanceof MiniGamePartyStep step)) throw new AssertionError("the mini-game runs");
        return new Duel(controller, step, players);
    }

    private static GlobalPos global(TestContext context, BlockPos relative) {
        return GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(relative));
    }
}
