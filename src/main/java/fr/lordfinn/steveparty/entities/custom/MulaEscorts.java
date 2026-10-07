package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.config.ServerConfig;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The tamed Mulas following each player: at most {@link #max()} (the config's {@code mulaMaxFollowers}, 16 by
 * default). A crowd of followers each looking for a path and teleporting after the same player cost the server a lot
 * during the playtest; the Mulas past the limit stay where they are, and take a place as soon as one frees up.
 * <p>
 * A Mula takes its place the first time it may follow its owner and keeps it. A place is given back only when needed,
 * when the escort is full and another Mula asks: the members that stopped following (sitting, gone, given away, in
 * another dimension, made a board token) are dropped then. Server thread only.
 */
public final class MulaEscorts {
    private static final Map<UUID, Set<UUID>> ESCORTS = new HashMap<>();

    private MulaEscorts() {
    }

    public static int max() {
        return Math.max(1, ServerConfig.get().mulaMaxFollowers);
    }

    /** May this Mula follow its owner: it already has its place in the escort, or one is free (it takes it). */
    public static boolean join(ServerWorld world, UUID owner, MulaEntity mula) {
        Set<UUID> escort = ESCORTS.computeIfAbsent(owner, id -> new LinkedHashSet<>());
        if (escort.contains(mula.getUuid())) return true;
        if (escort.size() >= max()) escort.removeIf(id -> !follows(world, id, owner));
        if (escort.size() >= max()) return false;
        escort.add(mula.getUuid());
        return true;
    }

    /** Whether one more Mula could follow this player now (asked when one is tamed). */
    public static boolean isFull(ServerWorld world, UUID owner) {
        Set<UUID> escort = ESCORTS.get(owner);
        if (escort == null) return false;
        if (escort.size() >= max()) escort.removeIf(id -> !follows(world, id, owner));
        return escort.size() >= max();
    }

    /** Whether this Mula has its place among its owner's followers. */
    public static boolean isFollower(UUID owner, MulaEntity mula) {
        Set<UUID> escort = ESCORTS.get(owner);
        return escort != null && escort.contains(mula.getUuid());
    }

    /** Followers of this player (for the tests). */
    public static int size(UUID owner) {
        Set<UUID> escort = ESCORTS.get(owner);
        return escort == null ? 0 : escort.size();
    }

    /** Still one of its owner's followers: loaded here, alive, still theirs, standing, not a board token. */
    private static boolean follows(ServerWorld world, UUID id, UUID owner) {
        Entity entity = world.getEntity(id);
        return entity instanceof MulaEntity mula && mula.isAlive() && mula.isTamed() && owner.equals(mula.getOwnerUuid())
                && !mula.isSitting() && !mula.isToken();
    }
}
