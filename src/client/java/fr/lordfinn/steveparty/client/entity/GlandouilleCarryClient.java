package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleTowers;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;

/**
 * A player carrying Glandouilles (held in front of him, see {@link GlandouilleTowers#heldPos}): both arms forward, and
 * his left click throws the bottom one instead of attacking or breaking.
 */
public final class GlandouilleCarryClient {
    private GlandouilleCarryClient() {
    }

    /** {@code entity} carries a Glandouille stack. */
    public static boolean carrying(Entity entity) {
        for (Entity passenger : entity.getPassengerList()) {
            if (passenger instanceof GlandouilleEntity) return true;
        }
        return false;
    }

    public static void initialize() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (client.currentScreen != null || !carrying(player)) return false;
            if (clickCount > 0 && ClientPlayNetworking.canSend(GlandouilleTowers.ThrowCarried.ID)) {
                ClientPlayNetworking.send(new GlandouilleTowers.ThrowCarried());
                player.swingHand(Hand.MAIN_HAND);
            }
            return true;
        });
    }
}
