package fr.lordfinn.steveparty.podium;

import net.minecraft.text.Text;

import java.util.Locale;

/**
 * The mode of a podium column, changed with the Wrench (right click): what taking it does. Whatever the mode, the
 * column stays a place of its group.
 * <ul>
 *     <li>A classic podium (gold, iron, copper) is taken by a click or by sneaking on it: {@link #REGISTER},
 *     {@link #FILL} or {@link #FILL_BOTTOM} decide which place the player gets.</li>
 *     <li>A Redstone Podium is taken by a redstone pulse, for the nearest player: every mode.</li>
 * </ul>
 * A column made of both kinds answers both.
 */
public enum PodiumSignal {
    /** The player (a pulse: the nearest one) is registered on this column (the default). */
    REGISTER(true),
    /** The player (a pulse: the nearest one without a place) takes the best free place of the group. */
    FILL(true),
    /** The player (a pulse: the nearest one without a place) takes the last free place of the group: filled from the bottom. */
    FILL_BOTTOM(true),
    /** A pulse empties the column. */
    CLEAR(false),
    /** A pulse resets the whole group: its columns emptied, the points of its goal pole bases back to 0. */
    RESET(false);

    private static final PodiumSignal[] VALUES = values();
    /** A classic podium has this mode (a click can do it). */
    private final boolean byClick;

    PodiumSignal(boolean byClick) {
        this.byClick = byClick;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** What a pulse does in this mode. */
    public Text text() {
        return text(true);
    }

    /** What this mode does: for a pulse ({@code redstone}), or for a click on a classic podium. */
    public Text text(boolean redstone) {
        return Text.translatable((redstone ? "podium_signal.steveparty." : "podium_mode.steveparty.") + key());
    }

    /** Whether a classic podium (taken by a click) has this mode. */
    public boolean byClick() {
        return byClick;
    }

    /** The mode after this one: every mode for a column answering redstone, the click modes for a classic one. */
    public PodiumSignal next(boolean redstone) {
        PodiumSignal next = this;
        do {
            next = VALUES[(next.ordinal() + 1) % VALUES.length];
        } while (!redstone && !next.byClick);
        return next;
    }

    /** The modes a column answering redstone ({@code redstone}) or only clicks has. */
    public boolean allowed(boolean redstone) {
        return redstone || byClick;
    }

    public static PodiumSignal byName(String name) {
        for (PodiumSignal value : VALUES) if (value.name().equals(name)) return value;
        return REGISTER;
    }
}
