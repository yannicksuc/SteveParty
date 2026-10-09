package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The teams of a mini-game. Team A is the team of the players on a positive tile, team B of those on a negative
 * one (see {@link TeamDispositionGenerator}); teams C and D only exist in 3- and 4-team mini-games. Free for all:
 * no team A, everyone in « team B » ({@link #freeForAll}).
 */
public class TeamDisposition {
    private static final Codec<Set<UUID>> TEAM = Codec.STRING.listOf().xmap(
            list -> list.stream().map(UUID::fromString).collect(Collectors.toCollection(LinkedHashSet::new)),
            set -> set.stream().map(UUID::toString).collect(Collectors.toList()));

    public static final Codec<TeamDisposition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TEAM.fieldOf("teamA").forGetter(t -> t.teamA),
            TEAM.fieldOf("teamB").forGetter(t -> t.teamB),
            TEAM.optionalFieldOf("teamC", Set.of()).forGetter(t -> t.teamC),
            TEAM.optionalFieldOf("teamD", Set.of()).forGetter(t -> t.teamD)
    ).apply(instance, TeamDisposition::new));

    final Set<UUID> teamA;
    final Set<UUID> teamB;
    final Set<UUID> teamC;
    final Set<UUID> teamD;

    public TeamDisposition(Set<UUID> teamA, Set<UUID> teamB) {
        this(teamA, teamB, Set.of(), Set.of());
    }

    public TeamDisposition(Set<UUID> teamA, Set<UUID> teamB, Set<UUID> teamC, Set<UUID> teamD) {
        this.teamA = teamA;
        this.teamB = teamB;
        this.teamC = teamC;
        this.teamD = teamD;
    }

    /** Everyone for themselves. */
    public static TeamDisposition freeForAll(Collection<UUID> players) {
        return new TeamDisposition(Set.of(), new LinkedHashSet<>(players));
    }

    /** Team A: the positive players (empty in free for all). */
    public Set<UUID> getTeamA() {
        return Collections.unmodifiableSet(teamA);
    }

    /** Team B: the negative players (everyone in free for all). */
    public Set<UUID> getTeamB() {
        return Collections.unmodifiableSet(teamB);
    }

    /** The four teams, A to D (the unused ones empty). */
    public List<Set<UUID>> teams() {
        return List.of(getTeamA(), getTeamB(), Collections.unmodifiableSet(teamC), Collections.unmodifiableSet(teamD));
    }

    /** Number of teams with players. */
    public int teamCount() {
        int count = 0;
        for (Set<UUID> team : teams()) if (!team.isEmpty()) count++;
        return count;
    }

    /** @return true when there are no teams: everyone plays for themselves. */
    public boolean isFreeForAll() {
        return teamCount() < 2;
    }

    /** The team of a player (0: A ... 3: D), -1 if it is in none. */
    public int teamOf(UUID player) {
        List<Set<UUID>> teams = teams();
        for (int i = 0; i < teams.size(); i++) if (teams.get(i).contains(player)) return i;
        return -1;
    }

    /** Number of players. */
    public int size() {
        return teamA.size() + teamB.size() + teamC.size() + teamD.size();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TeamDisposition other)) return false;
        return this.teamA.equals(other.teamA) && this.teamB.equals(other.teamB) && this.teamC.equals(other.teamC) && this.teamD.equals(other.teamD);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamA, teamB, teamC, teamD);
    }

    public Text toText(MinecraftServer server) {
        if (isFreeForAll()) return Text.translatable("message.steveparty.free_for_all");
        MutableText sizes = Text.empty(), names = Text.empty();
        boolean first = true;
        for (Set<UUID> team : teams()) {
            if (team.isEmpty()) continue;
            if (!first) {
                sizes.append(" ").append(vsText()).append(" ");
                names.append(" ").append(vsText()).append(" ");
            }
            sizes.append(String.valueOf(team.size()));
            names.append(getTeamPlayersNames(team, server));
            first = false;
        }
        return sizes.append("\n").append(names);
    }

    private static MutableText vsText() {
        return Text.translatableWithFallback("message.steveparty.vs", "vs");
    }

    @Override
    public String toString() {
        if (isFreeForAll()) return "free for all (" + size() + ")";
        return teams().stream().filter(team -> !team.isEmpty()).map(team -> String.valueOf(team.size())).collect(Collectors.joining(" vs "));
    }

    private @NotNull String getTeamPlayersNames(Set<UUID> team, MinecraftServer server) {
        return team.stream()
                .map(server.getPlayerManager()::getPlayer)
                .filter(Objects::nonNull)
                .map(p -> p.hasCustomName() ? p.getCustomName().getString() : p.getName().getString())
                .reduce((s1, s2) -> s1 + ", " + s2).orElseGet(() -> "");
    }
}
