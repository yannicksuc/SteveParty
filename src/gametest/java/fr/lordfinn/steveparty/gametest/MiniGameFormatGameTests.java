package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeSolid;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGameFormat.Side;
import fr.lordfinn.steveparty.minigame.MiniGameNameColors;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeIndex;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The formats of the mini-game pages ({@link MiniGameFormat}): the old ways to play migrated, the generated names,
 * matching a party's teams (any order, given to the sides deterministically) and out of a party (in order), the most
 * specific of several, the pipes each needs; then the round's name colours and the ways out by the linked pipes.
 */
public class MiniGameFormatGameTests implements FabricGameTest {
    private static final MiniGameFormat ONE_V_THREE = MiniGameFormat.teams(false, Side.exactly(1), Side.exactly(3));
    private static final MiniGameFormat TWO_V_TWO = MiniGameFormat.teams(false, Side.exactly(2), Side.exactly(2));

    private static String key(MiniGameFormat format) {
        return format.name().getContent() instanceof TranslatableTextContent content ? content.getKey() : format.name().getString();
    }

    private static List<Object> args(MiniGameFormat format) {
        return format.name().getContent() instanceof TranslatableTextContent content ? Arrays.asList(content.getArgs()) : List.of();
    }

    // ------------------------------------------------------------------ the model

    /** A page saved with ways to play and a players range gets formats: free for all keeps the range, teams of « 1 or more ». */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oldWaysToPlayBecomeFormats(TestContext context) {
        // Free for all (bit 0) and 2 teams (bit 1), 2 to 16 players (16: « any »)
        context.assertEquals(MiniGameFormat.migrate(0b11, 2, 16), List.of(MiniGameFormat.freeForAll(2, Side.INFINITE),
                MiniGameFormat.teams(false, Side.atLeast(1), Side.atLeast(1))), "free for all 2+, two teams of 1 or more");
        // 3 teams, at most 6 players: each team at most 4 (6 - the 2 others)
        context.assertEquals(MiniGameFormat.migrate(0b100, 1, 6), List.of(MiniGameFormat.teams(false, new Side(1, 4), new Side(1, 4), new Side(1, 4))),
                "3 teams capped so that together they don't exceed 6");
        context.assertEquals(MiniGameFormat.migrate(0, 1, 16), List.of(MiniGameFormat.freeForAll(1, Side.INFINITE)), "nothing ticked: free for all");

        // The saved form of a page from before: no Formats, its Modes and players instead
        NbtCompound old = MiniGamePageData.empty(UUID.randomUUID()).toNbt();
        old.remove("Formats");
        old.putInt("Format", 3);
        old.putInt("Modes", 0b1010);
        old.putInt("MinPlayers", 2);
        old.putInt("MaxPlayers", 8);
        MiniGamePageData migrated = MiniGamePageData.fromNbt(old);
        context.assertEquals(migrated.formats(), List.of(MiniGameFormat.teams(false, new Side(1, 7), new Side(1, 7)),
                MiniGameFormat.teams(false, new Side(1, 5), new Side(1, 5), new Side(1, 5), new Side(1, 5))), "2 and 4 teams, at most 8 players");
        context.assertEquals(MiniGamePageData.fromNbt(migrated.toNbt()), migrated, "saved again: the same formats");
        context.complete();
    }

