package fr.lordfinn.steveparty.board;

import net.minecraft.world.World;

/**
 * A counter bumped on the client whenever something the board view is drawn from may have changed: the data or the
 * block state of a board space or a router (cartridges, links, role, support, size). The board view rebuilds its graph
 * when it changed instead of on a timer (the client also bumps it when such a block entity, or a chest, is loaded or
 * removed). Client thread only.
 */
public final class BoardRevision {
    private static long client;

    private BoardRevision() {
    }

    /** Something of the board changed in {@code world} (ignored on the server). */
    public static void changed(World world) {
        if (world != null && world.isClient) client++;
    }

    /** Something of the board changed on the client. */
    public static void changedOnClient() {
        client++;
    }

    public static long client() {
        return client;
    }
}
