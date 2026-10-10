package fr.lordfinn.steveparty.api.event;

import fr.lordfinn.steveparty.events.DiceRollEvent;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.events.TileUpdatedEvent;
import net.fabricmc.fabric.api.event.Event;

/**
 * The board and its dice (server side), gathered here for the addons. A listener returning anything but
 * {@code ActionResult.PASS} stops the chain: the listeners after it, Steve Party's own included, are not called.
 * Steve Party's own listeners are registered during its initialization, before the addons'.
 */
public final class BoardEvents {
    private BoardEvents() {
    }

    /** A thrown die stopped on a value, before its token moves (Steve Party's party controllers listen to it). */
    public static final Event<DiceRollEvent> DICE_ROLLED = DiceRollEvent.EVENT;

    /** A token reached a board space during its move (passing or stopping there). */
    public static final Event<TileReachedEvent> TILE_REACHED = TileReachedEvent.EVENT;

    /** The role of a board space changed (another cartridge) while tokens stand on it. */
    public static final Event<TileUpdatedEvent> TILE_UPDATED = TileUpdatedEvent.EVENT;
}
