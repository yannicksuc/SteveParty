package fr.lordfinn.steveparty.gametest.kit;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Players for the tests: the GameTest mock player, or a player joining the server through a hand-built connection
 * (the join and disconnection hooks run, as for a real client), placed relative to the test, and removed again.
 */
public final class TestPlayers {
    /** Keeps the names of the joined players unique across every test class. */
    private static final AtomicInteger SERIAL = new AtomicInteger();

    private TestPlayers() {
    }

    // ---------------------------------------------------------------- creation

    /** The GameTest mock player (creative, in the test's world, at the world origin of the test). */
    @SuppressWarnings("removal")
    public static ServerPlayerEntity mock(TestContext context) {
        return context.createMockCreativeServerPlayerInWorld();
    }

    /** The GameTest mock player, switched to {@code mode}. */
    public static ServerPlayerEntity mock(TestContext context, GameMode mode) {
        ServerPlayerEntity player = mock(context);
        player.changeGameMode(mode);
        return player;
    }

    /** A profile with a fresh id and a name unique to this run: {@code prefix}, a serial number, {@code name}. */
    public static GameProfile profile(String prefix, String name) {
        return new GameProfile(UUID.randomUUID(), prefix + SERIAL.incrementAndGet() + name);
    }

    /** The player of {@code profile} joins: read from what was saved, then the join hooks run. */
    public static ServerPlayerEntity join(TestContext context, GameProfile profile) {
        ServerWorld world = context.getWorld();
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        return connect(world, new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions()), data);
    }

    /**
     * A joined player (see {@link #join}) named after {@code prefix} and {@code name}, in {@code mode}, with an empty
     * inventory, standing at the relative position ({@code x}, {@code y}, {@code z}).
     */
    public static ServerPlayerEntity joined(TestContext context, String prefix, String name, GameMode mode, double x, double y, double z) {
        return ready(context, join(context, profile(prefix, name)), mode, x, y, z);
    }

    /**
     * A joined creative player (see {@link #joined}) that always reads as creative and never as a spectator, whatever
     * its game mode does in between.
     */
    public static ServerPlayerEntity joinedCreative(TestContext context, String prefix, String name, double x, double y, double z) {
        ServerWorld world = context.getWorld();
        GameProfile profile = profile(prefix, name);
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        return ready(context, connect(world, player, data), GameMode.CREATIVE, x, y, z);
    }

    private static ServerPlayerEntity connect(ServerWorld world, ServerPlayerEntity player, ConnectedClientData data) {
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        return player;
    }

    private static ServerPlayerEntity ready(TestContext context, ServerPlayerEntity player, GameMode mode, double x, double y, double z) {
        player.changeGameMode(mode);
        player.getInventory().clear();
        place(context, player, x, y, z);
        return player;
    }

    // ---------------------------------------------------------------- position

    /** Moves the player to the relative position ({@code x}, {@code y}, {@code z}), looking south and level. */
    public static void place(TestContext context, ServerPlayerEntity player, double x, double y, double z) {
        place(context, player, new Vec3d(x, y, z), 0, 0);
    }

    /** Moves the player to the relative position {@code relative}, looking {@code yaw} / {@code pitch}. */
    public static void place(TestContext context, ServerPlayerEntity player, Vec3d relative, float yaw, float pitch) {
        Vec3d abs = context.getAbsolute(relative);
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, yaw, pitch);
    }

    /** Moves the player to the bottom centre of the relative block {@code at}, looking {@code yaw} and level. */
    public static void placeOn(TestContext context, ServerPlayerEntity player, BlockPos at, float yaw) {
        BlockPos abs = context.getAbsolutePos(at);
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, yaw, 0);
    }

    // ---------------------------------------------------------------- removal

    /** Removes the players from the server, as when they disconnect. */
    public static void remove(TestContext context, ServerPlayerEntity... players) {
        PlayerManager manager = context.getWorld().getServer().getPlayerManager();
        for (ServerPlayerEntity player : players) manager.remove(player);
    }

    /** Removes the players still on the server (some may already have left during the test). */
    public static void removeIfOnline(TestContext context, ServerPlayerEntity... players) {
        PlayerManager manager = context.getWorld().getServer().getPlayerManager();
        for (ServerPlayerEntity player : players) {
            if (manager.getPlayer(player.getUuid()) != null) manager.remove(player);
        }
    }

    /** Removes the player from the server when the test ends. */
    public static void removeAtEnd(TestContext context, ServerPlayerEntity player) {
        TestCleanup.atEnd(context, () -> remove(context, player));
    }

    /** Gets the players off what they ride and out of the mini-game pipes' party. */
    public static void quitMiniGames(ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) {
            if (player.hasVehicle()) player.stopRiding();
            MiniGamePipes.leaveParty(player.getUuid());
        }
    }

    /**
     * Removes every other player within {@code radius} blocks of the relative position {@code center}: players left
     * over by another test running nearby, that would otherwise take part in this one.
     */
    public static void alone(TestContext context, Vec3d center, double radius, ServerPlayerEntity... keep) {
        List<ServerPlayerEntity> own = List.of(keep);
        Vec3d abs = context.getAbsolute(center);
        PlayerManager manager = context.getWorld().getServer().getPlayerManager();
        for (ServerPlayerEntity other : new ArrayList<>(manager.getPlayerList())) {
            if (!own.contains(other) && other.getPos().squaredDistanceTo(abs) < radius * radius) manager.remove(other);
        }
    }

    /**
     * The player leaves as when the server lets it go: its connection is closed and handled as closed (the
     * disconnection hooks run, then it is saved and removed).
     */
    public static void leave(ServerPlayerEntity player) {
        player.networkHandler.disconnect(Text.empty());
    }
}
