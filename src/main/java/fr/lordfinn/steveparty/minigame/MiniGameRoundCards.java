package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads.RoundCard;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The card of a mini-game round at the top of its players' screens ({@link RoundCard}): its name, its objective, the
 * player's team and how it is played (free for all, 2 against 2...), read from the pipes everyone comes out of.
 */
public final class MiniGameRoundCards {
    private MiniGameRoundCards() {
    }

    /**
     * The card of {@code player} in a round of {@code page} played by {@code teams}, everyone coming out of the pipes
     * {@code pipes} (his own among them: his team; a spectators pipe: he watches).
     *
     * @param players the players of the round (the others in {@code pipes} watch)
     */
    public static RoundCard of(MiniGamePageData page, @Nullable TeamDisposition teams, Map<UUID, MiniGamePipeLink> pipes,
                               List<UUID> players, UUID player) {
        // The players of each team, by the pipes they come out of
        int[] perTeam = new int[4];
        for (UUID each : players) {
            MiniGamePipeLink link = pipes.get(each);
            int team = link == null ? -1 : teamOf(link.role());
            if (team >= 0) perTeam[team]++;
        }
        List<Integer> sizes = new ArrayList<>();
        for (int count : perTeam) if (count > 0) sizes.add(count);
        int kind;
        if (sizes.size() >= 2) {
            kind = MiniGameFormat.Kind.TEAMS.ordinal();
        } else {
            MiniGameFormat format = teams == null ? null : page.format(page.formatFor(MiniGamePartyStep.counts(teams)));
            kind = format == null || format.kind() == MiniGameFormat.Kind.TEAMS ? MiniGameFormat.Kind.FREE_FOR_ALL.ordinal() : format.kind().ordinal();
            sizes = List.of(players.size());
        }
        MiniGamePipeLink own = pipes.get(player);
        int team = !players.contains(player) ? RoundCard.SPECTATOR
                : kind == MiniGameFormat.Kind.TEAMS.ordinal() && own != null ? teamOf(own.role()) : RoundCard.NO_TEAM;
        return new RoundCard(true, page.title(), objective(page), team, kind, sizes);
    }

    /** A card with only the page's name and objective (a round whose pipes are not known). */
    public static RoundCard bare(MiniGamePageData page) {
        return new RoundCard(true, page.title(), objective(page), RoundCard.NO_TEAM, -1, List.of());
    }

    /** What to do: the first sentence of the page's description (markup), empty if it has none. */
    public static String objective(MiniGamePageData page) {
        List<String> sentences = MiniGameText.sentences(page.description());
        return sentences.isEmpty() ? "" : sentences.getFirst();
    }

    /** The team of a pipe's role (0: A ... 3: D), -1 for the others. */
    private static int teamOf(MiniGamePipeRole role) {
        return switch (role) {
            case TEAM_A -> 0;
            case TEAM_B -> 1;
            case TEAM_C -> 2;
            case TEAM_D -> 3;
            default -> -1;
        };
    }
}
