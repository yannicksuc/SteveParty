package fr.lordfinn.steveparty.podium;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who is registered on a podium column (kept by its bottom block, shown to everyone as a small figure on its top).
 *
 * @param player the registered player
 * @param name   its name (its skin is found again from it when the player is away)
 * @param team   the team it stands for in a team mini-game (0: A ... 3: D), -1 without teams
 * @param place  the place of the column in its group when it registered (1: the tallest)
 * @param since  world time of the registration (the figure pops in)
 */
public record PodiumOccupant(UUID player, String name, int team, int place, long since) {
    public PodiumOccupant withPlace(int newPlace) {
        return newPlace == place ? this : new PodiumOccupant(player, name, team, newPlace, since);
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
        return nbt;
    }

    public static @Nullable PodiumOccupant fromNbt(NbtCompound nbt) {
        if (!nbt.containsUuid("Player")) return null;
        return new PodiumOccupant(nbt.getUuid("Player"), nbt.getString("Name"), nbt.contains("Team") ? nbt.getInt("Team") : -1,
                Math.max(1, nbt.getInt("Place")), nbt.getLong("Since"));
    }
}
