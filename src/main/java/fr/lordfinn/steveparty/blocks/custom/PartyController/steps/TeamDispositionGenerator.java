package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The teams a mini-game can be played in, from the tiles the players' tokens stand on:
 * <ul>
 *     <li><b>two teams</b>: the players on a positive tile are team A, those on a negative one team B, always (1
 *     against 3 included); each player on a neutral tile can go to either team, which gives several ways to split;
 *     </li>
 *     <li><b>free for all</b> when that leaves a team empty;</li>
 *     <li><b>three or four teams</b>, with exactly three or four players: one player per team. The first positive
 *     player is team A, the first negative one team B, the others take the free teams in turn order.</li>
 * </ul>
 */
public class TeamDispositionGenerator {
    /** A player and the kind of tile its token stands on. */
    public record Seat(UUID player, ABoardSpaceBehavior.Status status) {
    }

    private static final int MAX_NEUTRAL_SPLITS = 10;

    /**
     * @param seats the players in turn order
     * @return every way to play, without duplicates, in a steady order
     */
    public static Set<TeamDisposition> generateTeamDispositions(List<Seat> seats) {
        Set<TeamDisposition> dispositions = new LinkedHashSet<>();
        if (seats.isEmpty()) return dispositions;
        List<UUID> everyone = seats.stream().map(Seat::player).toList();
        List<UUID> neutral = seats.stream().filter(seat -> seat.status() == ABoardSpaceBehavior.Status.NEUTRAL).map(Seat::player).toList();

        // Two teams: every way to send the neutral players to A or B
        int splits = 1 << Math.min(neutral.size(), MAX_NEUTRAL_SPLITS);
        for (int mask = 0; mask < splits; mask++) {
            Set<UUID> teamA = new LinkedHashSet<>(), teamB = new LinkedHashSet<>();
            for (Seat seat : seats) {
                boolean toA = switch (seat.status()) {
                    case GOOD -> true;
                    case BAD -> false;
                    case NEUTRAL -> {
                        int index = neutral.indexOf(seat.player());
                        yield index < MAX_NEUTRAL_SPLITS && (mask & (1 << index)) != 0;
                    }
                };
                (toA ? teamA : teamB).add(seat.player());
            }
            dispositions.add(teamA.isEmpty() || teamB.isEmpty() ? TeamDisposition.freeForAll(everyone) : new TeamDisposition(teamA, teamB));
        }

        // Three or four teams: one player each
        if (seats.size() == 3 || seats.size() == 4) dispositions.add(onePlayerPerTeam(seats));
        return dispositions;
    }

    private static TeamDisposition onePlayerPerTeam(List<Seat> seats) {
        UUID[] teams = new UUID[4];
        List<UUID> others = new ArrayList<>();
        for (Seat seat : seats) {
            if (seat.status() == ABoardSpaceBehavior.Status.GOOD && teams[0] == null) teams[0] = seat.player();
            else if (seat.status() == ABoardSpaceBehavior.Status.BAD && teams[1] == null) teams[1] = seat.player();
            else others.add(seat.player());
        }
        int next = 0;
        for (int team = 0; team < seats.size(); team++) {
            if (teams[team] == null) teams[team] = others.get(next++);
        }
        return new TeamDisposition(Set.of(teams[0]), Set.of(teams[1]), Set.of(teams[2]), seats.size() == 4 ? Set.of(teams[3]) : Set.of());
    }
}
