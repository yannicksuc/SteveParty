package fr.lordfinn.steveparty.gametest;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeSolid;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageNetworking;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGamePodiumLink;
import fr.lordfinn.steveparty.minigame.MiniGameSession;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import fr.lordfinn.steveparty.minigame.MiniGameTest.Status;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.podium.Podiums;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testing a mini-game out of any party, from its page: who is recruited (by the role of the pipe they stand near),
 * the flow up to the podiums with results and no payout, what stops it (its button, a linked step controller, its
 * players leaving, a party drawing the page), and why a page can't be tested.
 */
public class MiniGameTestGameTests implements FabricGameTest {
    private static final int WHITE = 0, YELLOW = 4, BLUE = 11, GREEN = 13, RED = 14;
    private static final AtomicInteger SERIAL = new AtomicInteger();

    // ------------------------------------------------------------------ helpers

    /** A pipe of that colour standing on a stone block: a mouth on top. @return its position (relative) */
    private static BlockPos pipe(TestContext context, int color, int x, int z) {
        context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos pos = new BlockPos(x, 2, z);
        context.setBlockState(pos, ModBlocks.PIPES[PipeKind.OPAQUE.ordinal()][color].getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        return pos;
    }

    private static GlobalPos global(TestContext context, BlockPos relative) {
        return GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(relative));
    }

    /** A page (free for all unless said otherwise) with the pipes linked to it, each with the role of its colour. */
    private static UUID page(TestContext context, BlockPos... pipes) {
        MinecraftServer server = context.getWorld().getServer();
        UUID id = UUID.randomUUID();
        MiniGamePages.update(server, MiniGamePageData.empty(id).withTexts("Essai " + SERIAL.incrementAndGet(), ""));
        for (BlockPos pipe : pipes) {
            MiniGamePages.toggleLink(server, id, global(context, pipe), Direction.UP, MiniGamePipeRole.ofPipe(context.getBlockState(pipe)));
        }
        return id;
    }

    private static BlockPos podium(TestContext context, UUID page, int x, int z, int halves) {
        BlockPos bottom = new BlockPos(x, 1, z);
        if (halves >= 2) context.setBlockState(bottom, ModBlocks.PODIUM.getDefaultState().with(PodiumBlock.FULL, true));
        else context.setBlockState(bottom, ModBlocks.GOLD_PODIUM.getDefaultState());
        MiniGamePages.addPodiumLink(context.getWorld().getServer(), page, new MiniGamePodiumLink(global(context, bottom), MiniGamePodiumLink.Kind.PODIUM));
        return bottom;
    }

    private static UUID occupant(TestContext context, BlockPos podium) {
        PodiumBlockEntity entity = context.getBlockEntity(podium);
        return entity.getOccupant() == null ? null : entity.getOccupant().player();
    }

