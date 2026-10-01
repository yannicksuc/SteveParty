package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDispositionGenerator;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDispositionGenerator.Seat;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior.Status;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeNetworks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeSolid;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGameMode;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageNetworking;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The pipes of the mini-games: a pipe's colour gives its role, pages link and re-role pipes, the players come out of
 * the pipes of their team in order (positive players team A, negative ones team B), a party sends its players out of
 * them and brings them back, entry and exit pipes work out of a party, and nothing warps beyond 100 blocks.
 */
public class MiniGamePipeGameTests implements FabricGameTest {
    private static final int WHITE = 0, ORANGE = 1, YELLOW = 4, CYAN = 9, PURPLE = 10, BLUE = 11, GREEN = 13, RED = 14, BLACK = 15;
    private static final String PARTY_BATCH = "mini_game_pipes_party";

    private static Block pipe(PipeKind kind, int color) {
        return ModBlocks.PIPES[kind.ordinal()][kind.colored ? color : 0];
    }

    /** A pipe of that colour standing on a stone block: a mouth on top. @return its position (relative) */
    private static BlockPos mouth(TestContext context, int color, int x, int z) {
        context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos pos = new BlockPos(x, 2, z);
        context.setBlockState(pos, pipe(PipeKind.OPAQUE, color).getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        return pos;
    }

    private static GlobalPos global(TestContext context, BlockPos relative) {
        return GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(relative));
    }

