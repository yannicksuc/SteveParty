package fr.lordfinn.steveparty.powerups.effects;

import java.util.Objects;

/**
 * The Star of the board, as the power-ups aiming at it see it (the Golden Pipe reads it, the Star Whistle moves it):
 * one {@link StarRelocator} for the whole mod, asked at each use.
 * <p>
 * By default the party's star of the Star Cartridge ({@link PartyStarRelocator}). A board with no star (no active star
 * space) answers "no Star": the Golden Pipe and the Star Whistle are then refused (not used up) with a message.
 */
public final class PowerUpStar {
    private static StarRelocator relocator = PartyStarRelocator.INSTANCE;

    private PowerUpStar() {
    }

    /** The Star's reader and mover used by the power-ups now. */
    public static StarRelocator relocator() {
        return relocator;
    }

    /**
     * Plugs in another Star reader and mover (a test one in the gametests).
     *
     * @return the one it replaces (to put it back)
     */
    public static StarRelocator install(StarRelocator starRelocator) {
        StarRelocator previous = relocator;
        relocator = Objects.requireNonNull(starRelocator);
        return previous;
    }
}
