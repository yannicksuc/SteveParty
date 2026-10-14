package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import java.util.Locale;

/**
 * How the teams of a mini-game are made. A mini-game card of the program: {@link #ANY}, the board decides (see
 * {@link TeamDispositionGenerator}). A Mini-game space (see fr.lordfinn.steveparty.items.custom.cartridges.MiniGameCartridgeItem)
 * plays one of the others, its formats.
 */
public enum MiniGameMode {
    /** The board's teams, as a mini-game card. */
    ANY,
    /** 1 v 1: the player who landed against the opponent he picks in the chat (drawn without an answer). */
    DUEL,
    /** 1 v 1: the player who landed against another player drawn at random. */
    DUEL_RANDOM,
    /** 1 against all: the player who landed against all the others. */
    VS,
    /** Free for all: everyone for themselves. */
    FFA,
    /** The board's teams (2 v 2, 1 v 3...), never everyone for themselves. */
    TEAMS,
    /** The player who landed picks one of the space's formats (in the chat). */
    CHOICE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether it pits the player who landed against one other player. */
    public boolean isDuel() {
        return this == DUEL || this == DUEL_RANDOM;
    }

    public static MiniGameMode byName(String name) {
        for (MiniGameMode mode : values()) if (mode.name().equalsIgnoreCase(name)) return mode;
        return ANY;
    }
}