    private static ServerPlayerEntity player(TestContext context, GameMode mode, double x, double y, double z) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(mode);
        player.getInventory().clear();
        Vec3d abs = context.getAbsolute(new Vec3d(x, y, z));
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, 0, 0);
        return player;
    }

    private static void remove(TestContext context, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) {
            if (player.hasVehicle()) player.stopRiding();
            MiniGamePipes.leaveParty(player.getUuid());
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
    }

    /** A page with an id, the pipes linked to it in the order given, each with the role of its colour. */
    private static UUID page(TestContext context, ItemStack stack, BlockPos... mouths) {
        MinecraftServer server = context.getWorld().getServer();
        UUID id = MiniGamePages.ensureId(stack);
        for (BlockPos mouth : mouths) {
            MiniGamePages.toggleLink(server, id, global(context, mouth), Direction.UP, MiniGamePipeRole.ofPipe(context.getBlockState(mouth)));
        }
        return id;
    }

    private static boolean near(TestContext context, ServerPlayerEntity player, BlockPos mouth) {
        Vec3d at = context.getRelative(player.getPos());
        return !player.hasVehicle() && Math.abs(at.x - (mouth.getX() + 0.5)) < 1.2 && Math.abs(at.z - (mouth.getZ() + 0.5)) < 1.2
                && at.y >= mouth.getY() + 0.9;
    }

    private static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            context.throwGameTestException(what);
        } else {
            context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
        }
    }

    // ------------------------------------------------------------------ roles

    /** Green players, white or glass spectators, blue A, red B, purple C, orange D, yellow exit, black entry. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pipeColoursGiveRoles(TestContext context) {
        Map<Integer, MiniGamePipeRole> roles = Map.of(GREEN, MiniGamePipeRole.PLAYERS, WHITE, MiniGamePipeRole.SPECTATORS,
                BLUE, MiniGamePipeRole.TEAM_A, RED, MiniGamePipeRole.TEAM_B, PURPLE, MiniGamePipeRole.TEAM_C,
                ORANGE, MiniGamePipeRole.TEAM_D, YELLOW, MiniGamePipeRole.EXIT, BLACK, MiniGamePipeRole.ENTRY, CYAN, MiniGamePipeRole.PLAYERS);
        roles.forEach((color, role) -> {
            for (PipeKind kind : new PipeKind[]{PipeKind.OPAQUE, PipeKind.WINDOWED, PipeKind.STAINED_GLASS}) {
                context.assertEquals(MiniGamePipeRole.ofPipe(pipe(kind, color).getDefaultState()), role, kind + " " + ModBlocks.COLORS[color]);
            }
        });
        context.assertEquals(MiniGamePipeRole.ofPipe(ModBlocks.GLASS_PIPE.getDefaultState()), MiniGamePipeRole.SPECTATORS, "plain glass: spectators");
        context.assertEquals(MiniGamePipeRole.needed(MiniGameMode.FREE_FOR_ALL), List.of(MiniGamePipeRole.PLAYERS), "free for all: players pipes");
        context.assertEquals(MiniGamePipeRole.needed(MiniGameMode.THREE_TEAMS),
                List.of(MiniGamePipeRole.TEAM_A, MiniGamePipeRole.TEAM_B, MiniGamePipeRole.TEAM_C), "3 teams: A, B and C pipes");
        context.complete();
    }

    /** A click on a pipe with the page links it with the role of its colour; another click unlinks it; the editor changes roles. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pageLinksPipesAndChangesTheirRoles(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        ServerWorld world = context.getWorld();
        BlockPos blue = mouth(context, BLUE, 2, 2), red = mouth(context, RED, 4, 2), green = mouth(context, GREEN, 6, 2);
        ServerPlayerEntity player = player(context, GameMode.SURVIVAL, 2.5, 4, 2.5);
        try {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.MINI_GAME_PAGE, 3));
            // Through the block, as a right click does: nobody goes in the pipe
            BlockState state = context.getBlockState(blue);
            BlockPos blueAbs = context.getAbsolutePos(blue);
            state.onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(blueAbs), Direction.UP, blueAbs, false));
            ItemStack held = player.getMainHandStack();
            UUID id = MiniGamePages.idOf(held);
            context.assertTrue(id != null && held.getCount() == 1, "the page clicked with got its id (one page of the stack)");
            context.assertTrue(!PipeTravel.isTravelling(player), "linking is not going in");
            MiniGamePageData data = MiniGamePages.get(server, id);
            context.assertEquals(data.pipeLinks(), List.of(new MiniGamePipeLink(global(context, blue), Direction.UP, MiniGamePipeRole.TEAM_A)),
                    "the blue pipe is linked as team A");

            context.assertTrue(MiniGamePipes.click(player, Hand.MAIN_HAND, world, context.getAbsolutePos(red)), "red linked");
            context.assertTrue(MiniGamePipes.click(player, Hand.MAIN_HAND, world, context.getAbsolutePos(green)), "green linked");
            data = MiniGamePages.get(server, id);
            context.assertEquals(data.pipeLinks().stream().map(MiniGamePipeLink::role).toList(),
                    List.of(MiniGamePipeRole.TEAM_A, MiniGamePipeRole.TEAM_B, MiniGamePipeRole.PLAYERS), "roles from the colours, in the order linked");
            context.assertEquals(MiniGamePages.linksAt(server, global(context, red)).size(), 1, "the pipe knows its page");

            // The editor: the red pipe becomes a spectators pipe, keeping its place
            MiniGamePagePayloads.PipeRole change = new MiniGamePagePayloads.PipeRole(Hand.MAIN_HAND, id, global(context, red), MiniGamePipeRole.SPECTATORS.ordinal());
            context.assertTrue(MiniGamePageNetworking.pipeRole(player, change), "role changed");
            data = MiniGamePages.get(server, id);
            context.assertEquals(data.pipeLinks().get(1).role(), MiniGamePipeRole.SPECTATORS, "second pipe: spectators now");
            context.assertEquals(data.pipes(MiniGamePipeRole.TEAM_B), List.of(), "no team B pipe any more");

            // Without the right to build: nothing changes
            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(!MiniGamePipes.click(player, Hand.MAIN_HAND, world, context.getAbsolutePos(green)), "adventure: click refused");
            context.assertTrue(!MiniGamePageNetworking.pipeRole(player, new MiniGamePagePayloads.PipeRole(Hand.MAIN_HAND, id, global(context, blue), -1)),
                    "adventure: unlinking refused");
            context.assertEquals(MiniGamePages.get(server, id).pipeLinks().size(), 3, "still three pipes");
            player.changeGameMode(GameMode.SURVIVAL);

            // A second click unlinks; the editor unlinks too
            context.assertTrue(MiniGamePipes.click(player, Hand.MAIN_HAND, world, context.getAbsolutePos(green)), "green clicked again");
            context.assertTrue(MiniGamePageNetworking.pipeRole(player, new MiniGamePagePayloads.PipeRole(Hand.MAIN_HAND, id, global(context, blue), -1)),
                    "blue unlinked in the editor");
            data = MiniGamePages.get(server, id);
            context.assertEquals(data.pipeLinks().stream().map(link -> link.mouth().pos()).toList(), List.of(context.getAbsolutePos(red)), "only the red pipe left");
            context.assertTrue(MiniGamePages.linksAt(server, global(context, green)).isEmpty(), "the green pipe is free again");
            // A block that is not a pipe mouth
            context.assertTrue(!MiniGamePipes.click(player, Hand.MAIN_HAND, world, context.getAbsolutePos(new BlockPos(2, 1, 2))), "stone: nothing");
            context.complete();
        } finally {
            remove(context, player);
        }
    }

    /** The page tells what is missing for each way to play it ticks, and can only be drawn with the pipes of that way. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pageTellsWhichPipesAreMissing(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        BlockPos blue = mouth(context, BLUE, 2, 2), red = mouth(context, RED, 4, 2), purple = mouth(context, PURPLE, 6, 2);
        ItemStack stack = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        TeamDisposition oneVsTwo = new TeamDisposition(Set.of(a), Set.of(b, c));
        TeamDisposition freeForAll = TeamDisposition.freeForAll(List.of(a, b, c));
        TeamDisposition three = new TeamDisposition(Set.of(a), Set.of(b), Set.of(c), Set.of());
        context.assertTrue(!MiniGamePartyStep.pagePlayable(server, stack, freeForAll), "a page never opened can't be drawn");

        UUID id = page(context, stack, blue);
        MiniGamePages.update(server, MiniGamePages.get(server, id).withModes(EnumSet.of(MiniGameMode.FREE_FOR_ALL, MiniGameMode.TWO_TEAMS)));
        MiniGamePageData data = MiniGamePages.get(server, id);
        context.assertEquals(data.missing(MiniGameMode.TWO_TEAMS), List.of(MiniGamePipeRole.TEAM_B), "2 teams ticked but no team B pipe");
        context.assertEquals(data.missing(MiniGameMode.FREE_FOR_ALL), List.of(MiniGamePipeRole.PLAYERS), "free for all ticked but no players pipe");
        context.assertTrue(!data.isPlayable(), "nothing can be played yet");
        context.assertTrue(!MiniGamePartyStep.pagePlayable(server, stack, oneVsTwo), "no team B pipe: not drawn for two teams");

        MiniGamePages.toggleLink(server, id, global(context, red), Direction.UP, MiniGamePipeRole.TEAM_B);
        data = MiniGamePages.get(server, id);
        context.assertTrue(data.missing(MiniGameMode.TWO_TEAMS).isEmpty() && data.isPlayable(), "two teams have their pipes");
        context.assertTrue(MiniGamePartyStep.pagePlayable(server, stack, oneVsTwo), "drawn for two teams");
        context.assertTrue(!MiniGamePartyStep.pagePlayable(server, stack, freeForAll), "still no players pipe: not for free for all");
        context.assertTrue(!MiniGamePartyStep.pagePlayable(server, stack, three), "3 teams is not ticked");

        // Three teams: ticked, and a team C pipe
        MiniGamePages.update(server, data.withModes(EnumSet.of(MiniGameMode.THREE_TEAMS)));
        context.assertEquals(MiniGamePages.get(server, id).missing(MiniGameMode.THREE_TEAMS), List.of(MiniGamePipeRole.TEAM_C), "team C pipe missing");
        MiniGamePages.toggleLink(server, id, global(context, purple), Direction.UP, MiniGamePipeRole.ofPipe(context.getBlockState(purple)));
        context.assertTrue(MiniGamePartyStep.pagePlayable(server, stack, three), "three players, one per team: drawn");
        context.assertTrue(!MiniGamePartyStep.pagePlayable(server, stack, oneVsTwo), "two teams no longer ticked");
        MiniGamePages.update(server, MiniGamePages.get(server, id).withPlayers(4, 8));
        context.assertTrue(!MiniGamePartyStep.pagePlayable(server, stack, three), "too few players for the page");
        context.complete();
    }

    // ------------------------------------------------------------------ teams

    /** Positive players are team A and negative ones team B, whatever their numbers; neutral ones go either way. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void positivePlayersAreTeamAAndNegativeOnesTeamB(TestContext context) {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(), p4 = UUID.randomUUID(), p5 = UUID.randomUUID();

        // One negative against four positive: the positive ones stay team A though they are more
        Set<TeamDisposition> ways = TeamDispositionGenerator.generateTeamDispositions(List.of(new Seat(p1, Status.BAD), new Seat(p2, Status.GOOD),
                new Seat(p3, Status.GOOD), new Seat(p4, Status.GOOD), new Seat(p5, Status.GOOD)));
        context.assertEquals(ways, Set.of(new TeamDisposition(Set.of(p2, p3, p4, p5), Set.of(p1))), "4 positive (A) against 1 negative (B)");
        TeamDisposition lone = ways.iterator().next();
        context.assertEquals(MiniGamePipes.roleOf(lone, p1), MiniGamePipeRole.TEAM_B, "the lone negative player uses the team B pipes");
        context.assertEquals(MiniGamePipes.roleOf(lone, p3), MiniGamePipeRole.TEAM_A, "a positive player the team A pipes");
        context.assertEquals(MiniGameMode.of(lone), MiniGameMode.TWO_TEAMS, "two teams");

        // One positive against three negative (1 v 3)
        ways = TeamDispositionGenerator.generateTeamDispositions(List.of(new Seat(p1, Status.GOOD), new Seat(p2, Status.BAD),
                new Seat(p3, Status.BAD), new Seat(p4, Status.BAD), new Seat(p5, Status.BAD)));
        context.assertEquals(ways, Set.of(new TeamDisposition(Set.of(p1), Set.of(p2, p3, p4, p5))), "1 positive (A) against 4 negative (B)");

        // A neutral player goes to either team
        ways = TeamDispositionGenerator.generateTeamDispositions(List.of(new Seat(p1, Status.GOOD), new Seat(p2, Status.BAD), new Seat(p3, Status.NEUTRAL),
                new Seat(p4, Status.BAD), new Seat(p5, Status.GOOD)));
        context.assertEquals(ways, Set.of(new TeamDisposition(Set.of(p1, p5), Set.of(p2, p3, p4)), new TeamDisposition(Set.of(p1, p3, p5), Set.of(p2, p4))),
                "the neutral player in A or in B");

        // Everyone on the same side: no teams
        ways = TeamDispositionGenerator.generateTeamDispositions(List.of(new Seat(p1, Status.GOOD), new Seat(p2, Status.GOOD)));
        context.assertEquals(ways, Set.of(TeamDisposition.freeForAll(List.of(p1, p2))), "all positive: free for all");
        context.assertEquals(MiniGameMode.of(ways.iterator().next()), MiniGameMode.FREE_FOR_ALL, "free for all");
        context.assertEquals(MiniGamePipes.roleOf(ways.iterator().next(), p1), MiniGamePipeRole.PLAYERS, "players pipes without teams");

        // Two neutral players: free for all, or one against the other either way
        ways = TeamDispositionGenerator.generateTeamDispositions(List.of(new Seat(p1, Status.NEUTRAL), new Seat(p2, Status.NEUTRAL)));
        context.assertEquals(ways, Set.of(TeamDisposition.freeForAll(List.of(p1, p2)), new TeamDisposition(Set.of(p1), Set.of(p2)),
                new TeamDisposition(Set.of(p2), Set.of(p1))), "two neutral players");
        context.complete();
    }

    /** With three (four) players, a three-team (four-team) way to play: one player per team. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void threeAndFourPlayersCanPlayOnePerTeam(TestContext context) {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(), p4 = UUID.randomUUID();
        Set<TeamDisposition> ways = TeamDispositionGenerator.generateTeamDispositions(List.of(new Seat(p1, Status.BAD), new Seat(p2, Status.GOOD),
                new Seat(p3, Status.GOOD)));
        TeamDisposition three = new TeamDisposition(Set.of(p2), Set.of(p1), Set.of(p3), Set.of());
        context.assertEquals(ways, Set.of(new TeamDisposition(Set.of(p2, p3), Set.of(p1)), three),
                "2 v 1, or one per team: the first positive in A, the negative in B, the other in C");
        context.assertEquals(MiniGameMode.of(three), MiniGameMode.THREE_TEAMS, "three teams");
        context.assertEquals(MiniGamePipes.roleOf(three, p3), MiniGamePipeRole.TEAM_C, "team C pipes");

        ways = TeamDispositionGenerator.generateTeamDispositions(List.of(new Seat(p1, Status.NEUTRAL), new Seat(p2, Status.NEUTRAL),
                new Seat(p3, Status.BAD), new Seat(p4, Status.GOOD)));
        TeamDisposition four = new TeamDisposition(Set.of(p4), Set.of(p3), Set.of(p1), Set.of(p2));
        context.assertTrue(ways.contains(four), "one per team: positive A, negative B, the neutral ones C and D in turn order: " + ways);
        context.assertEquals(MiniGameMode.of(four), MiniGameMode.FOUR_TEAMS, "four teams");
        context.assertEquals(four.teamOf(p2), 3, "team D");
        context.assertEquals(ways.size(), 5, "four ways to split the two neutral players in two teams, and one per team");

        // Five players: no three- or four-team way
        ways = TeamDispositionGenerator.generateTeamDispositions(List.of(new Seat(p1, Status.GOOD), new Seat(p2, Status.BAD), new Seat(p3, Status.BAD),
                new Seat(p4, Status.BAD), new Seat(UUID.randomUUID(), Status.BAD)));
        context.assertTrue(ways.stream().allMatch(way -> MiniGameMode.of(way) == MiniGameMode.TWO_TEAMS), "five players: two teams only");

        // The podium names teams C and D too (signal 3 and 4)
        List<UUID> players = List.of(p1, p2, p3, p4);
        context.assertEquals(PodiumBlockEntity.designateWinners(players, List.of(), four, 3, null), List.of(p1), "signal 3: team C");
        context.assertEquals(PodiumBlockEntity.designateWinners(players, List.of(), four, 4, null), List.of(p2), "signal 4: team D");
        context.assertEquals(PodiumBlockEntity.designateWinners(players, List.of(), four, 1, null), List.of(p4), "signal 1: team A");
        context.complete();
    }

    /** Several pipes of a role: the players go one after the other into each in turn, in turn order. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void playersGoInOrderIntoThePipesOfTheirRole(TestContext context) {
        GlobalPos g1 = global(context, new BlockPos(1, 2, 1)), g2 = global(context, new BlockPos(2, 2, 1)), a1 = global(context, new BlockPos(3, 2, 1)),
                b1 = global(context, new BlockPos(4, 2, 1)), b2 = global(context, new BlockPos(5, 2, 1)), s1 = global(context, new BlockPos(6, 2, 1));
        List<MiniGamePipeLink> links = new ArrayList<>();
        links.add(new MiniGamePipeLink(b1, Direction.UP, MiniGamePipeRole.TEAM_B));
        links.add(new MiniGamePipeLink(g1, Direction.UP, MiniGamePipeRole.PLAYERS));
        links.add(new MiniGamePipeLink(a1, Direction.UP, MiniGamePipeRole.TEAM_A));
        links.add(new MiniGamePipeLink(g2, Direction.UP, MiniGamePipeRole.PLAYERS));
        links.add(new MiniGamePipeLink(b2, Direction.UP, MiniGamePipeRole.TEAM_B));
        MiniGamePageData page = MiniGamePageData.empty(UUID.randomUUID()).withPipeLinks(links);
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(), p4 = UUID.randomUUID(), p5 = UUID.randomUUID(), watcher = UUID.randomUUID();
        List<UUID> order = List.of(p1, p2, p3, p4, p5);

        // Free for all: the players pipes each in turn, in turn order
        Map<UUID, MiniGamePipeLink> pipes = MiniGamePipes.distribute(page, TeamDisposition.freeForAll(order), order, List.of(watcher));
        context.assertEquals(order.stream().map(uuid -> pipes.get(uuid).mouth()).toList(), List.of(g1, g2, g1, g2, g1), "g1, g2, g1, g2, g1");
        context.assertTrue(!pipes.containsKey(watcher), "no spectators pipe: the audience stays");
        context.assertEquals(new ArrayList<>(pipes.keySet()), order, "they leave in turn order");
        for (int i = 0; i < 5; i++) {
            context.assertEquals(MiniGamePipes.distribute(page, TeamDisposition.freeForAll(order), order, List.of()), pipes, "never random");
        }

        // Two teams: p2 alone in A, the others in B, each team in its pipes
        TeamDisposition teams = new TeamDisposition(Set.of(p2), Set.of(p1, p3, p4, p5));
        Map<UUID, MiniGamePipeLink> byTeam = MiniGamePipes.distribute(page.withPipeLinks(append(links, new MiniGamePipeLink(s1, Direction.UP, MiniGamePipeRole.SPECTATORS))),
                teams, order, List.of(watcher));
        context.assertEquals(byTeam.get(p2).mouth(), a1, "team A pipe");
        context.assertEquals(List.of(p1, p3, p4, p5).stream().map(uuid -> byTeam.get(uuid).mouth()).toList(), List.of(b1, b2, b1, b2), "team B: b1, b2, b1, b2");
        context.assertEquals(byTeam.get(watcher).mouth(), s1, "the audience out of the spectators pipe");

        // A team without a pipe of its own falls back on the players pipes
        TeamDisposition three = new TeamDisposition(Set.of(p1), Set.of(p2), Set.of(p3), Set.of());
        Map<UUID, MiniGamePipeLink> fallback = MiniGamePipes.distribute(page, three, List.of(p1, p2, p3), List.of());
        context.assertEquals(fallback.get(p3).mouth(), g1, "no team C pipe: a players pipe");
        context.assertTrue(MiniGamePipes.distribute(MiniGamePageData.empty(UUID.randomUUID()), three, List.of(p1), List.of()).isEmpty(), "no pipe at all: nobody leaves");
        context.complete();
    }

    private static List<MiniGamePipeLink> append(List<MiniGamePipeLink> links, MiniGamePipeLink more) {
        List<MiniGamePipeLink> all = new ArrayList<>(links);
        all.add(more);
        return all;
    }

    // ------------------------------------------------------------------ party

    /**
     * The departure of a party's mini-game: each player comes out of a pipe of its team (two in the same pipe one
     * after the other), the audience out of the spectators pipe; the exit pipe brings one back early, the end of the
     * mini-game everyone else.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = PARTY_BATCH, tickLimit = 200)
    public void partySendsPlayersOutOfTheirPipesAndBack(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        BlockPos blue = mouth(context, BLUE, 1, 5), red1 = mouth(context, RED, 3, 5), red2 = mouth(context, RED, 5, 5),
                white = mouth(context, WHITE, 6, 1), yellow = mouth(context, YELLOW, 6, 7);
        ServerPlayerEntity good = player(context, GameMode.SURVIVAL, 0.5, 2, 0.5), bad1 = player(context, GameMode.SURVIVAL, 1.5, 2, 0.5),
                bad2 = player(context, GameMode.SURVIVAL, 2.5, 2, 0.5), bad3 = player(context, GameMode.SURVIVAL, 3.5, 2, 0.5),
                watcher = player(context, GameMode.SURVIVAL, 4.5, 2, 0.5);
        ServerPlayerEntity[] all = {good, bad1, bad2, bad3, watcher};
        List<Vec3d> starts = new ArrayList<>();
        for (ServerPlayerEntity player : all) starts.add(player.getPos());
        BlockPos controllerPos = new BlockPos(0, 2, 7);
        try {
            ItemStack pageStack = new ItemStack(ModItems.MINI_GAME_PAGE);
            page(context, pageStack, blue, red1, red2, white, yellow);

            context.setBlockState(controllerPos.down(), Blocks.STONE);
            context.setBlockState(controllerPos, ModBlocks.PARTY_CONTROLLER);
            PartyControllerEntity controller = context.getBlockEntity(controllerPos);
            NbtCompound stepNbt = new NbtCompound();
            stepNbt.putString("Type", PartyStepType.MINI_GAME.name());
            stepNbt.putString("Status", PartyStep.Status.IN_PROGRESS.name());
            stepNbt.putString("Phase", MiniGamePartyStep.Phase.COUNTDOWN.name());
            stepNbt.putBoolean("MiniGameChosen", true);
            NbtList participants = new NbtList();
            for (ServerPlayerEntity player : new ServerPlayerEntity[]{good, bad1, bad2, bad3}) participants.add(NbtString.of(player.getUuid().toString()));
            stepNbt.put("Participants", participants);
            MiniGamePartyStep step = new MiniGamePartyStep(stepNbt);
            PartyData data = new PartyData();
            data.addStep(step);
            data.setStepIndex(0);
            controller.setPartyData(data);
            ItemStack catalogue = new ItemStack(ModItems.MINI_GAMES_CATALOGUE);
            MiniGamesCatalogueItem.setCurrentMiniGamePage(catalogue, pageStack);
            MiniGamesCatalogueItem.setCurrentMiniGameTeamDisposition(catalogue,
                    new TeamDisposition(Set.of(good.getUuid()), Set.of(bad1.getUuid(), bad2.getUuid(), bad3.getUuid())));
            controller.catalogue = catalogue;
            controller.addInterestedPlayer(watcher);

            step.depart(controller);
            context.assertEquals(step.getPhase(), MiniGamePartyStep.Phase.PLAYING, "the mini-game is being played");
            for (ServerPlayerEntity player : all) {
                context.assertTrue(step.isAway(player.getUuid()) && MiniGamePipes.isInParty(player.getUuid()), "sent to the mini-game");
            }
            context.assertTrue(PipeTravel.isTravelling(good) && PipeTravel.isTravelling(bad1) && PipeTravel.isTravelling(bad2), "they ride out of the pipes");
            context.assertTrue(!PipeTravel.isTravelling(bad3), "the second player of a pipe waits for the first to be out");

            when(context, () -> near(context, good, blue) && near(context, bad1, red1) && near(context, bad2, red2) && near(context, bad3, red1)
                    && near(context, watcher, white), 60, "they never all came out of their pipes", () -> {
                // The exit pipe: back where it stood, before the others
                context.waitAndRun(PipeTravel.COOLDOWN + 1, () -> {
                    context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(yellow), Direction.UP, bad2, 0), "into the exit pipe");
                    when(context, () -> !step.isAway(bad2.getUuid()) && !bad2.hasVehicle(), 20, "the exit pipe never brought the player back", () -> {
                        try {
                            context.assertTrue(bad2.getPos().distanceTo(starts.get(2)) < 0.01, "back where it stood: " + bad2.getPos());
                            context.assertTrue(!MiniGamePipes.isInParty(bad2.getUuid()), "no longer in the mini-game");
                            context.assertTrue(step.isAway(bad1.getUuid()) && near(context, bad1, red1), "the others play on");
                            context.assertTrue(step.isPlaying(), "the mini-game goes on");

                            // The end of the mini-game: everyone back
                            step.end(controller);
                            for (int i = 0; i < all.length; i++) {
                                context.assertTrue(all[i].getPos().distanceTo(starts.get(i)) < 0.01, "player " + i + " is back: " + all[i].getPos());
                                context.assertTrue(!MiniGamePipes.isInParty(all[i].getUuid()) && !step.isAway(all[i].getUuid()), "player " + i + " left the mini-game");
                            }
                        } finally {
                            remove(context, all);
                            context.removeBlock(controllerPos);
                        }
                        context.complete();
                    });
                });
            });
        } catch (RuntimeException e) {
            remove(context, all);
            context.removeBlock(controllerPos);
            throw e;
        }
    }

    // ------------------------------------------------------------------ entry and exit, out of a party

    /**
     * An entry pipe sends to the players pipes, each in turn; the exit pipe brings back to the entry pipe. Nothing is
     * taken from the player.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void entryPipeSendsToThePlayersPipesAndTheExitPipeBringsBack(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos black = mouth(context, BLACK, 1, 1), green1 = mouth(context, GREEN, 4, 5), green2 = mouth(context, GREEN, 6, 5), yellow = mouth(context, YELLOW, 6, 1);
        page(context, new ItemStack(ModItems.MINI_GAME_PAGE), black, green1, green2, yellow);
        ServerPlayerEntity player = player(context, GameMode.SURVIVAL, 1.5, 3, 1.5);
        player.getInventory().insertStack(new ItemStack(Items.ENDER_PEARL, 2));
        BlockPos entry = context.getAbsolutePos(black);
        Runnable cleanup = () -> remove(context, player);

        context.assertTrue(PipeTravel.enter(world, entry, Direction.UP, player, 0), "into the entry pipe");
        when(context, () -> near(context, player, green1), 30, "never came out of the first players pipe", () -> guarded(context, cleanup, () -> {
            // The exit pipe: back out of the entry pipe
            context.waitAndRun(PipeTravel.COOLDOWN + 1, () -> guarded(context, cleanup, () -> {
                context.assertTrue(PipeTravel.enter(world, context.getAbsolutePos(yellow), Direction.UP, player, 0), "into the exit pipe");
                when(context, () -> near(context, player, black), 30, "the exit pipe never brought back to the entry", () -> guarded(context, cleanup, () -> {
                    // Again: the second players pipe this time
                    context.waitAndRun(PipeTravel.COOLDOWN + 1, () -> guarded(context, cleanup, () -> {
                        context.assertTrue(PipeTravel.enter(world, entry, Direction.UP, player, 0), "in again");
                        when(context, () -> near(context, player, green2), 30, "never came out of the second players pipe", () -> {
                            try {
                                context.assertEquals(player.getInventory().count(Items.ENDER_PEARL), 2, "nothing was taken");
                            } finally {
                                cleanup.run();
                            }
                            context.complete();
                        });
                    }));
                }));
            }));
        }));
    }

    /** Runs {@code body}; if it fails, cleans up before the failure goes on. */
    private static void guarded(TestContext context, Runnable cleanup, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
    }

    /** Mobs and items are not concerned by the mini-games: an entry pipe is a pipe like any other for them. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void mobsGoThroughEntryPipesLikeAnyPipe(TestContext context) {
        BlockPos black = mouth(context, BLACK, 1, 1), green = mouth(context, GREEN, 5, 5);
        page(context, new ItemStack(ModItems.MINI_GAME_PAGE), black, green);
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(1, 3, 1));
        pig.setAiDisabled(true);
        context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(black), Direction.UP, pig, 0), "in");
        context.waitAndRun(40, () -> {
            Vec3d at = context.getRelative(pig.getPos());
            // Where the pipe leads (back out, or to another black pipe around), but not to the mini-game
            context.assertTrue(!pig.hasVehicle() && !(Math.abs(at.x - 5.5) < 1.2 && Math.abs(at.z - 5.5) < 1.2),
                    "the pig was not sent out of the players pipe: " + at);
            context.complete();
        });
    }

    // ------------------------------------------------------------------ no warp beyond 100 blocks

    /** A mouth of its colour more than 100 blocks away is no warp, for a player either (whatever it carries): back out. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void noWarpBeyondAHundredBlocksForAPlayerEither(TestContext context) {
        ServerWorld world = context.getWorld();
        Block magenta = pipe(PipeKind.STAINED_GLASS, 2);
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 1), magenta.getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        BlockPos far = context.getAbsolutePos(new BlockPos(1, 2, 120));
        world.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        context.setBlockState(new BlockPos(1, 1, 120), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 120), magenta.getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        BlockPos warp = context.getAbsolutePos(new BlockPos(1, 2, 1));
        ServerPlayerEntity player = player(context, GameMode.SURVIVAL, 1.5, 3, 1.5);
        player.getInventory().insertStack(new ItemStack(Items.ENDER_PEARL));
        Runnable cleanup = () -> {
            remove(context, player);
            world.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
            world.setBlockState(far, Blocks.AIR.getDefaultState());
            world.setBlockState(far.down(), Blocks.AIR.getDefaultState());
        };
        PipeNetworks.Network own = PipeNetworks.of(world).network(warp);
        guarded(context, cleanup, () -> {
            context.assertTrue(PipeNetworks.of(world).nearestMouth(warp, own, PipeNetworks.WARP_RADIUS) == null, "no mouth of its colour within 100 blocks");
            context.assertTrue(PipeTravel.enter(world, warp, Direction.UP, player, 0), "in");
        });
        when(context, () -> player.age > 3 && !PipeTravel.isTravelling(player), 60, "never came back out", () -> {
            try {
                context.assertTrue(context.getRelative(player.getPos()).z < 4, "back out where it went in: " + context.getRelative(player.getPos()));
                context.assertEquals(player.getInventory().count(Items.ENDER_PEARL), 1, "nothing was taken");
            } finally {
                cleanup.run();
            }
            context.complete();
        });
    }

    // ------------------------------------------------------------------ what was removed

    /** The teleportation pads and the « Here we go » / « Here we come » books are gone, with everything that named them. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void padsAndBooksAreGone(TestContext context) {
        for (String id : List.of("big_book", "here_we_go_book", "here_we_come_book")) {
            context.assertTrue(!Registries.ITEM.containsId(Steveparty.id(id)), "no item " + id);
            context.assertTrue(!Registries.BLOCK.containsId(Steveparty.id(id)), "no block " + id);
        }
        context.assertTrue(!Registries.BLOCK_ENTITY_TYPE.containsId(Steveparty.id("big_book_entity")), "no pad block entity");
        context.assertTrue(!Registries.DATA_COMPONENT_TYPE.containsId(Steveparty.id("teleporting-targets")), "no book component");
        for (String handler : List.of("here_we_go_book_screen_handler", "here_we_come_screen_handler")) {
            context.assertTrue(!Registries.SCREEN_HANDLER.containsId(Steveparty.id(handler)), "no screen " + handler);
        }
        context.getWorld().getServer().getRecipeManager().values().forEach(recipe -> {
            String path = recipe.id().getValue().toString();
            context.assertTrue(!path.contains("here_we_") && !path.contains("teleportation_pad") && !path.contains("big_book"), "no recipe " + path);
        });
        for (String lang : List.of("en_us", "fr_fr")) {
            try (java.io.InputStream in = MiniGamePipeGameTests.class.getResourceAsStream("/assets/steveparty/lang/" + lang + ".json")) {
                String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
                for (String word : List.of("here_we", "here we", "big_book", "teleportation pad", "pad de téléportation")) {
                    context.assertTrue(!text.contains(word), lang + " still says « " + word + " »");
                }
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        }
        // A page that still carries pad positions loses them when the server next looks at it
        ItemStack old = new ItemStack(ModItems.MINI_GAME_PAGE);
        old.set(fr.lordfinn.steveparty.components.ModComponents.DESTINATIONS_COMPONENT,
                new fr.lordfinn.steveparty.components.DestinationsComponent(new ArrayList<>(List.of(new BlockPos(1, 2, 3))), ""));
        ServerPlayerEntity player = player(context, GameMode.SURVIVAL, 1.5, 2, 1.5);
        try {
            long time = context.getWorld().getTime();
            old.getItem().inventoryTick(old, context.getWorld(), player, (int) (20 - time % 20) % 20, false);
            context.assertTrue(!old.contains(fr.lordfinn.steveparty.components.ModComponents.DESTINATIONS_COMPONENT), "the old pad positions are dropped");
        } finally {
            remove(context, player);
        }
        context.complete();
    }
}
