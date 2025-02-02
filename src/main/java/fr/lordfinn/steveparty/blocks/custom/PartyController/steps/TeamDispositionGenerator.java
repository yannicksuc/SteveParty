package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

public class TeamDispositionGenerator {
    public static Set<PossibleTeamDisposition> generateTeamDispositions(
            Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners,
            List<ABoardSpaceBehavior.Status> statuses) {

        // Categorize players based on statuses
        List<PlayerEntity> bad = new ArrayList<>();
        List<PlayerEntity> good = new ArrayList<>();
        List<PlayerEntity> neutral = new ArrayList<>();

        int index = 0;
        for (Map.Entry<TokenizedEntityInterface, PlayerEntity> entry : tokensWithOwners.entrySet()) {
            PlayerEntity player = entry.getValue();
            ABoardSpaceBehavior.Status status = statuses.get(index++);

            switch (status) {
                case BAD -> bad.add(player);
                case GOOD -> good.add(player);
                case NEUTRAL -> neutral.add(player);
            }
        }

        return generateTeamDispositionsFromLists(bad, good, neutral);
    }

    private static Set<PossibleTeamDisposition> generateTeamDispositionsFromLists(
            List<PlayerEntity> bad, List<PlayerEntity> good, List<PlayerEntity> neutral) {

        Set<PossibleTeamDisposition> teamDispositions = new HashSet<>();
        int neutralCount = neutral.size();
        int maxStates = 1 << neutralCount; // 2^neutralCount possible distributions

        for (int mask = 0; mask < maxStates; mask++) {
            Set<PlayerEntity> teamA = new HashSet<>(bad);
            Set<PlayerEntity> teamB = new HashSet<>(good);

            for (int i = 0; i < neutralCount; i++) {
                if ((mask & (1 << i)) != 0) {
                    teamA.add(neutral.get(i)); // Assign neutral player to team A
                } else {
                    teamB.add(neutral.get(i)); // Assign neutral player to team B
                }
            }

            teamDispositions.add(new PossibleTeamDisposition(teamA, teamB));
        }

        return teamDispositions;
    }
}
