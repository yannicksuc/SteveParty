package fr.lordfinn.steveparty.minigame;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * During a mini-game round (a party's, its practice round, or one played out of a party), each player's name takes the
 * colour of the pipe he came out of: team A blue, B red, C purple, D orange, the players pipes green, the spectators
 * grey. Above his head, in the player list and in the chat.
 * <p>
 * <b>Leaving the round takes the colour away</b> (the team he had before given back): the end of the round, its stop,
 * a disconnection, the server stopping. No pipe takes it away: during a round the pipes linked to its page are closed
 * to its players ({@code MiniGamePipes}).
 * <p>
 * It is a vanilla scoreboard team per colour ({@code steveparty_side_<role>}): colour only (no prefix, friendly fire,
 * collisions and name tags as the vanilla default). A player already in a team (another mod's, a map's) is taken out
 * of it for the round and put back in it after ({@link #restore}): when he leaves the round, disconnects, or when the
 * server stops. A team colour is the robust way: every client (vanilla included) shows it at
 * the three places, with nothing to send. Teams left by a crash are removed when the server starts again (their
 * players then lose the team they had before, which the crash forgot).
 */
public final class MiniGameNameColors {
    public static final String PREFIX = "steveparty_side_";

    /** Each coloured player: the team he was in before (empty: none), his name (the scoreboard's key), his round's page. */
    private record Before(String team, String name, UUID page) {
    }

    private static final Map<UUID, Before> COLOURED = new HashMap<>();
    private static @Nullable MinecraftServer server;

    private MiniGameNameColors() {
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            removeStale(started);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            for (UUID uuid : new ArrayList<>(COLOURED.keySet())) restore(stopping, uuid);
            server = null;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, disconnecting) -> restore(disconnecting, handler.player.getUuid()));
    }

    /** The colour of a role's pipes. */
    public static Formatting colorOf(MiniGamePipeRole role) {
        return switch (role) {
            case TEAM_A -> Formatting.BLUE;
            case TEAM_B -> Formatting.RED;
            case TEAM_C -> Formatting.DARK_PURPLE;
            case TEAM_D -> Formatting.GOLD;
            case SPECTATORS -> Formatting.GRAY;
            default -> Formatting.GREEN;
        };
    }

    /** {@code player} came out of a pipe of {@code role} for the round of {@code page}: his name takes its colour until {@link #restore}. */
    public static void apply(ServerPlayerEntity player, MiniGamePipeRole role, UUID page) {
        MinecraftServer owner = player.getServer();
        if (owner == null) return;
        Scoreboard scoreboard = owner.getScoreboard();
        String name = player.getNameForScoreboard();
        String teamName = PREFIX + role.name().toLowerCase(java.util.Locale.ROOT);
        Team current = scoreboard.getScoreHolderTeam(name);
        if (current != null && current.getName().equals(teamName)) return;
        if (!COLOURED.containsKey(player.getUuid())) {
            COLOURED.put(player.getUuid(), new Before(current == null || current.getName().startsWith(PREFIX) ? "" : current.getName(), name, page));
        }
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.addTeam(teamName);
            team.setColor(colorOf(role));
        }
        scoreboard.addScoreHolderToTeam(name, team);
    }

    /** {@code uuid} leaves the round: his name gets its own colour back, and the team he had before. */
    public static void restore(UUID uuid) {
        if (server != null) restore(server, uuid);
    }

    public static void restore(MinecraftServer owner, UUID uuid) {
        Before before = COLOURED.remove(uuid);
        if (before == null) return;
        Scoreboard scoreboard = owner.getScoreboard();
        Team current = scoreboard.getScoreHolderTeam(before.name());
        // Put in another team meanwhile (or out of every team: an operator, a map), he stays as he was put: the round
        // gives back only the team it took him from, never takes him out of one it did not put him in
        if (current == null || !current.getName().startsWith(PREFIX)) return;
        scoreboard.removeScoreHolderFromTeam(before.name(), current);
        if (current.getPlayerList().isEmpty()) scoreboard.removeTeam(current);
        Team previous = before.team().isEmpty() ? null : scoreboard.getTeam(before.team());
        if (previous != null) scoreboard.addScoreHolderToTeam(before.name(), previous);
    }

    /** @return true if {@code uuid}'s name is coloured by a round now. */
    public static boolean isColoured(UUID uuid) {
        return COLOURED.containsKey(uuid);
    }

    /** The teams of this class left by a crash. */
    private static void removeStale(MinecraftServer started) {
        Scoreboard scoreboard = started.getScoreboard();
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith(PREFIX)) scoreboard.removeTeam(team);
        }
        COLOURED.clear();
    }
}
