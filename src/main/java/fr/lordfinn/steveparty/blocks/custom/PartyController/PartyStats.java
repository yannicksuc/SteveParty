package fr.lordfinn.steveparty.blocks.custom.PartyController;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What each pawn of a party did, collected while it plays for an end-of-party infographic and bonus awards (most coins,
 * most spaces of a kind, mini-games won or lost...): nothing shows it yet. Saved with the party, cleared when a new one
 * starts. Filled by {@code PartyStatsCollector}.
 * <p>
 * The coins and stars are a timeline: one value per {@linkplain #points() point} (the party's start, each round's start,
 * each mini-game's end, the party's end), the same index for every pawn (a pawn that joined later is padded with 0).
 */
public final class PartyStats {
    /** A point of the timeline: what happened ({@link Kind}) and the round it happened in (0 before the first). */
    public record Point(Kind kind, int round) {
    }

    public enum Kind { START, ROUND, MINI_GAME, END }

    /** What one pawn did. */
    public static final class Player {
        private @Nullable UUID owner;
        private String name = "";
        private final List<Integer> coins = new ArrayList<>(), stars = new ArrayList<>();
        /** Landings (stops) by board space role id ({@code steveparty:tile_star}...). */
        private final Map<String, Integer> landings = new LinkedHashMap<>();
        /** Items used (power-ups, by item id). */
        private final Map<String, Integer> itemsUsed = new LinkedHashMap<>();
        private int spacesWalked, diceRolls, rollTotal, bestRoll;
        private int miniGamesPlayed, miniGamesWon, miniGamesLost, placesTotal;

        public @Nullable UUID owner() {
            return owner;
        }

        public String name() {
            return name;
        }

        public List<Integer> coins() {
            return Collections.unmodifiableList(coins);
        }

        public List<Integer> stars() {
            return Collections.unmodifiableList(stars);
        }

        public Map<String, Integer> landings() {
            return Collections.unmodifiableMap(landings);
        }

        public int landings(String role) {
            return landings.getOrDefault(role, 0);
        }

        public Map<String, Integer> itemsUsed() {
            return Collections.unmodifiableMap(itemsUsed);
        }

        public int spacesWalked() {
            return spacesWalked;
        }

        public int diceRolls() {
            return diceRolls;
        }

        public int rollTotal() {
            return rollTotal;
        }

        public int bestRoll() {
            return bestRoll;
        }

        public int miniGamesPlayed() {
            return miniGamesPlayed;
        }

        public int miniGamesWon() {
            return miniGamesWon;
        }

        public int miniGamesLost() {
            return miniGamesLost;
        }

        /** The sum of its places in the mini-games (the average place: divided by {@link #miniGamesPlayed}). */
        public int placesTotal() {
            return placesTotal;
        }

        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            if (owner != null) nbt.putUuid("Owner", owner);
            nbt.putString("Name", name);
            nbt.put("Coins", new NbtIntArray(coins));
            nbt.put("Stars", new NbtIntArray(stars));
            nbt.put("Landings", counts(landings));
            nbt.put("Items", counts(itemsUsed));
            nbt.putIntArray("Counters", new int[]{spacesWalked, diceRolls, rollTotal, bestRoll,
                    miniGamesPlayed, miniGamesWon, miniGamesLost, placesTotal});
            return nbt;
        }

