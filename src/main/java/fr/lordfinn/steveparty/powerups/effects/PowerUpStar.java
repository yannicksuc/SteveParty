package fr.lordfinn.steveparty.powerups.effects;

import java.util.Objects;

/**
 * The Star of the board, as the power-ups aiming at it see it (the Golden Pipe reads it, the Star Whistle moves it):
 * one {@link StarRelocator} for the whole mod, asked at each use.
 * <p>
 * Until the Star cartridge plugs its own in ({@link #install}, at start-up), the default one answers "no Star": the
 * Golden Pipe and the Star Whistle are refused (not used up) with a message saying so.
 */
public final class PowerUpStar {
    private static StarRelocator relocator = StarRelocator.NONE;

    private PowerUpStar() {
    }

    /** The Star's reader and mover used by the power-ups now. */
    public static StarRelocator relocator() {
        return relocator;
    }

    /**
     * Plugs in the Star's reader and mover (the Star cartridge's, at start-up; a test one in the gametests).
     *
     * @return the one it replaces (to put it back)
     */
    public static StarRelocator install(StarRelocator starRelocator) {
        StarRelocator previous = relocator;
        relocator = Objects.requireNonNull(starRelocator);
        return previous;
    }
}
