package fr.lordfinn.steveparty.client.pawn;

import fr.lordfinn.steveparty.entities.custom.pawn.PawnPossessions;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

/**
 * Client side of a player inside a pawn: the local player is inside when their camera is a pawn they possess (the
 * server set it); any player is drawn nowhere while a pawn around says it holds them.
 */
public final class PawnPossessionClient {
    private PawnPossessionClient() {
    }

    public static void initialize() {
        PawnPossessions.setClientCheck(PawnPossessionClient::isLocalPlayerInside);
    }

    public static boolean isLocalPlayerInside(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        return player == client.player && client.getCameraEntity() instanceof PlayerPawnEntity pawn
                && player.getUuid().equals(pawn.getPossessor());
    }

    /** Whether {@code player} is inside a pawn (held at its position by the server): not to be drawn. */
    public static boolean isHidden(PlayerEntity player) {
        if (isLocalPlayerInside(player)) return true;
        UUID id = player.getUuid();
        return !player.getWorld().getEntitiesByClass(PlayerPawnEntity.class, player.getBoundingBox().expand(1.0),
                pawn -> id.equals(pawn.getPossessor())).isEmpty();
    }
}
