package fr.lordfinn.steveparty.utils;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.util.Collection;
import java.util.Map;

/**
 * Static server-side memory (maps and sets keyed by players, tokens, worlds...) that must not outlive its server.
 * <p>
 * In singleplayer the same JVM opens world after world: a static collection that is only emptied by a scheduled task
 * or by the end of an animation keeps its entries when the world closes in the middle (the scheduler drops its tasks
 * without running them), and the next world inherits them. Token UUIDs and dimension keys are saved with the worlds,
 * so a stale entry then blocks a token, skips an event... Wrapping the collection where it is declared clears it when
 * the server stops, whatever was going on.
 */
public final class ServerMemory {
    private ServerMemory() {
    }

    /** @return {@code collection}, emptied every time a server stops */
    public static <C extends Collection<?>> C forgetOnStop(C collection) {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> collection.clear());
        return collection;
    }

    /** @return {@code map}, emptied every time a server stops */
    public static <M extends Map<?, ?>> M forgetOnStop(M map) {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> map.clear());
        return map;
    }
}
