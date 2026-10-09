package fr.lordfinn.steveparty.client.render.geo;

/** A creature's blink: shut a few ticks once in a while, each one at its own moment (its id, salted, shifts it). */
public final class Blink {
    private Blink() {
    }

    /** Whether its eyes are shut at {@code ticks} (its age): {@code length} ticks once in {@code every}. */
    public static boolean closed(int ticks, int id, int salt, int every, int length) {
        return Math.floorMod(ticks + id * salt, every) < length;
    }
}
