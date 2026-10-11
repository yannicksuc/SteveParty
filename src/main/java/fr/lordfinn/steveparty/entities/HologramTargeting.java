package fr.lordfinn.steveparty.entities;

import fr.lordfinn.steveparty.service.BoardActors;

/**
 * What every entity keeps of its board actor state for the crosshair (implemented on {@code Entity} by
 * BoardActorEntityMixin; read and written through {@link BoardActors}, never directly). Server side, being a hologram
 * is its tag; client side, both values come from the server ({@code BoardActorStatePayload}).
 */
public interface HologramTargeting {
    /** Client: whether the server said it is a hologram. */
    boolean steveparty$isSyncedHologram();

    void steveparty$setSyncedHologram(boolean hologram);

    /** Whether its show wants blows or clicks on it right now (it can then be aimed at, though still a hologram). */
    boolean steveparty$isTouchable();

    void steveparty$setTouchable(boolean touchable);
}
