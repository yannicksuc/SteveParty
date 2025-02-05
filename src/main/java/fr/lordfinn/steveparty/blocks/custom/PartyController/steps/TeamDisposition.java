package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TeamDisposition {
    public static final Codec<TeamDisposition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().xmap(
                    list -> list.stream().map(UUID::fromString).collect(Collectors.toSet()),
                    set -> set.stream().map(UUID::toString).collect(Collectors.toList())
            ).fieldOf("teamA").forGetter(t -> t.teamA),
            Codec.STRING.listOf().xmap(
                    list -> list.stream().map(UUID::fromString).collect(Collectors.toSet()),
                    set -> set.stream().map(UUID::toString).collect(Collectors.toList())
            ).fieldOf("teamB").forGetter(t -> t.teamB)
    ).apply(instance, TeamDisposition::new));
    Set<UUID> teamA;
    Set<UUID> teamB;

    public TeamDisposition(Set<UUID> teamA, Set<UUID> teamB) {
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
        if (!(obj instanceof TeamDisposition other)) return false;
        return this.teamA.equals(other.teamA) && this.teamB.equals(other.teamB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamA, teamB);
    }

    public Text toText(MinecraftServer server) {
        if (teamA == null || teamB == null) return Text.empty();
        if (teamA.isEmpty()) return Text.translatable("message.steveparty.free_for_all");
        MutableText str = Text.literal(teamA.size() + " ").append(Text.translatable("message.steveparty.vs").getLiteralString()).append(" " + " " + teamB.size() + "\n");
        str.append(getTeamPlayersNames(teamA, server) + " ").append(Text.translatable("message.steveparty.vs").getLiteralString()).append(" " + getTeamPlayersNames(teamB, server));
        return str;
    }

    @Override
    public String toString() {
        return Text.literal(teamA.size() + " ").append(Text.translatable("message.steveparty.vs").getLiteralString()).append(" " + " " + teamB.size() + "\n").toString();
    }

    private @NotNull String getTeamPlayersNames(Set<UUID> team, MinecraftServer server) {
        return team.stream()
                .map(server.getPlayerManager()::getPlayer)
                .filter(Objects::nonNull)
                .map(p -> p.hasCustomName() ? p.getCustomName().getString() : p.getName().toString())
                .reduce((s1, s2) -> s1 + ", " + s2).orElseGet(() -> "");
    }
}
