package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.entities.BoardActor;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.utils.MessageUtils;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

/**
 * The shows of board spaces summoning mobs ({@link FrousseuxThefts}, {@link GlandouillePushes},
 * {@link MistigriSentences}): at most one per token that landed, ticked every tick until it is over, its actors
 * ({@link BoardActors}) removed with it. Each service keeps one registry of its own {@link Sequence} class; forgotten
 * when the server stops. Server thread only.
 */
public final class BoardSequences<S extends BoardSequences.Sequence> {
    /** The running sequences, by the UUID of the token that landed. */
    private final Map<UUID, S> running = ServerMemory.forgetOnStop(new HashMap<>());

    /** The server stopping in the middle: {@code stop} for each sequence still running (none is ever resumed). */
    public void stopWithServer(Consumer<S> stop) {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (S sequence : new ArrayList<>(running.values())) stop.accept(sequence);
        });
    }

    public boolean isRunning(MobEntity token) {
        return running.containsKey(token.getUuid());
    }

    /** The sequence of {@code token}'s landing, null if none. */
    public @Nullable S get(MobEntity token) {
        return running.get(token.getUuid());
    }

    /** {@code sequence} (set up) runs: {@code tick} every tick until it {@link Sequence#close closes}. */
    public void run(S sequence, Runnable tick) {
        ((Sequence) sequence).registry = this;
        running.put(sequence.token.getUuid(), sequence);
        SCHEDULER.repeat(sequence.task, 1, tick, () -> !sequence.done, () -> {
        });
    }

    /** The online player of {@code token}, null if none (or not online). */
    public static @Nullable ServerPlayerEntity tokenPlayer(ServerWorld world, MobEntity token) {
        UUID id = token instanceof TokenizedEntityInterface tokenized ? tokenized.steveparty$getTokenOwner() : null;
        ServerPlayerEntity player = id == null ? null : world.getServer().getPlayerManager().getPlayer(id);
        return player == null || player.isDisconnected() ? null : player;
    }

    /** {@code message} to {@code party}'s audience, and to these players (null: nobody). */
    public static void tell(PartyControllerEntity party, Text message, @Nullable ServerPlayerEntity... also) {
        List<ServerPlayerEntity> audience = new ArrayList<>(party.getPartyAudience());
        for (ServerPlayerEntity player : also) if (player != null && !audience.contains(player)) audience.add(player);
        MessageUtils.sendToPlayers(audience, message, MessageUtils.MessageType.CHAT);
    }

    /** The yaw of a mob at {@code from} facing {@code to}. */
    public static float yawToward(Vec3d from, Vec3d to) {
        return (float) (MathHelper.atan2(to.z - from.z, to.x - from.x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
    }

    /**
     * One show of a token's landing: its scheduler task (also the {@link BoardActors} sequence of its actors), and
     * {@code onDone}, run by the service when it is over. A subclass ends it with {@link #close} (once), removes its
     * actors with {@link #dismissActors}, and calls {@link #onDone} as it says.
     */
    public abstract static class Sequence {
        protected final UUID task = UUID.randomUUID();
        protected final ServerWorld world;
        protected final MobEntity token;
        protected final Runnable onDone;
        /** Over: no more ticks, its prompts' answers ignored. */
        protected boolean done;
        private @Nullable BoardSequences<?> registry;

        protected Sequence(ServerWorld world, MobEntity token, Runnable onDone) {
            this.world = world;
            this.token = token;
            this.onDone = onDone;
        }

        /** Makes {@code actor} (before it is spawned) a board actor of this show, removed when it is dismissed. */
        protected <T extends MobEntity & BoardActor> T cast(T actor) {
            actor.makeBoardActor();
            return BoardActors.join(task, actor);
        }

        /** Makes {@code prop} (before it is spawned) one of this show's actors (not a mob: a die...). */
        protected <T extends Entity> T castProp(T prop) {
            return BoardActors.join(task, prop);
        }

        /** Ends it: no more ticks, no longer its token's. False when it was already over (nothing to do then). */
        protected final boolean close() {
            if (done) return false;
            done = true;
            SCHEDULER.cancel(task);
            if (registry != null) registry.running.remove(token.getUuid());
            return true;
        }

        /** Its actors are removed (whatever is left of them). */
        protected final void dismissActors() {
            BoardActors.end(task);
        }
    }
}
