package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.podium.PodiumGroup;
import fr.lordfinn.steveparty.podium.PodiumOccupant;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The results of a mini-game of a party, as everyone sees them on the results card: who took which place, and what
 * the party paid for it.
 *
 * @param title    the mini-game's title, empty if it has none
 * @param coinItem the item the party counts as coins (its icon next to the amounts)
 * @param starItem the item the party counts as stars
 * @param rows     the best place first, the participants (no place) last
 * @param test     the results of a test ({@link MiniGameTest}): what each place would be paid, nothing was
 */
public record MiniGameResults(String title, ItemStack coinItem, ItemStack starItem, List<Row> rows, boolean test) {
    /** The same results, as those of a test: nothing was paid. */
    public MiniGameResults asTest() {
        return new MiniGameResults(title, coinItem, starItem, rows, true);
    }

    public static final int MAX_ROWS = 16;

    /**
     * A line of the results: a player, or a team in a team mini-game.
     *
     * @param place 1 for the winners, 2, 3... ; 0 for the participants (no place)
     * @param team  the team (0: A ... 3: D), -1 without teams
     * @param names the players of the line
     * @param coins what each of them received
     */
    public record Row(int place, int team, List<String> names, int coins, int stars) {
    }

    /**
     * The place of each player of a mini-game, read on its podiums: the place of the column a player is registered on
     * or, in a team mini-game, of the column his team is registered on; 0 (« participant ») for those without one.
     *
     * @param participants the players of the mini-game, in turn order
     * @param teams        its teams, null (or free for all) without teams
     * @param group        its podiums, null for none
     * @return the place of every participant, in the same order
     */
    public static Map<UUID, Integer> places(List<UUID> participants, @Nullable TeamDisposition teams, @Nullable PodiumGroup group) {
        Map<UUID, Integer> places = new LinkedHashMap<>();
        for (UUID player : participants) places.put(player, 0);
        if (group == null) return places;
        boolean teamGame = teams != null && !teams.isFreeForAll();
        for (Map.Entry<PodiumOccupant, Integer> placement : group.placements().entrySet()) {
            UUID registered = placement.getKey().player();
            int team = teamGame ? teams.teamOf(registered) : -1;
            for (UUID player : participants) {
                if (team >= 0 ? teams.teamOf(player) != team : !player.equals(registered)) continue;
                int known = places.get(player);
                if (known == 0 || placement.getValue() < known) places.put(player, placement.getValue());
            }
        }
        return places;
    }

    /**
     * The lines of the results: one per player, one per team in a team mini-game; the best place first, those without
     * a place last (the turn order between equals).
     *
     * @param places the place of each player ({@link #places})
     * @param name   the name of a player
     */
    public static MiniGameResults of(String title, ItemStack coinItem, ItemStack starItem, MiniGameGains gains,
                                     Map<UUID, Integer> places, @Nullable TeamDisposition teams, Function<UUID, String> name) {
        boolean teamGame = teams != null && !teams.isFreeForAll();
        List<Row> rows = new ArrayList<>();
        Map<Integer, Integer> teamRows = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : places.entrySet()) {
            int place = entry.getValue();
            int team = teamGame ? teams.teamOf(entry.getKey()) : -1;
            Integer index = team >= 0 ? teamRows.get(team) : null;
            if (index != null) {
                rows.get(index).names().add(name.apply(entry.getKey()));
                continue;
            }
            List<String> names = new ArrayList<>();
            names.add(name.apply(entry.getKey()));
            if (team >= 0) teamRows.put(team, rows.size());
            rows.add(new Row(place, team, names, gains.forPlace(PartyCurrency.COIN, place), gains.forPlace(PartyCurrency.STAR, place)));
        }
        rows.sort(Comparator.comparingInt(row -> row.place() == 0 ? Integer.MAX_VALUE : row.place()));
        return new MiniGameResults(title, coinItem, starItem, rows, false);
    }

    public static final PacketCodec<RegistryByteBuf, MiniGameResults> PACKET_CODEC = PacketCodec.of((results, buf) -> {
        buf.writeString(results.title, MiniGamePageData.MAX_TITLE_LENGTH);
        ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, results.coinItem);
        ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, results.starItem);
        List<Row> rows = results.rows.size() > MAX_ROWS ? results.rows.subList(0, MAX_ROWS) : results.rows;
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeVarInt(row.place);
            buf.writeVarInt(row.team + 1);
            buf.writeVarInt(row.names.size());
            row.names.forEach(buf::writeString);
            buf.writeVarInt(row.coins);
            buf.writeVarInt(row.stars);
        }
        buf.writeBoolean(results.test);
    }, buf -> {
        String title = buf.readString(MiniGamePageData.MAX_TITLE_LENGTH);
        ItemStack coinItem = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
        ItemStack starItem = ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
        int count = Math.min(buf.readVarInt(), MAX_ROWS);
        List<Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int place = buf.readVarInt(), team = buf.readVarInt() - 1, nameCount = buf.readVarInt();
            List<String> names = new ArrayList<>();
            for (int j = 0; j < nameCount; j++) names.add(buf.readString());
            rows.add(new Row(place, team, names, buf.readVarInt(), buf.readVarInt()));
        }
        return new MiniGameResults(title, coinItem, starItem, rows, buf.readBoolean());
    });
}
