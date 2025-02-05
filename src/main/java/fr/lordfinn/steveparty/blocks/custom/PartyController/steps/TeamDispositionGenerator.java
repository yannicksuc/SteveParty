package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

public class TeamDispositionGenerator {
    public static Set<TeamDisposition> generateTeamDispositions(
            Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners,
            List<ABoardSpaceBehavior.Status> statuses) {

        // Categorize players based on statuses
        List<UUID> bad = new ArrayList<>();
        List<UUID> good = new ArrayList<>();
        List<UUID> neutral = new ArrayList<>();

        int index = 0;
        for (Map.Entry<TokenizedEntityInterface, PlayerEntity> entry : tokensWithOwners.entrySet()) {
            PlayerEntity player = entry.getValue();
            ABoardSpaceBehavior.Status status = statuses.get(index++);

            switch (status) {
                case BAD -> bad.add(player.getUuid());
                case GOOD -> good.add(player.getUuid());
                case NEUTRAL -> neutral.add(player.getUuid());
            }
        }

        return generateTeamDispositionsFromLists(bad, good, neutral);
    }

    private static Set<TeamDisposition> generateTeamDispositionsFromLists(
            List<UUID> bad, List<UUID> good, List<UUID> neutral) {

        Set<TeamDisposition> teamDispositions = new HashSet<>();
        int neutralCount = neutral.size();
        int maxStates = 1 << neutralCount; // 2^neutralCount possible distributions

        for (int mask = 0; mask < maxStates; mask++) {
            Set<UUID> teamA = new HashSet<>(bad);
            Set<UUID> teamB = new HashSet<>(good);

            for (int i = 0; i < neutralCount; i++) {
                if ((mask & (1 << i)) != 0) {
                    teamA.add(neutral.get(i)); // Assign neutral player to team A
                } else {
                    teamB.add(neutral.get(i)); // Assign neutral player to team B
                }
            }

            teamDispositions.add(new TeamDisposition(teamA, teamB));
        }

        return teamDispositions;
    }
}
