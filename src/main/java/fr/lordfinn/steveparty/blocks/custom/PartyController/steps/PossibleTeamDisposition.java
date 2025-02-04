package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

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

    public Text toText() {
        if (teamA == null || teamB == null) return Text.empty();
        if (teamA.isEmpty()) return Text.translatable("message.steveparty.free_for_all");
        MutableText str = Text.literal(teamA.size() + " ").append(Text.translatable("message.steveparty.vs").getLiteralString()).append(" " + " " + teamB.size() + "\n");
        str.append(teamA + " ").append(Text.translatable("message.steveparty.vs").getLiteralString()).append(" " + getTeamPlayersNames(teamB));
        return str;
    }

    @Override
    public String toString() {
        return toText().toString();
    }

    private @NotNull String getTeamPlayersNames(Set<PlayerEntity> team) {
        return team.stream()
                .map(p -> p.hasCustomName() ? p.getCustomName().getString() : p.getName().toString())
                .reduce((s1, s2) -> s1 + ", " + s2).orElseGet(() -> "");
    }
}
