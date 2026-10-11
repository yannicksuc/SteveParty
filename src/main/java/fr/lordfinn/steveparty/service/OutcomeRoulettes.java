package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.hud.OutcomeRoulette;
import fr.lordfinn.steveparty.payloads.custom.OutcomeRoulettePayload;
import fr.lordfinn.steveparty.payloads.custom.OutcomeRoulettePayload.Line;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Outcome roulettes: a random board space shows everything that may happen before it happens. The server draws the
 * result first (with the space's own odds), then every player of the party sees the list on a panel floating over the
 * space (a strip on the HUD when it is out of sight), the light running down it and stopping on that result ({@link OutcomeRoulette} for the timing, {@link OutcomeRoulettePayload} for what is
 * sent); the space applies the result once {@link Roulette#isOver} ({@link OutcomeRoulette#TOTAL_TICKS} later). A
 * player joining in the middle gets it where it is. Nothing to tick: a roulette only remembers when it started.
 * Server thread only.
 */
public final class OutcomeRoulettes {
    private static final List<Roulette> RUNNING = ServerMemory.forgetOnStop(new ArrayList<>());
    private static int nextId = 1;

    private OutcomeRoulettes() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(handler.getPlayer()));
    }

    /**
     * One roulette: its party, its lines and result, when it started (server ticks), and who was sent it.
     */
    public static final class Roulette {
        private final int id;
        private final ServerWorld world;
        private final PartyControllerEntity party;
        private final @Nullable UUID trigger;
        private final @Nullable Vec3d anchor;
        private final Text title, caption;
        private final List<Line> lines;
        private final int result, steps, startedAt;
        private final Set<UUID> sentTo = new LinkedHashSet<>();

        private Roulette(int id, ServerWorld world, PartyControllerEntity party, @Nullable UUID trigger, @Nullable Vec3d anchor,
                         Text title, Text caption, List<Line> lines, int result) {
            this.id = id;
            this.world = world;
            this.party = party;
            this.trigger = trigger;
            this.anchor = anchor;
            this.title = title;
            this.caption = caption;
            this.lines = List.copyOf(lines);
            this.result = result;
            this.steps = OutcomeRoulette.steps(lines.size(), result);
            this.startedAt = world.getServer().getTicks();
        }

        public int id() {
            return id;
        }

        public List<Line> lines() {
            return lines;
        }

        /** The line it stops on. */
        public int result() {
            return result;
        }

        /** Ticks since it started. */
        public int elapsed() {
            return world.getServer().getTicks() - startedAt;
        }

        /** The light stopped and the result was held: what it says may happen. */
        public boolean isOver() {
            return elapsed() >= OutcomeRoulette.TOTAL_TICKS;
        }

        /** Where its panel floats (the middle of its bottom edge), null for the HUD only. */
        public @Nullable Vec3d anchor() {
            return anchor;
        }

        /** The players it was sent to. */
        public Set<UUID> sentTo() {
            return Set.copyOf(sentTo);
        }

        OutcomeRoulettePayload payload() {
            return new OutcomeRoulettePayload(id, Optional.ofNullable(trigger), Optional.ofNullable(anchor), title, caption, lines, result, steps,
                    Math.max(0, elapsed()));
        }

        void send(ServerPlayerEntity player) {
            ServerPlayNetworking.send(player, payload());
            sentTo.add(player.getUuid());
        }
    }

    /**
     * Starts a roulette for {@code party} over {@code lines}, stopping on line {@code result} (drawn by the caller),
     * shown to every player of the party ({@link #recipients}). {@code trigger}: the player it is about;
     * {@code anchor}: where its panel floats in the world (the middle of its bottom edge), null for the HUD only.
     */
    public static Roulette start(ServerWorld world, PartyControllerEntity party, @Nullable ServerPlayerEntity trigger,
                                 @Nullable Vec3d anchor, Text title, Text caption, List<Line> lines, int result) {
        if (lines.isEmpty() || lines.size() > OutcomeRoulette.MAX_LINES || result < 0 || result >= lines.size())
            throw new IllegalArgumentException("result " + result + " out of " + lines.size() + " lines");
        RUNNING.removeIf(Roulette::isOver);
        Roulette roulette = new Roulette(nextId++, world, party, trigger == null ? null : trigger.getUuid(), anchor, title, caption, lines, result);
        RUNNING.add(roulette);
        for (ServerPlayerEntity player : recipients(party, trigger)) roulette.send(player);
        return roulette;
    }

    /** Its show was stopped before the end: off every screen. */
    public static void stop(@Nullable Roulette roulette) {
        if (roulette == null || !RUNNING.remove(roulette) || roulette.isOver()) return;
        OutcomeRoulettePayload stop = OutcomeRoulettePayload.stop(roulette.id);
        for (UUID id : roulette.sentTo) {
            ServerPlayerEntity player = roulette.world.getServer().getPlayerManager().getPlayer(id);
            if (player != null && !player.isDisconnected()) ServerPlayNetworking.send(player, stop);
        }
    }

    /**
     * Who sees a roulette of {@code party}: its players online wherever they are, its audience (who follows it), and
     * {@code trigger}.
     */
    public static List<ServerPlayerEntity> recipients(PartyControllerEntity party, @Nullable ServerPlayerEntity trigger) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        if (party.getWorld() instanceof ServerWorld world) {
            for (UUID id : party.getPlayersInOrder()) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
                if (player != null && !player.isDisconnected() && !players.contains(player)) players.add(player);
            }
        }
        for (ServerPlayerEntity player : party.getPartyAudience()) if (!players.contains(player)) players.add(player);
        if (trigger != null && !players.contains(trigger)) players.add(trigger);
        return players;
    }

    /** A player joining while a roulette of their party runs: it shows where it is. */
    private static void joined(ServerPlayerEntity player) {
        RUNNING.removeIf(Roulette::isOver);
        for (Roulette roulette : RUNNING) {
            if (roulette.party.isRemoved()) continue;
            if (recipients(roulette.party, null).contains(player)) roulette.send(player);
        }
    }
}