        static Player fromNbt(NbtCompound nbt) {
            Player player = new Player();
            player.owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
            player.name = nbt.getString("Name");
            for (int value : nbt.getIntArray("Coins")) player.coins.add(value);
            for (int value : nbt.getIntArray("Stars")) player.stars.add(value);
            readCounts(nbt.getCompound("Landings"), player.landings);
            readCounts(nbt.getCompound("Items"), player.itemsUsed);
            int[] counters = nbt.getIntArray("Counters");
            int i = 0;
            if (counters.length >= 8) {
                player.spacesWalked = counters[i++];
                player.diceRolls = counters[i++];
                player.rollTotal = counters[i++];
                player.bestRoll = counters[i++];
                player.miniGamesPlayed = counters[i++];
                player.miniGamesWon = counters[i++];
                player.miniGamesLost = counters[i++];
                player.placesTotal = counters[i];
            }
            return player;
        }
    }

    private final List<Point> points = new ArrayList<>();
    private final Map<UUID, Player> players = new LinkedHashMap<>();

    public List<Point> points() {
        return Collections.unmodifiableList(points);
    }

    /** The pawns, in the order they joined. */
    public Map<UUID, Player> players() {
        return Collections.unmodifiableMap(players);
    }

    public @Nullable Player of(UUID token) {
        return players.get(token);
    }

    public boolean isEmpty() {
        return points.isEmpty() && players.isEmpty();
    }

    public void clear() {
        points.clear();
        players.clear();
    }

    /** The pawn {@code token}'s line, made if new (its timeline padded with 0 up to now). */
    Player player(UUID token) {
        return players.computeIfAbsent(token, uuid -> {
            Player player = new Player();
            for (int i = 0; i < points.size(); i++) {
                player.coins.add(0);
                player.stars.add(0);
            }
            return player;
        });
    }

    /** Who plays {@code token} now (its owner may change during the party). */
    void name(UUID token, @Nullable UUID owner, String name) {
        Player player = player(token);
        if (owner != null) player.owner = owner;
        if (name != null && !name.isEmpty()) player.name = name;
    }

    /** A new point of the timeline: {@code values} gives each pawn's coins and stars then (missing: as before). */
    void point(Point point, Map<UUID, int[]> values) {
        points.add(point);
        for (Map.Entry<UUID, int[]> entry : values.entrySet()) player(entry.getKey());
        for (Map.Entry<UUID, Player> entry : players.entrySet()) {
            Player player = entry.getValue();
            int[] value = values.get(entry.getKey());
            int coins = value != null ? value[0] : player.coins.isEmpty() ? 0 : player.coins.getLast();
            int stars = value != null ? value[1] : player.stars.isEmpty() ? 0 : player.stars.getLast();
            player.coins.add(coins);
            player.stars.add(stars);
        }
    }

    void landed(UUID token, String role) {
        player(token).landings.merge(role, 1, Integer::sum);
    }

    void walked(UUID token) {
        player(token).spacesWalked++;
    }

    void rolled(UUID token, int value) {
        Player player = player(token);
        player.diceRolls++;
        player.rollTotal += value;
        player.bestRoll = Math.max(player.bestRoll, value);
    }

    void used(UUID token, String item) {
        player(token).itemsUsed.merge(item, 1, Integer::sum);
    }

    /** A mini-game ended: {@code place} 1 for a winner, {@code last} the place of who came last. */
    void miniGame(UUID token, int place, boolean won, boolean last) {
        Player player = player(token);
        player.miniGamesPlayed++;
        player.placesTotal += Math.max(1, place);
        if (won) player.miniGamesWon++;
        if (last) player.miniGamesLost++;
    }

    // ---------------------------------------------------------------- save

    public void writeNbt(NbtCompound nbt) {
        if (isEmpty()) return;
        NbtCompound stats = new NbtCompound();
        NbtList pointsNbt = new NbtList();
        for (Point point : points) pointsNbt.add(NbtString.of(point.kind().name() + ":" + point.round()));
        stats.put("Points", pointsNbt);
        NbtCompound playersNbt = new NbtCompound();
        players.forEach((token, player) -> playersNbt.put(token.toString(), player.toNbt()));
        stats.put("Players", playersNbt);
        nbt.put("Stats", stats);
    }

    public void readNbt(NbtCompound nbt) {
        clear();
        NbtCompound stats = nbt.getCompound("Stats");
        for (NbtElement element : stats.getList("Points", NbtElement.STRING_TYPE)) {
            String[] parts = element.asString().split(":");
            try {
                points.add(new Point(Kind.valueOf(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0));
            } catch (IllegalArgumentException ignored) {
            }
        }
        NbtCompound playersNbt = stats.getCompound("Players");
        for (String key : playersNbt.getKeys()) {
            try {
                players.put(UUID.fromString(key), Player.fromNbt(playersNbt.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static NbtCompound counts(Map<String, Integer> counts) {
        NbtCompound nbt = new NbtCompound();
        counts.forEach(nbt::putInt);
        return nbt;
    }

    private static void readCounts(NbtCompound nbt, Map<String, Integer> counts) {
        for (String key : nbt.getKeys()) counts.put(key, nbt.getInt(key));
    }
}
