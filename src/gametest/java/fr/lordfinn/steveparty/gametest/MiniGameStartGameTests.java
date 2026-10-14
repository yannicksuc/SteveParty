package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGameFreeze;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGameReady;
import fr.lordfinn.steveparty.minigame.MiniGameRoundCards;
import fr.lordfinn.steveparty.minigame.MiniGameText;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads.RoundCard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.GameMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The start of a mini-game round (playtest 3): the players stand still while the chat tells the description sentence
 * by sentence, « Ready » from all of them skips the rest, then a 5 second countdown (3 in a fast party); the card at
 * the top of the screen (name, objective, team, kind).
 */
public class MiniGameStartGameTests implements SteveGameTest {
    private static ServerPlayerEntity player(TestContext context, String name) {
        ServerPlayerEntity player = TestPlayers.joined(context, "start", name, GameMode.ADVENTURE, 1.5, 1, 1.5);
        TestPlayers.removeAtEnd(context, player);
        return player;
    }

    private static MiniGamePageData page(String description) {
        return MiniGamePageData.empty(UUID.randomUUID()).withTexts("Hot Potato", description);
    }

    /** A start with {@code players}, still on until the test ends. */
    private static void start(TestContext context, MiniGamePageData page, MiniGameFreeze.Pace pace, ServerPlayerEntity... players) {
        AtomicBoolean on = new AtomicBoolean(true);
        TestCleanup.atEnd(context, () -> on.set(false));
        MiniGameFreeze.start(context.getWorld().getServer(), List.of(players), List.of(), page.title(), page, pace, on::get);
    }

