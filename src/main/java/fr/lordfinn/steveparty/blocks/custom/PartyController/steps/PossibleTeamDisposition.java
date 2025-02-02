package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import net.minecraft.entity.player.PlayerEntity;

import java.util.Objects;
import java.util.Set;

public class PossibleTeamDisposition {
    Set<PlayerEntity> teamA;
    Set<PlayerEntity> teamB;

    public PossibleTeamDisposition(Set<PlayerEntity> teamA, Set<PlayerEntity> teamB) {
        // Normalize: Ensure teamA is always the smaller one to avoid duplicates
        if (teamA.size() > teamB.size()) {
            this.teamA = teamB;
            this.teamB = teamA;
        } else {
            this.teamA = teamA;
            this.teamB = teamB;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PossibleTeamDisposition other)) return false;
        return this.teamA.equals(other.teamA) && this.teamB.equals(other.teamB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamA, teamB);
    }

    @Override
    public String toString() {
        return "Team A: " + teamA + " | Team B: " + teamB;
    }
}
