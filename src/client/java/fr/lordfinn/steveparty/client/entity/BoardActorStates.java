package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.payloads.custom.BoardActorStatePayload;
import fr.lordfinn.steveparty.service.BoardActors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * The board actors' states the server sends ({@link BoardActorStatePayload}), put on the entities themselves
 * ({@link BoardActors#applySynced}): a hologram is never aimed at, unless its show wants blows or clicks right now. A
 * state arriving before its entity waits for it (the order of a newly tracked entity's packets is no promise). Client
 * thread only.
 */
public final class BoardActorStates {
    /** States whose entity is not in the world yet, by network id. */
    private static final Map<Integer, BoardActorStatePayload> PENDING = new HashMap<>();
    /** Never kept for long: past this many, the oldest ones were for entities seen and gone at once. */
    private static final int MAX_PENDING = 1024;

    private BoardActorStates() {
    }

    public static void initialize() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            BoardActorStatePayload state = PENDING.remove(entity.getId());
            if (state != null) apply(entity, state);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(PENDING::clear));
    }

    public static void receive(ClientWorld world, BoardActorStatePayload payload) {
        if (world == null) return;
        Entity entity = world.getEntityById(payload.entityId());
        if (entity == null) {
            if (PENDING.size() >= MAX_PENDING) PENDING.clear();
            PENDING.put(payload.entityId(), payload);
            return;
        }
        apply(entity, payload);
    }

    private static void apply(Entity entity, BoardActorStatePayload state) {
        BoardActors.applySynced(entity, state.hologram(), state.touchable());
    }
}