    /** A connected player with a name of its own, standing at a relative position. */
    private static ServerPlayerEntity player(TestContext context, String name, double x, double y, double z) {
        ServerWorld world = context.getWorld();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "t" + SERIAL.incrementAndGet() + name);
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        Vec3d abs = context.getAbsolute(new Vec3d(x, y, z));
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, 0, 0);
        return player;
    }

    /**
     * The players other tests left behind around this test are sent away from the server: they would be recruited
     * like anyone standing near a pipe. (These tests run in batches of their own: nobody else is playing now.)
     */
    private static void alone(TestContext context, ServerPlayerEntity... mine) {
        List<ServerPlayerEntity> own = List.of(mine);
        Vec3d center = context.getAbsolute(new Vec3d(4, 2, 4));
        for (ServerPlayerEntity other : new ArrayList<>(context.getWorld().getServer().getPlayerManager().getPlayerList())) {
            if (!own.contains(other) && other.getPos().squaredDistanceTo(center) < 40 * 40) context.getWorld().getServer().getPlayerManager().remove(other);
        }
    }

    private static void remove(TestContext context, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) {
            if (player.hasVehicle()) player.stopRiding();
            MiniGamePipes.leaveParty(player.getUuid());
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
    }

    private static void cleanUp(TestContext context, UUID page, ServerPlayerEntity... players) {
        MiniGameTest.stop(page);
        remove(context, players);
    }

    private static boolean at(ServerPlayerEntity player, Vec3d pos) {
        return !player.hasVehicle() && player.getPos().distanceTo(pos) < 0.01;
    }

    // ------------------------------------------------------------------ who plays

    /**
     * A player within 10 blocks of a pipe takes the role of the nearest one (the pipe nearest to him is his side,
     * playtest #72); entry and exit pipes recruit nobody.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_test_recruit")
    public void playersAreRecruitedByTheNearestPipe(TestContext context) {
        BlockPos green = pipe(context, GREEN, 0, 0), blue = pipe(context, BLUE, 3, 0), white = pipe(context, WHITE, 0, 3), yellow = pipe(context, YELLOW, 7, 5);
        ServerPlayerEntity g = player(context, "g", 0.5, 1, 1.5), b = player(context, "b", 2.5, 1, 1.5), w = player(context, "w", 1.5, 1, 3.5),
                exit = player(context, "x", 7.5, 1, 5.5), high = player(context, "h", 3.5, 9, 0.5), far = player(context, "f", 4.5, 14, 5.5);
        try {
            alone(context, g, b, w, exit, high, far);
            UUID id = page(context, green, blue, white, yellow);
            MinecraftServer server = context.getWorld().getServer();
            Map<UUID, MiniGamePipeRole> recruits = MiniGameTest.recruit(server, MiniGamePages.get(server, id));
            Map<UUID, MiniGamePipeRole> expected = new LinkedHashMap<>();
            expected.put(g.getUuid(), MiniGamePipeRole.PLAYERS);
            expected.put(b.getUuid(), MiniGamePipeRole.TEAM_A);
            expected.put(w.getUuid(), MiniGamePipeRole.SPECTATORS);
            // On the exit pipe, the blue pipe is the nearest one within 10 blocks; 6.5 blocks above it, blue too
            expected.put(exit.getUuid(), MiniGamePipeRole.TEAM_A);
            expected.put(high.getUuid(), MiniGamePipeRole.TEAM_A);
            context.assertEquals(recruits.keySet(), expected.keySet(), "who is within 10 blocks of an arrival pipe");
            for (UUID uuid : expected.keySet()) context.assertEquals(recruits.get(uuid), expected.get(uuid), "each takes the role of the nearest pipe");
            context.assertTrue(MiniGameTest.recruit(server, MiniGamePages.get(server, page(context, yellow))).isEmpty(), "an exit pipe recruits nobody");
            // Someone already in a mini-game is not recruited
            MiniGamePipes.enterParty(g.getUuid(), id, () -> true);
            context.assertTrue(!MiniGameTest.recruit(server, MiniGamePages.get(server, id)).containsKey(g.getUuid()), "nobody in two mini-games at once");
        } finally {
            remove(context, g, b, w, exit, high, far);
        }
        context.complete();
    }

    private static final MiniGameFormat ANY = MiniGameFormat.freeForAll(1, MiniGameFormat.Side.INFINITE), TWO = MiniGameFormat.blank();

    private static MiniGamePageData pageOf(List<MiniGameFormat> formats, MiniGamePipeRole... pipes) {
        List<MiniGamePipeLink> links = new ArrayList<>();
        for (int i = 0; i < pipes.length; i++) {
            links.add(new MiniGamePipeLink(GlobalPos.create(net.minecraft.world.World.OVERWORLD, new BlockPos(i, 0, 0)), Direction.UP, pipes[i]));
        }
        return MiniGamePageData.empty(UUID.randomUUID()).withFormats(formats).withPipeLinks(links);
    }

    private static Map<UUID, MiniGamePipeRole> recruits(Object... pairs) {
        Map<UUID, MiniGamePipeRole> recruits = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) recruits.put((UUID) pairs[i], (MiniGamePipeRole) pairs[i + 1]);
        return recruits;
    }

    /**
     * The way to play of a test: the one ticked on the page that takes the most of those near the pipes; those near a
     * pipe it does not use are left out; and why a page can't be tested.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theWayToPlayDecidesWhoPlays(TestContext context) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID(), s = UUID.randomUUID();
        MiniGamePipeRole players = MiniGamePipeRole.PLAYERS, teamA = MiniGamePipeRole.TEAM_A, teamB = MiniGamePipeRole.TEAM_B, watch = MiniGamePipeRole.SPECTATORS;

        // Free for all: the players pipes; someone near a team pipe is left out, the spectator watches
        MiniGameTest.Plan plan = MiniGameTest.plan(pageOf(List.of(ANY), players, teamA, watch), recruits(a, players, b, teamA, s, watch));
        context.assertTrue(plan.status() == Status.READY && plan.format() == 0, "free for all");
        context.assertEquals(plan.players(), List.of(a), "the one near the players pipe plays");
        context.assertEquals(plan.ignored(), Map.of(b, teamA), "the one near a team pipe is left out, with its role");
        context.assertEquals(plan.spectators(), List.of(s), "the one near the spectators pipe watches");
        context.assertTrue(plan.teams() != null && plan.teams().isFreeForAll(), "no team");

        // Two teams: each needs someone
        MiniGamePageData twoTeams = pageOf(List.of(TWO), teamA, teamB);
        context.assertEquals(MiniGameTest.plan(twoTeams, recruits(a, teamA)).status(), Status.NOT_ENOUGH, "a team without anyone");
        plan = MiniGameTest.plan(twoTeams, recruits(a, teamA, b, teamB, c, teamB));
        context.assertTrue(plan.status() == Status.READY && plan.format() == 0, "two teams");
        context.assertEquals(plan.teams(), new TeamDisposition(Set.of(a), Set.of(b, c)), "1 v 2: the teams of the pipes");

        // Both ticked: the way that takes the most players (free for all when equal)
        MiniGamePageData both = pageOf(List.of(ANY, TWO), players, teamA, teamB);
        context.assertEquals(MiniGameTest.plan(both, recruits(a, players, b, teamA, c, teamB)).format(), 1, "two in teams, one alone: teams");
        context.assertEquals(MiniGameTest.plan(both, recruits(a, players, d, players, b, teamA, c, teamB)).format(), 0,
                "two and two: the more specific (free for all, one range)");

        // Why not
        context.assertEquals(MiniGameTest.plan(pageOf(List.of(ANY), watch), recruits(s, watch)).status(), Status.NO_PIPE, "no players pipe");
        context.assertEquals(MiniGameTest.plan(pageOf(List.of(TWO), players), recruits(a, players)).status(), Status.NO_PIPE,
                "the format has no pipe of its own");
        context.assertEquals(MiniGameTest.plan(pageOf(List.of(ANY), players, watch), recruits(s, watch)).status(), Status.NOBODY, "only a spectator");
        context.assertEquals(MiniGameTest.plan(pageOf(List.of(ANY), players), recruits()).status(), Status.NOBODY, "nobody");
        MiniGameTest.Plan few = MiniGameTest.plan(pageOf(List.of(MiniGameFormat.freeForAll(2, 4)), players), recruits(a, players));
        context.assertEquals(few.status(), Status.NOT_ENOUGH, "fewer than the format's players");
        context.assertEquals(few.shortfall(), new MiniGameTest.Shortfall(0, MiniGamePipeRole.PLAYERS.ordinal(), 1, 2, 4),
                "the closest format says what it misses: 1 near the players pipes, 2 to 4 wanted");
        context.complete();
    }

    // ------------------------------------------------------------------ the flow

    /**
     * A test from its start to its podiums: only its players take a podium and count on a linked base, everyone
     * placed ends it, the results say what each place is worth and nothing is paid, then everyone is back where it was.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_test_flow", tickLimit = 200)
    public void aTestRunsToItsPodiumsAndPaysNothing(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1), white = pipe(context, WHITE, 3, 1);
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 0.5, 1, 1.5), watcher = player(context, "s", 3.5, 1, 2.5),
                observer = player(context, "o", 7.5, 14, 7.5);
        MinecraftServer server = context.getWorld().getServer();
        ServerWorld world = context.getWorld();
        UUID id = page(context, green, white);
        BlockPos basePos = new BlockPos(6, 1, 2);
        MiniGameTest test;
        List<Vec3d> starts = List.of(p1.getPos(), p2.getPos(), watcher.getPos(), observer.getPos());
        try {
            alone(context, p1, p2, watcher, observer);
            BlockPos first = podium(context, id, 4, 5, 2), second = podium(context, id, 5, 5, 1);
            context.setBlockState(basePos, ModBlocks.GOAL_POLE_BASE.getDefaultState().with(GoalPoleBaseBlock.FACING, Direction.NORTH));
            GoalPoleNetwork.processPending();
            GoalPoleBaseBlockEntity base = context.getBlockEntity(basePos);
            base.setPlayers(GoalPoleBaseBlockEntity.Players.PARTY, 16);
            MiniGamePages.addPodiumLink(server, id, new MiniGamePodiumLink(global(context, basePos), MiniGamePodiumLink.Kind.COUNTER));
            base.credit("old", 3, null);
            ((PodiumBlockEntity) context.getBlockEntity(second)).setOccupant(new fr.lordfinn.steveparty.podium.PodiumOccupant(UUID.randomUUID(), "old", -1, 2, 0));

            context.assertEquals(MiniGameTest.check(server, id).status(), Status.READY, "two players near the players pipe");
            context.assertEquals(MiniGameTest.start(server, id, observer, 0), Status.READY, "the test starts");
            test = MiniGameTest.of(id);
            context.assertTrue(test != null && test.phase() == MiniGameTest.Phase.PLAYING, "being played");
            context.assertEquals(Set.copyOf(test.participants()), Set.of(p1.getUuid(), p2.getUuid()), "the two near the players pipe play");
            context.assertEquals(test.spectators(), List.of(watcher.getUuid()), "the one near the spectators pipe watches");
            context.assertEquals(test.observer(), observer.getUuid(), "who started it far from the pipes only observes");
            context.assertTrue(MiniGameSession.playing(List.of(id)) == test, "the page's mini-game is this test");
            context.assertEquals(MiniGameTest.check(server, id).status(), Status.RUNNING, "one test per page");
            context.assertEquals(MiniGameTest.start(server, id, observer, 0), Status.RUNNING, "a second one is refused");
            context.assertTrue(occupant(context, second) == null && base.getTotal() == 0, "podiums emptied and counters back to 0, as in a party");
            context.assertTrue(MiniGamePipes.isInParty(p1.getUuid()) && MiniGamePipes.isInParty(watcher.getUuid()) && !MiniGamePipes.isInParty(observer.getUuid()),
                    "players and spectators are in the mini-game, the observer is not");
            context.assertTrue(at(observer, starts.get(3)), "the observer is not moved");

            // Its podiums and its counters only know its players
            context.assertTrue(base.follows(p1) && !base.follows(observer) && !base.follows(watcher), "a linked base counts the test's players only");
            context.assertTrue(!Podiums.toggle(observer, world, context.getAbsolutePos(first)) && occupant(context, first) == null, "the observer takes no podium");
            context.assertTrue(Podiums.toggle(p2, world, context.getAbsolutePos(first)), "p2 takes the first place");
            context.assertTrue(test.phase() == MiniGameTest.Phase.PLAYING, "p1 has no place: it goes on");
            context.assertTrue(Podiums.toggle(p1, world, context.getAbsolutePos(second)), "p1 takes the second place");
            context.assertEquals(test.phase(), MiniGameTest.Phase.FINISHED, "everyone placed: over, as in a party");
            context.assertEquals(test.places(), Map.of(p1.getUuid(), 2, p2.getUuid(), 1), "the places of the podiums");
            context.assertTrue(test.results() != null && test.results().test(), "results marked as a test's");
            context.assertEquals(test.results().rows().stream().map(row -> row.coins()).toList(), List.of(10, 5), "what each place would be paid by default");
            context.assertEquals(p2.getInventory().count(Items.EMERALD) + p2.getInventory().count(PartyCurrency.COIN.defaultStack().getItem()), 0, "nothing is paid");
            context.assertTrue(MiniGameSession.playing(List.of(id)) == null, "no longer being played");
        } catch (RuntimeException e) {
            context.setBlockState(basePos, Blocks.AIR);
            cleanUp(context, id, p1, p2, watcher, observer);
            throw e;
        }
        context.waitAndRun(MiniGamePartyStep.RETURN_DELAY_TICKS + 5, () -> {
            try {
                context.assertTrue(MiniGameTest.of(id) == null, "the test is over");
                context.assertTrue(at(p1, starts.get(0)) && at(p2, starts.get(1)) && at(watcher, starts.get(2)), "everyone is back where it stood");
                context.assertTrue(!MiniGamePipes.isInParty(p1.getUuid()) && !MiniGamePipes.isInParty(watcher.getUuid()), "and out of the mini-game");
                context.assertEquals(MiniGameTest.check(server, id).status(), Status.READY, "it can be tested again");
            } finally {
                context.setBlockState(basePos, Blocks.AIR);
                cleanUp(context, id, p1, p2, watcher, observer);
            }
            context.complete();
        });
    }

    /**
     * The editor's button: it starts the test after the card's countdown (only for who may edit the page), and
     * stopping it brings everyone back at once, without results. A test whose players all left stops by itself.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_test_button", tickLimit = 300)
    public void theButtonStartsAndStopsATest(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1);
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        MinecraftServer server = context.getWorld().getServer();
        UUID id = page(context, green);
        List<Vec3d> starts = List.of(p1.getPos(), p2.getPos());
        try {
            alone(context, p1, p2);
            ItemStack stack = new ItemStack(ModItems.MINI_GAME_PAGE);
            stack.set(fr.lordfinn.steveparty.components.ModComponents.MINI_GAME_PAGE, new fr.lordfinn.steveparty.components.MiniGamePageRef(id, "", false));
            p1.setStackInHand(Hand.MAIN_HAND, stack);
            p1.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(MiniGamePageNetworking.testAction(p1, new MiniGamePagePayloads.TestAction(Hand.MAIN_HAND, id, true)) == null, "Adventure: not allowed");
            context.assertTrue(MiniGameTest.of(id) == null, "nothing started");
            p1.changeGameMode(GameMode.CREATIVE);
            context.assertEquals(MiniGamePageNetworking.testAction(p1, new MiniGamePagePayloads.TestAction(Hand.MAIN_HAND, id, true)), Status.READY, "the button starts the test");
            context.assertTrue(MiniGameTest.of(id) != null && MiniGameTest.of(id).phase() == MiniGameTest.Phase.COUNTDOWN, "the card and its countdown first");
            context.assertTrue(MiniGameSession.playing(List.of(id)) == null, "not played yet");
        } catch (RuntimeException e) {
            cleanUp(context, id, p1, p2);
            throw e;
        }
        context.waitAndRun(MiniGameTest.COUNTDOWN_SECONDS * 20 + 15, () -> {
            try {
                MiniGameTest test = MiniGameTest.of(id);
                context.assertTrue(test != null && test.phase() == MiniGameTest.Phase.PLAYING, "played after the countdown");
                context.assertTrue(!at(p1, starts.get(0)) || p1.hasVehicle(), "the players were sent through their pipe");
                context.assertEquals(MiniGamePageNetworking.testAction(p1, new MiniGamePagePayloads.TestAction(Hand.MAIN_HAND, id, false)), Status.RUNNING, "the same button stops it");
                context.assertTrue(MiniGameTest.of(id) == null && test.results() == null, "stopped at once, without results");
                context.assertTrue(at(p1, starts.get(0)) && at(p2, starts.get(1)), "everyone is back where it stood");
                context.assertTrue(!MiniGamePipes.isInParty(p1.getUuid()), "and out of the mini-game");

                // A test whose players all leave the server stops by itself
                context.assertEquals(MiniGameTest.start(server, id, null, 0), Status.READY, "another test");
                remove(context, p1, p2);
            } catch (RuntimeException e) {
                cleanUp(context, id, p1, p2);
                throw e;
            }
            context.waitAndRun(45, () -> {
                try {
                    context.assertTrue(MiniGameTest.of(id) == null, "nobody left: the test stopped by itself");
                } finally {
                    cleanUp(context, id);
                }
                context.complete();
            });
        });
    }

    // ------------------------------------------------------------------ what else ends it

    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 7);

    /** A party whose mini-game step, on {@code pageId}, is at {@code phase} with {@code participants}. */
    private static PartyControllerEntity party(TestContext context, UUID pageId, MiniGamePartyStep.Phase phase, ServerPlayerEntity... participants) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        NbtCompound stepNbt = new NbtCompound();
        stepNbt.putString("Type", PartyStepType.MINI_GAME.name());
        stepNbt.putString("Status", PartyStep.Status.IN_PROGRESS.name());
        stepNbt.putString("Phase", phase.name());
        stepNbt.putBoolean("MiniGameChosen", true);
        NbtList list = new NbtList();
        List<UUID> uuids = new ArrayList<>();
        for (ServerPlayerEntity player : participants) {
            list.add(NbtString.of(player.getUuid().toString()));
            uuids.add(player.getUuid());
        }
        stepNbt.put("Participants", list);
        UUID token = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(token);
        data.addStep(new PartyStep());
        data.addStep(new MiniGamePartyStep(stepNbt));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token))));
        data.setStepIndex(1);
        controller.setPartyData(data);
        ItemStack pageStack = new ItemStack(ModItems.MINI_GAME_PAGE);
        pageStack.set(fr.lordfinn.steveparty.components.ModComponents.MINI_GAME_PAGE, new fr.lordfinn.steveparty.components.MiniGamePageRef(pageId, "", false));
        ItemStack catalogue = new ItemStack(ModItems.MINI_GAMES_CATALOGUE);
        MiniGamesCatalogueItem.setCurrentMiniGamePage(catalogue, pageStack);
        MiniGamesCatalogueItem.setCurrentMiniGameTeamDisposition(catalogue, TeamDisposition.freeForAll(uuids));
        controller.catalogue = catalogue;
        return controller;
    }

    /**
     * A step controller linked to the page ends its test (next: with results, the places as they stand; restart or
     * previous: stopped). A party playing the page: no test; a party about to play a page under test stops the test.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_test_step", tickLimit = 100)
    public void aLinkedStepControllerEndsATest(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1), stepPos = new BlockPos(5, 1, 5);
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 2.5, 1, 1.5);
        MinecraftServer server = context.getWorld().getServer();
        UUID id = page(context, green);
        Vec3d start = p1.getPos();
        try {
            alone(context, p1, p2);
            BlockPos first = podium(context, id, 4, 3, 2);
            podium(context, id, 5, 3, 1);
            podium(context, id, 6, 3, 1);
            context.setBlockState(stepPos.down(), Blocks.STONE);
            context.setBlockState(stepPos, ModBlocks.STEP_CONTROLLER);
            StepControllerBlockEntity stepController = context.getBlockEntity(stepPos);
            MiniGamePages.addPodiumLink(server, id, new MiniGamePodiumLink(global(context, stepPos), MiniGamePodiumLink.Kind.STEP_CONTROLLER));

            // Next: the test ends with its results, the podiums as they stand
            context.assertEquals(MiniGameTest.start(server, id, null, 0), Status.READY, "a test");
            MiniGameTest test = MiniGameTest.of(id);
            context.assertTrue(Podiums.toggle(p2, context.getWorld(), context.getAbsolutePos(first)), "p2 takes the first place");
            context.assertTrue(test.phase() == MiniGameTest.Phase.PLAYING, "p1 has no place, podiums are left: it goes on");
            context.assertTrue(stepController.getLinkedPages().contains(id), "the step controller is linked to the page");
            stepController.trigger();
            context.assertEquals(test.phase(), MiniGameTest.Phase.FINISHED, "ended by the linked step controller");
            context.assertEquals(test.places(), Map.of(p1.getUuid(), 0, p2.getUuid(), 1), "the places as they stood; p1 is a participant");
            context.assertTrue(test.results() != null && test.results().test(), "with its results");
            MiniGameTest.stop(id);
            context.assertTrue(at(p1, start), "back");

            // Restart (or previous): stopped at once, without results
            context.assertEquals(MiniGameTest.start(server, id, null, 0), Status.READY, "another test");
            test = MiniGameTest.of(id);
            stepController.mode = 1;
            stepController.trigger();
            context.assertTrue(MiniGameTest.of(id) == null && test.results() == null && at(p1, start), "stopped, everyone back");

            // A party about to play the page: the test gives way
            context.assertEquals(MiniGameTest.start(server, id, null, 0), Status.READY, "a third test");
            PartyControllerEntity controller = party(context, id, MiniGamePartyStep.Phase.COUNTDOWN, p1);
            ((MiniGamePartyStep) controller.getPartyData().getCurrentStep()).depart(controller);
            context.assertTrue(MiniGameTest.of(id) == null, "the test was stopped for the party");
            context.assertTrue(MiniGameSession.playing(List.of(id)) instanceof fr.lordfinn.steveparty.minigame.PartyMiniGameSession, "the page's mini-game is the party's");
            context.assertEquals(MiniGameTest.check(server, id).status(), Status.PARTY_PLAYING, "no test while a party plays the page");
            context.assertEquals(MiniGameTest.start(server, id, null, 0), Status.PARTY_PLAYING, "refused");
            context.removeBlock(CONTROLLER);
        } finally {
            if (context.getBlockState(CONTROLLER).isOf(ModBlocks.PARTY_CONTROLLER)) context.removeBlock(CONTROLLER);
            cleanUp(context, id, p1, p2);
        }
        context.complete();
    }

    /** Why a page can't be tested, as its button says it. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_test_reasons")
    public void whyAPageCanNotBeTested(TestContext context) {
        ServerPlayerEntity p1 = player(context, "a", 7.5, 14, 7.5);
        MinecraftServer server = context.getWorld().getServer();
        UUID id = page(context);
        try {
            alone(context, p1);
            context.assertEquals(MiniGameTest.check(server, id).status(), Status.NO_PIPE, "no pipe linked");
            context.assertEquals(MiniGameTest.start(server, id, p1, 0), Status.NO_PIPE, "and no test starts");
            BlockPos green = pipe(context, GREEN, 1, 1);
            MiniGamePages.toggleLink(server, id, global(context, green), Direction.UP, MiniGamePipeRole.PLAYERS);
            context.assertEquals(MiniGameTest.check(server, id).status(), Status.NOBODY, "nobody within 10 blocks of the pipe");
            Vec3d near = context.getAbsolute(new Vec3d(1.5, 1, 2.5));
            p1.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
            MiniGameTest.Plan ready = MiniGameTest.check(server, id);
            context.assertTrue(ready.status() == Status.READY && ready.players().equals(List.of(p1.getUuid())) && ready.format() == 0,
                    "someone near the pipe: ready, alone, free for all");
            MiniGamePages.update(server, MiniGamePages.get(server, id).withFormats(List.of(MiniGameFormat.freeForAll(2, 4))));
            context.assertEquals(MiniGameTest.check(server, id).status(), Status.NOT_ENOUGH, "the page wants two players");
            context.assertTrue(MiniGameTest.of(id) == null, "checking starts nothing");
        } finally {
            cleanUp(context, id, p1);
        }
        context.complete();
    }

    /**
     * A player who left the server during a test is not forgotten when it ends: it is brought back where it stood
     * when it comes again, even after the server was restarted in between; the others at once.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "minigame_test_away", tickLimit = 100)
    public void aPlayerAwayAtTheEndIsBroughtBackWhenItComes(TestContext context) {
        BlockPos green = pipe(context, GREEN, 1, 1);
        ServerPlayerEntity p1 = player(context, "a", 1.5, 1, 2.5), p2 = player(context, "b", 0.5, 1, 1.5);
        MinecraftServer server = context.getWorld().getServer();
        UUID id = page(context, green);
        Vec3d start1 = p1.getPos(), start2 = p2.getPos();
        GameProfile away = p2.getGameProfile();
        ServerPlayerEntity back = null;
        try {
            alone(context, p1, p2);
            context.assertEquals(MiniGameTest.start(server, id, null, 0), Status.READY, "the test starts");
            Reconnect.leave(p2);
            MiniGameTest.stop(id);
            context.assertTrue(at(p1, start1), "who is there is back at once");
            context.assertTrue(MiniGameReturns.isPending(server, away.getId()), "who left is waited for");
            MiniGameReturns.simulateRestart(server);
            context.assertTrue(MiniGameReturns.isPending(server, away.getId()), "and still is after a restart");
            back = Reconnect.join(context, away);
        } catch (RuntimeException e) {
            if (back != null) remove(context, back);
            cleanUp(context, id, p1, p2);
            throw e;
        }
        ServerPlayerEntity joined = back;
        // Brought back the tick after it comes (the join itself puts it where it was saved)
        context.waitAndRun(2, () -> {
            try {
                context.assertTrue(at(joined, start2), "it comes back where it stood before the test");
                context.assertTrue(!MiniGameReturns.isPending(server, away.getId()), "once");
            } finally {
                remove(context, joined);
                cleanUp(context, id, p1, p2);
            }
            context.complete();
        });
    }
}