    /** The names are generated from the formats, the ready-made as the hand-made. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void formatNamesAreGenerated(TestContext context) {
        context.assertEquals(key(MiniGameFormat.GALLERY.get(0)), "format.steveparty.duel", "1 v 1: Duel");
        context.assertEquals(key(MiniGameFormat.teams(false, Side.exactly(1), Side.atLeast(1))), "format.steveparty.one_vs_all", "1 v 1+: 1 contre tous");
        context.assertEquals(key(MiniGameFormat.teams(false, Side.atLeast(1), Side.exactly(1))), "format.steveparty.one_vs_all", "either order");
        context.assertEquals(key(ONE_V_THREE), "format.steveparty.vs", "1 contre 3");
        context.assertEquals(args(ONE_V_THREE).get(1), "3", "its numbers in it");
        context.assertEquals(key(MiniGameFormat.teams(true, Side.atLeast(2), Side.atLeast(2))), "format.steveparty.at_least", "Équipes égales 2+");
        context.assertEquals(key(MiniGameFormat.teams(true, Side.atLeast(1), Side.atLeast(1))), "format.steveparty.equal", "Équipes égales");
        context.assertEquals(key(MiniGameFormat.GALLERY.get(5)), "format.steveparty.teams", "3 équipes");
        context.assertEquals(key(MiniGameFormat.freeForAll(2, 8)), "format.steveparty.with_players", "Chacun pour soi · 2 à 8");
        context.assertEquals(key(MiniGameFormat.freeForAll(1, Side.INFINITE)), "format.steveparty.free_for_all", "Chacun pour soi");
        context.assertEquals(key(MiniGameFormat.allTogether(1, Side.INFINITE)), "format.steveparty.all_together", "Tous ensemble");
        context.assertEquals(new Side(2, 3).label() + " " + Side.atLeast(3).label() + " " + Side.exactly(2).label(), "2-3 3+ 2", "the sides' labels");
        context.complete();
    }

    /**
     * A party's teams fit a format in any order, given to its sides deterministically (the board's order when it fits);
     * out of a party the teams near the pipes fit in order.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void formatsMatchTeamsAndOrderTheirSides(TestContext context) {
        context.assertTrue(ONE_V_THREE.matches(List.of(1, 3)) && ONE_V_THREE.matchesInOrder(List.of(1, 3)), "1 and 3: fits as it is");
        context.assertTrue(ONE_V_THREE.matches(List.of(3, 1)), "3 and 1: fits, reordered");
        context.assertTrue(!ONE_V_THREE.matchesInOrder(List.of(3, 1)), "but not in order");
        context.assertTrue(Arrays.equals(ONE_V_THREE.assignment(List.of(1, 3)), new int[]{0, 1}), "the board's order kept when it fits");
        context.assertTrue(Arrays.equals(ONE_V_THREE.assignment(List.of(3, 1)), new int[]{1, 0}), "the lone group takes side A");
        context.assertTrue(!ONE_V_THREE.matches(List.of(2, 2)) && !ONE_V_THREE.matches(List.of(1, 3, 1)), "2 and 2, or three teams: no");
        MiniGameFormat ranges = MiniGameFormat.teams(false, new Side(1, 2), new Side(1, 2));
        context.assertTrue(Arrays.equals(ranges.assignment(List.of(2, 1)), new int[]{0, 1}), "both orders fit: the board's");
        MiniGameFormat equal = MiniGameFormat.teams(true, Side.atLeast(1), Side.atLeast(1));
        context.assertTrue(equal.matches(List.of(2, 2)) && !equal.matches(List.of(1, 3)), "same size");
        MiniGameFormat three = MiniGameFormat.teams(false, Side.exactly(1), Side.exactly(1), Side.exactly(2));
        context.assertTrue(Arrays.equals(three.assignment(List.of(2, 1, 1)), new int[]{1, 2, 0}), "first fitting order: A and B the first lone groups, C the pair");
        context.assertTrue(MiniGameFormat.freeForAll(2, 8).matches(List.of(5)) && !MiniGameFormat.freeForAll(2, 8).matches(List.of(9)), "free for all: the count");
        context.assertTrue(!MiniGameFormat.freeForAll(2, 8).matches(List.of(2, 3)) && MiniGameFormat.allTogether(2, 4).matches(List.of(4)),
                "no team for free for all, one side all together");

        // A party: board 3 positive (A) and 1 negative (B), page « 1 contre 3 »: the negative player plays side A
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(), n = UUID.randomUUID();
        TeamDisposition board = new TeamDisposition(Set.of(p1, p2, p3), Set.of(n));
        MiniGamePageData page = MiniGamePageData.empty(UUID.randomUUID()).withFormats(List.of(ONE_V_THREE)).withPipeLinks(List.of(
                link(0, MiniGamePipeRole.TEAM_A), link(1, MiniGamePipeRole.TEAM_B)));
        TeamDisposition played = MiniGamePartyStep.arrange(page, board);
        context.assertEquals(played.getTeamA(), Set.of(n), "the lone negative player: side A, the blue pipes");
        context.assertEquals(played.getTeamB(), Set.of(p1, p2, p3), "the three positive ones: side B, the red pipes");
        context.assertEquals(MiniGamePartyStep.arrange(page, new TeamDisposition(Set.of(n), Set.of(p1, p2, p3))),
                new TeamDisposition(Set.of(n), Set.of(p1, p2, p3)), "already in order: unchanged");
        context.complete();
    }

    private static MiniGamePipeLink link(int x, MiniGamePipeRole role) {
        return new MiniGamePipeLink(GlobalPos.create(net.minecraft.world.World.OVERWORLD, new BlockPos(x, 0, 0)), Direction.UP, role);
    }

    /** Of several formats that fit, the most specific is played; the draw only keeps pages with a fitting format and its pipes. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theMostSpecificFormatIsPlayed(TestContext context) {
        MiniGameFormat any = MiniGameFormat.blank();
        MiniGamePageData page = MiniGamePageData.empty(UUID.randomUUID()).withFormats(List.of(any, TWO_V_TWO, ONE_V_THREE))
                .withPipeLinks(List.of(link(0, MiniGamePipeRole.TEAM_A), link(1, MiniGamePipeRole.TEAM_B)));
        context.assertEquals(page.formatFor(List.of(2, 2)), 1, "2 and 2: « 2 contre 2 », not « 2 équipes »");
        context.assertEquals(page.formatFor(List.of(3, 1)), 2, "3 and 1: « 1 contre 3 »");
        context.assertEquals(page.formatFor(List.of(4, 1)), 0, "4 and 1: only « 2 équipes »");
        context.assertEquals(page.formatFor(List.of(5)), -1, "everyone together: none of them");
        MiniGamePageData noPipe = page.withPipeLinks(List.of(link(0, MiniGamePipeRole.TEAM_A)));
        context.assertEquals(noPipe.formatFor(List.of(2, 2)), -1, "without a team B pipe: not drawn");
        context.assertEquals(noPipe.missing(TWO_V_TWO), List.of(MiniGamePipeRole.TEAM_B), "the missing pipe");
        MiniGamePageData ffaToo = page.withFormats(List.of(MiniGameFormat.freeForAll(2, 8), MiniGameFormat.allTogether(2, 4)))
                .withPipeLinks(List.of(link(0, MiniGamePipeRole.PLAYERS)));
        context.assertEquals(ffaToo.formatFor(List.of(3)), 1, "3 together: all together (2 to 4) is narrower than free for all (2 to 8)");
        context.assertEquals(ffaToo.formatFor(List.of(6)), 0, "6: free for all");
        context.complete();
    }

    /** Out of a party the teams near the pipes are in order: « 1 contre 3 » wants 1 near the A pipes, 3 near the B ones. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void outOfAPartyTheTeamsAreInOrder(TestContext context) {
        MiniGamePageData page = MiniGamePageData.empty(UUID.randomUUID()).withFormats(List.of(ONE_V_THREE))
                .withPipeLinks(List.of(link(0, MiniGamePipeRole.TEAM_A), link(1, MiniGamePipeRole.TEAM_B)));
        UUID a = UUID.randomUUID(), b1 = UUID.randomUUID(), b2 = UUID.randomUUID(), b3 = UUID.randomUUID();
        Map<UUID, MiniGamePipeRole> right = new LinkedHashMap<>();
        right.put(a, MiniGamePipeRole.TEAM_A);
        right.put(b1, MiniGamePipeRole.TEAM_B);
        right.put(b2, MiniGamePipeRole.TEAM_B);
        right.put(b3, MiniGamePipeRole.TEAM_B);
        MiniGameTest.Plan plan = MiniGameTest.plan(page, right);
        context.assertTrue(plan.status() == MiniGameTest.Status.READY && plan.teams().getTeamA().equals(Set.of(a)), "1 at A, 3 at B: ready");
        Map<UUID, MiniGamePipeRole> wrong = new LinkedHashMap<>();
        wrong.put(a, MiniGamePipeRole.TEAM_B);
        wrong.put(b1, MiniGamePipeRole.TEAM_A);
        wrong.put(b2, MiniGamePipeRole.TEAM_A);
        wrong.put(b3, MiniGamePipeRole.TEAM_A);
        MiniGameTest.Plan not = MiniGameTest.plan(page, wrong);
        context.assertEquals(not.status(), MiniGameTest.Status.NOT_ENOUGH, "3 at A, 1 at B: not in order, not ready");
        context.assertEquals(not.shortfall(), new MiniGameTest.Shortfall(0, MiniGamePipeRole.TEAM_A.ordinal(), 3, 1, 1),
                "the closest: team A has 3, it wants 1");
        MiniGamePageData same = page.withFormats(List.of(MiniGameFormat.teams(true, Side.atLeast(1), Side.atLeast(1))));
        context.assertEquals(MiniGameTest.plan(same, right).shortfall(), new MiniGameTest.Shortfall(0, -1, 0, 0, 0), "the teams are not of the same size");
        context.complete();
    }

    // ------------------------------------------------------------------ the round: name colours, ways out

    private static ServerPlayerEntity player(TestContext context, double x, double y, double z) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        Vec3d abs = context.getAbsolute(new Vec3d(x, y, z));
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, 0, 0);
        return player;
    }

    private static void remove(TestContext context, ServerPlayerEntity player) {
        if (player.hasVehicle()) player.stopRiding();
        MiniGamePipes.leaveParty(player.getUuid());
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    /** A pipe of {@code block} standing on stone: a mouth on top. */
    private static BlockPos mouth(TestContext context, Block block, int x, int z) {
        context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos pos = new BlockPos(x, 2, z);
        context.setBlockState(pos, block.getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        return pos;
    }

    private static GlobalPos global(TestContext context, BlockPos relative) {
        return GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(relative));
    }

