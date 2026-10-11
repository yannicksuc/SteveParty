package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.entities.BoardActor;
import fr.lordfinn.steveparty.entities.HologramTargeting;
import fr.lordfinn.steveparty.payloads.custom.BoardActorStatePayload;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The mobs a board space summons for its show (the Glandouilles of a Glandouille space, the Frousseux of a Frousseux
 * space...): « board actors ». Every one of them:
 * <ul>
 *     <li>is invulnerable: nothing hurts it (creative players included) but what nothing withstands, commands
 *     ({@code /kill}) and the void. A space may still listen to blows (the Frousseux's coin defence) as long as they
 *     never hurt;</li>
 *     <li>is removed at the end of its space's sequence ({@link #end}), whatever ends it: normal end, interrupted
 *     sequence, party over, player gone, server stopping;</li>
 *     <li>is never kept: one that slipped into a save (marked by {@link #TAG}) is removed as soon as it loads;</li>
 *     <li>is never aimed at: the crosshair goes through it (no outline, no name, no click on it) to the block or the
 *     mob behind, and blocks are placed in its box as if it were not there, so it never stands in the way of the
 *     Spawn Marker under it or the tiles around. Only while its show wants blows or clicks on it
 *     ({@link #setTouchable}: the Frousseux's coin defence) can it be aimed at; the clients learn both
 *     ({@link BoardActorStatePayload}).</li>
 * </ul>
 * A space spawns its actors through {@link #join} (or marks one with {@link #mark}, its own entity class keeping it out
 * of saves), and calls {@link #end} with the same sequence id when its show is over. Server thread only.
 */
public final class BoardActors {
    /** The scoreboard tag of a board actor (saved with it: how a stray one is recognised on load). */
    public static final String TAG = "steveparty.board_actor";

    /** The board actors alive now (an actor loading without being here is a stray one). */
    private static final Set<UUID> LIVE = ServerMemory.forgetOnStop(new HashSet<>());
    /** The actors of each running sequence. */
    private static final Map<UUID, List<Entity>> SEQUENCES = ServerMemory.forgetOnStop(new HashMap<>());

    private BoardActors() {
    }

    public static void initialize() {
        // Nothing hurts it but commands and the void (not even a creative player)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !isBoardActor(entity) || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY));
        // A stray actor (saved by mistake, or left by a crash) is removed as soon as it loads
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (isBoardActor(entity) && !LIVE.contains(entity.getUuid())) entity.discard();
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (isBoardActor(entity)) LIVE.remove(entity.getUuid());
        });
        // The players who see it learn it is a hologram (their crosshair goes through it)
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (isBoardActor(entity)) ServerPlayNetworking.send(player, state(entity));
        });
        // Stopping: every show's actors go (they are never saved anyway)
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (UUID sequence : new ArrayList<>(SEQUENCES.keySet())) end(sequence);
        });
    }

    /**
     * Whether {@code entity} is to be treated as a hologram: a board actor server side (its tag); client side, where
     * the tag is not known, what the server said ({@link BoardActorStatePayload}), or one of our board mobs without AI
     * (the flag is synced), for what the client predicts (aiming, pushing, uses). A hologram: invulnerable, no loot nor
     * experience, no use of any kind on it, no AI of its own, neither pushing nor pushed, no knockback, never burning,
     * untouched by explosions, picking nothing up, never despawning by itself, never saved, and never aimed at
     * ({@link #isTargetable}): it only plays its animations and the moves its space scripts (mixins BoardActor*Mixin).
     */
    public static boolean isHologram(Entity entity) {
        if (entity == null) return false;
        if (!entity.getWorld().isClient) return isBoardActor(entity);
        if (((HologramTargeting) entity).steveparty$isSyncedHologram()) return true;
        return entity instanceof BoardActor && entity instanceof MobEntity mob && mob.isAiDisabled();
    }

    /**
     * Whether {@code entity} can be aimed at (crosshair, projectiles, a player's reach: {@code canHit}): anything but a
     * hologram, unless its show wants blows or clicks on it right now ({@link #setTouchable}).
     */
    public static boolean isTargetable(Entity entity) {
        return !isHologram(entity) || isTouchable(entity);
    }

    /** Whether the show of {@code entity} wants blows or clicks on it right now (see {@link #setTouchable}). */
    public static boolean isTouchable(Entity entity) {
        return ((HologramTargeting) entity).steveparty$isTouchable();
    }

    /**
     * Opens ({@code true}) or closes the moment where the show of {@code actor} wants blows or clicks on it: meanwhile
     * it can be aimed at (still invulnerable: its show gets the blows, see FrousseuxEntity#onBoardHit), and the players
     * who see it are told. Server side; nothing sent when nothing changes.
     */
    public static void setTouchable(Entity actor, boolean touchable) {
        HologramTargeting state = (HologramTargeting) actor;
        if (state.steveparty$isTouchable() == touchable) return;
        state.steveparty$setTouchable(touchable);
        if (actor.getWorld().isClient) return;
        BoardActorStatePayload payload = state(actor);
        for (ServerPlayerEntity player : PlayerLookup.tracking(actor)) ServerPlayNetworking.send(player, payload);
    }

    /** Client: what the server says of {@code entity} ({@link BoardActorStatePayload}). */
    public static void applySynced(Entity entity, boolean hologram, boolean touchable) {
        HologramTargeting state = (HologramTargeting) entity;
        state.steveparty$setSyncedHologram(hologram);
        state.steveparty$setTouchable(touchable);
        if (hologram) entity.intersectionChecked = false; // its box never stops a block (client prediction)
    }

    private static BoardActorStatePayload state(Entity actor) {
        return new BoardActorStatePayload(actor.getId(), isBoardActor(actor), isTouchable(actor));
    }

    /** Whether {@code entity} is a board actor (both sides for the tag; server side it is what counts). */
    public static boolean isBoardActor(Entity entity) {
        return entity.getCommandTags().contains(TAG);
    }

    /**
     * Makes {@code entity} a board actor, before it is spawned: invulnerable, tagged, known alive. Its removal is up to
     * whoever spawned it ({@link #join} does it with its sequence).
     */
    public static <T extends Entity> T mark(T entity) {
        entity.addCommandTag(TAG);
        entity.setInvulnerable(true);
        entity.intersectionChecked = false; // a block may be placed in its box (it is not there)
        if (entity instanceof MobEntity mob) mob.setPersistent(); // never despawns in the middle of its show
        LIVE.add(entity.getUuid());
        return entity;
    }

    /** {@link #mark}s {@code entity} (before it is spawned) and makes it one of {@code sequence}'s actors. */
    public static <T extends Entity> T join(UUID sequence, T entity) {
        mark(entity);
        SEQUENCES.computeIfAbsent(sequence, id -> new ArrayList<>()).add(entity);
        return entity;
    }

    /** The show {@code sequence} is over (or stopped): its actors are removed. */
    public static void end(UUID sequence) {
        List<Entity> actors = SEQUENCES.remove(sequence);
        if (actors == null) return;
        for (Entity actor : actors) {
            LIVE.remove(actor.getUuid());
            if (!actor.isRemoved()) actor.discard();
        }
    }

    /** Whether {@code sequence} has actors and is not over yet. */
    public static boolean isRunning(UUID sequence) {
        return SEQUENCES.containsKey(sequence);
    }

    /** The actors of {@code sequence} still in the world (for the GameTests). */
    public static int alive(UUID sequence) {
        List<Entity> actors = SEQUENCES.get(sequence);
        return actors == null ? 0 : (int) actors.stream().filter(actor -> !actor.isRemoved()).count();
    }
}
