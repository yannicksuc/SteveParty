package fr.lordfinn.steveparty.items.custom.jumpshoes;

import fr.lordfinn.steveparty.payloads.custom.JumpShoesPayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The server side of the Triple Jump Shoes: checks the moves the clients report, relays them to the players around
 * and spares the fall damage of the accepted ones. Only the players in the air after a move are ticked.
 */
public final class JumpShoesServer {
    private static final Map<UUID, JumpShoesState> AIRBORNE = new HashMap<>();

    private JumpShoesServer() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(JumpShoesServer::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> AIRBORNE.clear());
    }

    public static void onMove(ServerPlayerEntity player, byte actionId, byte sideId) {
        JumpShoes.Action action = JumpShoes.Action.byId(actionId);
        if (action == null) return;
        Direction side = sideId >= 0 && sideId < 6 ? Direction.byId(sideId) : null;
        JumpShoesState state = AIRBORNE.computeIfAbsent(player.getUuid(), uuid -> new JumpShoesState());
        if (!state.accept(player, action, side)) return;
        JumpShoesPayloads.Seen seen = new JumpShoesPayloads.Seen(player.getId(), actionId, sideId);
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            if (other != player) ServerPlayNetworking.send(other, seen);
        }
    }

    private static void tick(MinecraftServer server) {
        if (AIRBORNE.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, JumpShoesState>> it = AIRBORNE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, JumpShoesState> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || player.isRemoved() || !entry.getValue().tick(player)) it.remove();
        }
    }
}