    /** The description is cut in sentences, each keeping the colour and styles in force where it starts. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theDescriptionIsCutInSentences(TestContext context) {
        context.assertEquals(MiniGameText.sentences("&cRun! Grab the &lstar. Win...\nThen go"),
                List.of("&cRun!", "&cGrab the &lstar.", "&c&lWin...", "&c&lThen go"), "sentences, codes carried on");
        context.assertEquals(MiniGameText.sentences("Score 1.5 points.  "), List.of("Score 1.5 points."), "no cut inside a number");
        context.assertEquals(MiniGameText.sentences("  "), List.of(), "nothing to tell");
        context.assertTrue(MiniGameText.readingTicks("Go!") == 40 && MiniGameText.readingTicks("x".repeat(400)) == 160,
                "from 2 to 8 seconds to read");
        context.complete();
    }

    /** The players stand still while the description is told; once all of them are ready, the 5 second countdown. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
    public void everyoneReadySkipsTheDescriptionThenFiveSeconds(TestContext context) {
        ServerPlayerEntity alice = player(context, "A"), bob = player(context, "B");
        start(context, page("Catch the potato. Throw it before it burns. The last one holding it loses."), MiniGameFreeze.Pace.FIRST, alice, bob);
        context.waitAndRun(15, () -> {
            context.assertTrue(MiniGameFreeze.isFrozen(alice) && MiniGameFreeze.isFrozen(bob), "they stand still while it is told");
            context.assertTrue(MiniGameReady.canVote(alice), "they may say they are ready");
            context.getWorld().getServer().getCommandManager().executeWithPrefix(alice.getCommandSource(), MiniGameReady.COMMAND);
            context.assertTrue(MiniGameReady.canVote(bob), "one ready out of two: still told");
            context.assertTrue(MiniGameReady.press(bob), "bob says it too (the key)");
            context.assertTrue(!MiniGameReady.canVote(alice), "everyone ready: the countdown");
            context.waitAndRun(MiniGameFreeze.SECONDS * 20 - 10, () -> {
                context.assertTrue(MiniGameFreeze.isFrozen(alice), "still counting down");
                context.waitAndRun(15, () -> {
                    context.assertTrue(!MiniGameFreeze.isFrozen(alice) && !MiniGameFreeze.isFrozen(bob), "Go!");
                    context.complete();
                });
            });
        });
    }

    /** Nobody ready: each sentence is given its reading time before the countdown. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void theDescriptionTakesItsReadingTime(TestContext context) {
        ServerPlayerEntity alice = player(context, "A");
        start(context, page("One. Two."), MiniGameFreeze.Pace.FIRST, alice);
        // 10 ticks before it speaks, then 2 seconds per short sentence
        context.waitAndRun(10 + 2 * 40 - 5, () -> {
            context.assertTrue(MiniGameReady.canVote(alice), "still told");
            context.waitAndRun(10, () -> {
                context.assertTrue(!MiniGameReady.canVote(alice) && MiniGameFreeze.isFrozen(alice), "told: the countdown");
                context.complete();
            });
        });
    }

    /** « Y » typed in the chat is a vote, other words are not. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void typingYIsReady(TestContext context) {
        context.assertTrue(MiniGameReady.isReadyWord("Y") && MiniGameReady.isReadyWord(" y ") && !MiniGameReady.isReadyWord("yes"),
                "only « Y »");
        context.complete();
    }

    /** A fast party: no reading time, no vote, 3 seconds. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void aFastStartTakesThreeSeconds(TestContext context) {
        ServerPlayerEntity alice = player(context, "A");
        start(context, page("Catch the potato. Throw it before it burns."), MiniGameFreeze.Pace.FIRST.fast(), alice);
        context.waitAndRun(12, () -> {
            context.assertTrue(MiniGameFreeze.isFrozen(alice) && !MiniGameReady.canVote(alice), "the countdown at once");
            context.waitAndRun(MiniGameFreeze.FAST_SECONDS * 20 - 2 + 3, () -> {
                context.assertTrue(!MiniGameFreeze.isFrozen(alice), "Go! after 3 seconds");
                context.complete();
            });
        });
    }

    /** The card: name, objective (first sentence), the player's team by his pipe, and how it is played. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theCardTellsTheTeamAndTheKind(TestContext context) {
        MiniGamePageData page = page("&eCatch the potato. Throw it.");
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID(), b1 = UUID.randomUUID(), b2 = UUID.randomUUID(), watcher = UUID.randomUUID();
        GlobalPos at = GlobalPos.create(context.getWorld().getRegistryKey(), BlockPos.ORIGIN);
        Map<UUID, MiniGamePipeLink> pipes = new LinkedHashMap<>();
        pipes.put(a1, new MiniGamePipeLink(at, Direction.UP, MiniGamePipeRole.TEAM_A));
        pipes.put(a2, new MiniGamePipeLink(at, Direction.UP, MiniGamePipeRole.TEAM_A));
        pipes.put(b1, new MiniGamePipeLink(at, Direction.UP, MiniGamePipeRole.TEAM_B));
        pipes.put(b2, new MiniGamePipeLink(at, Direction.UP, MiniGamePipeRole.TEAM_B));
        pipes.put(watcher, new MiniGamePipeLink(at, Direction.UP, MiniGamePipeRole.SPECTATORS));
        List<UUID> players = List.of(a1, a2, b1, b2);
        TeamDisposition teams = new TeamDisposition(Set.of(a1, a2), Set.of(b1, b2));
        RoundCard card = MiniGameRoundCards.of(page, teams, pipes, players, b1);
        context.assertEquals(card.title(), "Hot Potato", "its name");
        context.assertEquals(card.objective(), "&eCatch the potato.", "its objective: the first sentence");
        context.assertEquals(card.team(), 1, "team B, by his pipe");
        context.assertEquals(card.kind(), MiniGameFormat.Kind.TEAMS.ordinal(), "teams");
        context.assertEquals(card.sizes(), List.of(2, 2), "2 against 2");
        context.assertEquals(MiniGameRoundCards.of(page, teams, pipes, players, watcher).team(), RoundCard.SPECTATOR, "the audience watches");

        Map<UUID, MiniGamePipeLink> free = new LinkedHashMap<>();
        players.forEach(uuid -> free.put(uuid, new MiniGamePipeLink(at, Direction.UP, MiniGamePipeRole.PLAYERS)));
        RoundCard ffa = MiniGameRoundCards.of(page, TeamDisposition.freeForAll(players), free, players, a1);
        context.assertTrue(ffa.team() == RoundCard.NO_TEAM && ffa.kind() == MiniGameFormat.Kind.FREE_FOR_ALL.ordinal() && ffa.sizes().equals(List.of(4)),
                "free for all: no team, 4 players");
        context.complete();
    }
}
