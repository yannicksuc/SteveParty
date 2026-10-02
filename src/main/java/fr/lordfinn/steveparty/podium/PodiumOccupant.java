package fr.lordfinn.steveparty.podium;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who is registered on a podium column (kept by its bottom block, shown to everyone on its top).
 * <p>
 * A column on its own shows its player as a small figure. While a mini-game is played on the page of its group, the
 * columns of the same height are one place ({@code Podiums}): each of them is held by the same player (or team), and
 * what it shows is its own: the figure of the player on the column he took, of a member of the team on each column of
 * the team's place, or no figure at all (only the label) on the columns left over.
 *
 * @param player the registered player: the one who took the place
 * @param name   its name
 * @param team   the team it stands for in a team mini-game (0: A ... 3: D), -1 without teams
 * @param place  the place of the column in its group when it registered (1: the tallest)
 * @param since  world time of the registration (the figure pops in)
 * @param figure who stands on this column as a figure (its skin is found again from it when the player is away); null:
 *               nobody, the column only says who holds it
 * @param more   the other members of the team, named on the label of this column (those the place has no column for)
 */
public record PodiumOccupant(UUID player, String name, int team, int place, long since, @Nullable Figure figure, List<String> more) {
    /** A player shown as a figure. */
    public record Figure(UUID player, String name) {
    }

    public PodiumOccupant {
        more = List.copyOf(more);
    }

    /** A player on a column of its own: he is the figure. */
    public PodiumOccupant(UUID player, String name, int team, int place, long since) {
        this(player, name, team, place, since, new Figure(player, name), List.of());
    }

    public PodiumOccupant withPlace(int newPlace) {
        return newPlace == place ? this : new PodiumOccupant(player, name, team, newPlace, since, figure, more);
    }

    /** @return true if {@code other} is the same player or, in a team mini-game, stands for the same team. */
    public boolean sameSide(UUID otherPlayer, int otherTeam) {
        return player.equals(otherPlayer) || (team >= 0 && team == otherTeam);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Player", player);
        nbt.putString("Name", name);
        nbt.putInt("Team", team);
        nbt.putInt("Place", place);
        nbt.putLong("Since", since);
        if (figure == null) {
            nbt.putBoolean("NoFigure", true);
        } else if (!figure.player().equals(player)) {
            nbt.putUuid("Figure", figure.player());
            nbt.putString("FigureName", figure.name());
        }
        if (!more.isEmpty()) {
            NbtList list = new NbtList();
            more.forEach(other -> list.add(NbtString.of(other)));
            nbt.put("More", list);
        }
        return nbt;
    }

    public static @Nullable PodiumOccupant fromNbt(NbtCompound nbt) {
        if (!nbt.containsUuid("Player")) return null;
        UUID player = nbt.getUuid("Player");
        String name = nbt.getString("Name");
        Figure figure = nbt.getBoolean("NoFigure") ? null
                : nbt.containsUuid("Figure") ? new Figure(nbt.getUuid("Figure"), nbt.getString("FigureName")) : new Figure(player, name);
        List<String> more = new ArrayList<>();
        for (NbtElement element : nbt.getList("More", NbtElement.STRING_TYPE)) more.add(element.asString());
        return new PodiumOccupant(player, name, nbt.contains("Team") ? nbt.getInt("Team") : -1,
                Math.max(1, nbt.getInt("Place")), nbt.getLong("Since"), figure, more);
    }
}
