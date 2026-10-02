package fr.lordfinn.steveparty.podium;

import net.minecraft.text.Text;

import java.util.Locale;

/**
 * What a redstone pulse into a podium column does: a setting of the column, changed with the Wrench (right click).
 * Whatever the setting, the column stays a place of its group.
 */
public enum PodiumSignal {
    /** The player the nearest to the column is registered on it (the default). */
    REGISTER,
    /** The player the nearest to the column who has no place yet takes the highest free place of the group. */
    FILL,
    /** The column is emptied. */
    CLEAR,
    /** The whole group is reset: its columns emptied, the points of its goal pole bases back to 0. */
    RESET;

    private static final PodiumSignal[] VALUES = values();

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Text text() {
        return Text.translatable("podium_signal.steveparty." + key());
    }

    public PodiumSignal next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static PodiumSignal byName(String name) {
        for (PodiumSignal value : VALUES) if (value.name().equals(name)) return value;
        return REGISTER;
    }
}
