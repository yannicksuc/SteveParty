package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Event-driven glue of the goal poles (server side): nothing here runs while nothing happens.
 * <ul>
 * <li>The loaded bases, and which base owns which scoreboard objective: a score update (a mixin on the server
 * scoreboard) wakes only the base of that objective.</li>
 * <li>A landing on a pole is handed to the pole's own base.</li>
 * <li>Bases and poles that were just loaded or placed are set up at the end of the tick (never while their chunk is
 * still being loaded).</li>
 * </ul>
 */
public final class GoalPoleNetwork {
    private static final Set<GoalPoleBaseBlockEntity> BASES = new LinkedHashSet<>();
    /** The bases each objective wakes: its own two, or several following the same objective of the server's. */
    private static final Map<String, Set<GoalPoleBaseBlockEntity>> BY_OBJECTIVE = new HashMap<>();
    private static final ArrayDeque<BlockEntity> PENDING = new ArrayDeque<>();
    /** Bases and poles whose data changed this tick: sent to the players watching them once, at the end of the tick. */
    private static final Set<BlockEntity> TO_SYNC = new LinkedHashSet<>();

    private GoalPoleNetwork() {}

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            processPending();
            flushSyncs();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
    }

    // ------------------------------------------------------------------ registration

    static void register(GoalPoleBaseBlockEntity base) {
        BASES.add(base);
        PENDING.add(base);
    }

    static void unregister(GoalPoleBaseBlockEntity base) {
        BASES.remove(base);
        BY_OBJECTIVE.values().removeIf(owners -> owners.remove(base) && owners.isEmpty());
    }

    /** A pole was loaded or placed: it looks for its base (and copies its column's settings) at the end of the tick. */
    static void schedule(BlockEntity entity) {
        PENDING.add(entity);
    }

    static void index(String objectiveName, GoalPoleBaseBlockEntity base) {
        BY_OBJECTIVE.computeIfAbsent(objectiveName, name -> new LinkedHashSet<>()).add(base);
    }

    static void unindex(String objectiveName, GoalPoleBaseBlockEntity base) {
        Set<GoalPoleBaseBlockEntity> owners = BY_OBJECTIVE.get(objectiveName);
        if (owners != null && owners.remove(base) && owners.isEmpty()) BY_OBJECTIVE.remove(objectiveName);
    }

    /** Loaded bases (a copy: safe to use while bases are added or removed). */
    public static List<GoalPoleBaseBlockEntity> bases() {
        return new ArrayList<>(BASES);
    }

    public static void processPending() {
        int count = PENDING.size();
        for (int i = 0; i < count && !PENDING.isEmpty(); i++) {
            BlockEntity entity = PENDING.poll();
            if (entity.isRemoved() || entity.getWorld() == null) continue;
            if (entity instanceof GoalPoleBaseBlockEntity base) base.onLoaded();
            else if (entity instanceof GoalPoleBlockEntity pole) pole.onLoaded();
        }
    }

    static void requestSync(BlockEntity entity) {
        TO_SYNC.add(entity);
    }

    /** Sends the block entities that changed this tick to the players watching them (one update each). */
    public static void flushSyncs() {
        if (TO_SYNC.isEmpty()) return;
        for (BlockEntity entity : TO_SYNC) {
            if (entity.isRemoved() || entity.getWorld() == null) continue;
            var state = entity.getCachedState();
            entity.getWorld().updateListeners(entity.getPos(), state, state, net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
        TO_SYNC.clear();
    }

    private static void clear() {
        BASES.clear();
        BY_OBJECTIVE.clear();
        PENDING.clear();
        TO_SYNC.clear();
    }

    // ------------------------------------------------------------------ events

    /** A score changed on the server scoreboard (set, added, or removed: 0). */
    public static void onScoreUpdated(ScoreboardObjective objective, String holder, int value) {
        if (BY_OBJECTIVE.isEmpty()) return;
        Set<GoalPoleBaseBlockEntity> owners = BY_OBJECTIVE.get(objective.getName());
        if (owners == null) return;
        for (GoalPoleBaseBlockEntity base : new ArrayList<>(owners)) {
            if (!base.isRemoved()) base.onScoreUpdated(objective, holder, value);
        }
    }

    /** All the scores of a holder were removed ({@code /scoreboard players reset <name>}). */
    public static void onHolderRemoved(String holder) {
        if (BY_OBJECTIVE.isEmpty()) return;
        for (GoalPoleBaseBlockEntity base : bases()) {
            if (!base.isRemoved()) base.onHolderRemoved(holder);
        }
    }

    public static void onObjectiveRemoved(ScoreboardObjective objective) {
        if (BY_OBJECTIVE.isEmpty()) return;
        Set<GoalPoleBaseBlockEntity> owners = BY_OBJECTIVE.get(objective.getName());
        if (owners == null) return;
        for (GoalPoleBaseBlockEntity base : new ArrayList<>(owners)) {
            if (!base.isRemoved()) base.onObjectiveRemoved(objective);
        }
    }

    /** A party started: the bases linked to it (following its players) go back to 0. */
    public static void onPartyStarted(PartyControllerEntity controller) {
        for (GoalPoleBaseBlockEntity base : bases()) {
            if (!base.isRemoved() && base.getWorld() == controller.getWorld()) base.onPartyStarted(controller);
        }
    }

    /** A player landed on a pole (once per landing): its own base counts it if it counts landings on its poles. */
    public static void onLanding(GoalPoleBlockEntity pole, ServerPlayerEntity player) {
        GoalPoleBaseBlockEntity base = pole.getCachedBase();
        if (base != null && !base.isRemoved()) base.onLanding(player);
    }
}
