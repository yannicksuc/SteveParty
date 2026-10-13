package fr.lordfinn.steveparty.advancement;

import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * SteveParty's advancement triggers. Call {@link #fire} wherever a moment an advancement listens to happens,
 * server side; it costs nothing when no player still waits for that advancement.
 */
public final class ModCriteria {
    public static final PartyEventCriterion EVENT = Criteria.register("steveparty:event", new PartyEventCriterion());

    private ModCriteria() {}

    public static void initialize() {
        // class load registers the criteria
    }

    public static void fire(@Nullable PlayerEntity player, String event) {
        fire(player, event, 1, null);
    }

    public static void fire(@Nullable PlayerEntity player, String event, int value) {
        fire(player, event, value, null);
    }

    public static void fire(@Nullable PlayerEntity player, String event, @Nullable Entity entity) {
        fire(player, event, 1, entity);
    }

    public static void fire(@Nullable PlayerEntity player, String event, int value, @Nullable Entity entity) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            EVENT.trigger(serverPlayer, event, value, entity);
        }
    }

    /** Fires for the player {@code player} if online (an owner, a roller... known by UUID); nothing otherwise. */
    public static void fire(@Nullable MinecraftServer server, @Nullable UUID player, String event, int value, @Nullable Entity entity) {
        if (server != null && player != null) fire(server.getPlayerManager().getPlayer(player), event, value, entity);
    }

    /** Fires for the owner of the pawn {@code pawn} (a tokenized mob, a player pawn), if online. */
    public static void fireOwner(@Nullable Entity pawn, String event, int value) {
        if (pawn instanceof TokenizedEntityInterface token)
            fire(pawn.getServer(), token.steveparty$getTokenOwner(), event, value, null);
    }

    /** Fires for every player (not spectating) within {@code radius} blocks of {@code pos}: the witnesses. */
    public static void fireNear(ServerWorld world, Vec3d pos, double radius, String event, int value, @Nullable Entity entity) {
        double r2 = radius * radius;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isSpectator() && player.squaredDistanceTo(pos) <= r2) {
                EVENT.trigger(player, event, value, entity);
            }
        }
    }
}