    private static boolean near(TestContext context, ServerPlayerEntity player, BlockPos mouth) {
        Vec3d at = context.getRelative(player.getPos());
        return !player.hasVehicle() && Math.abs(at.x - (mouth.getX() + 0.5)) < 1.2 && Math.abs(at.z - (mouth.getZ() + 0.5)) < 1.2
                && at.y >= mouth.getY() + 0.9;
    }

    private static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        if (condition.getAsBoolean()) then.run();
        else if (ticks <= 0) context.throwGameTestException(what);
        else context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
    }

    /**
     * A round's player takes the colour of the pipe he came out of (a team of the scoreboard): taken away when he leaves
     * the round, the team he had before given back.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void namesTakeTheColourOfTheirPipe(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        Scoreboard scoreboard = server.getScoreboard();
        ServerPlayerEntity player = player(context, 2.5, 3, 2.5);
        Team before = scoreboard.getTeam("steveparty_test_before");
        if (before == null) before = scoreboard.addTeam("steveparty_test_before");
        try {
            String name = player.getNameForScoreboard();
            scoreboard.addScoreHolderToTeam(name, before);
            UUID page = UUID.randomUUID();
            MiniGamePages.update(server, MiniGamePageData.empty(page).withPipeLinks(List.of(new MiniGamePipeLink(global(context, new BlockPos(2, 2, 2)),
                    Direction.UP, MiniGamePipeRole.TEAM_A))));
            MiniGameNameColors.apply(player, MiniGamePipeRole.TEAM_A, page);
            Team side = scoreboard.getScoreHolderTeam(name);
            context.assertTrue(side != null && side.getName().equals(MiniGameNameColors.PREFIX + "team_a") && side.getColor() == Formatting.BLUE,
                    "out of a team A pipe: blue");
            MiniGameNameColors.apply(player, MiniGamePipeRole.TEAM_B, page);
            context.assertEquals(scoreboard.getScoreHolderTeam(name).getColor(), Formatting.RED, "then a team B pipe: red");
            MiniGameNameColors.restore(server, player.getUuid());
            context.assertEquals(scoreboard.getScoreHolderTeam(name), before, "the round over: its team back");
            context.assertTrue(scoreboard.getTeam(MiniGameNameColors.PREFIX + "team_b") == null, "the side's team, empty, is gone");

        } finally {
            MiniGameNameColors.restore(server, player.getUuid());
            scoreboard.removeTeam(before);
            remove(context, player);
        }
        context.complete();
    }
}
