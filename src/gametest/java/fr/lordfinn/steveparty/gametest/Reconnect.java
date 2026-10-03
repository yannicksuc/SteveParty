package fr.lordfinn.steveparty.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

/** A player leaving the server as a client does, and coming back: the server's own disconnection and join. */
final class Reconnect {
    private Reconnect() {
    }

    /**
     * The player leaves as when the server lets it go: its connection is closed and handled as closed (the
     * disconnection hooks run, then it is saved and removed).
     */
    static void leave(ServerPlayerEntity player) {
        player.networkHandler.disconnect(Text.empty());
    }

    /** The player of {@code profile} comes back: read from what was saved, then the join hooks run. */
    static ServerPlayerEntity join(TestContext context, GameProfile profile) {
        ServerWorld world = context.getWorld();
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        return player;
    }
}
