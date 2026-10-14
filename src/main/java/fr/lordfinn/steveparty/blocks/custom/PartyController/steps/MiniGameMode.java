package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import java.util.Locale;

/**
 * How the teams of a mini-game are made. A mini-game card of the program: {@link #ANY}, the board decides (see
 * {@link TeamDispositionGenerator}). A Mini-game space (see fr.lordfinn.steveparty.items.custom.cartridges.MiniGameCartridgeItem)
 * picks one of the others.
 */
public enum MiniGameMode {
    /** The board's teams, as a mini-game card. */
    ANY,
    /** VS: the player who landed against all the others; the board's teams when no mini-game fits that. */
    VS,
    /** Free for all: everyone for themselves. */
    FFA,
    /** The player who landed chooses VS or free for all (in the chat). */
    CHOICE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MiniGameMode byName(String name) {
        for (MiniGameMode mode : values()) if (mode.name().equalsIgnoreCase(name)) return mode;
        return ANY;
    }
}
