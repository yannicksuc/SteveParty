package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGameTeleports;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The way back from a mini-game, for every way to play one (a party's practice and real rounds, a test out of any
 * party): where each player stood before it, and the player brought back there when it is over.
 * <p>
 * A player who is not on the server when its round ends is brought back when it comes: where it would have been
 * brought, kept with the world (a restart in between changes nothing). Its own inventory, ender chest, experience and
 * game mode are not this class's business: a player leaving a zone bubble takes them back as it leaves (or, after a
 * crash, as it comes back: {@link ZoneBubbles#settle}). Both are done by the one hook of {@link #onJoin}, which tells
 * the player in one line.
 */
public final class MiniGameReturns extends PersistentState {
    private static final String ID = "steveparty_minigame_returns";
    private static final Type<MiniGameReturns> TYPE = new Type<>(MiniGameReturns::new, (nbt, registries) -> fromNbt(nbt), null);

    /** Where a player goes back: its dimension, position and look. */
    public record Return(RegistryKey<World> dimension, Vec3d pos, float yaw, float pitch) {
        public static Return of(ServerPlayerEntity player) {
            return new Return(player.getWorld().getRegistryKey(), player.getPos(), player.getYaw(), player.getPitch());
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Dimension", dimension.getValue().toString());
            nbt.putDouble("X", pos.x);
            nbt.putDouble("Y", pos.y);
            nbt.putDouble("Z", pos.z);
            nbt.putFloat("Yaw", yaw);
            nbt.putFloat("Pitch", pitch);
            return nbt;
        }

        /** An unknown or missing dimension is the overworld. */
        public static Return fromNbt(NbtCompound nbt) {
            Identifier dimension = nbt.getString("Dimension").isEmpty() ? null : Identifier.tryParse(nbt.getString("Dimension"));
            return new Return(dimension == null ? World.OVERWORLD : RegistryKey.of(RegistryKeys.WORLD, dimension),
                    new Vec3d(nbt.getDouble("X"), nbt.getDouble("Y"), nbt.getDouble("Z")), nbt.getFloat("Yaw"), nbt.getFloat("Pitch"));
        }
    }

    /** The players to bring back when they come, the round they played being over. */
    private final Map<UUID, Return> pending = new LinkedHashMap<>();

    public static void initialize() {
        // The only join hook of the mini-games: the bubble's inventory first, then the way back
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
        // Back on its death screen: the way back waits for the respawn, which would undo it. And for the tick after:
        // during the respawn its connection still holds (and a teleport still moves) the dead player, not the new one
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive || !get(newPlayer.server).pending.containsKey(newPlayer.getUuid())) return;
            MinecraftServer server = newPlayer.server;
            UUID id = newPlayer.getUuid();
            Steveparty.SCHEDULER.schedule(UUID.randomUUID(), 1, () -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player != null && !player.isDead()) bringBack(player, false);
            });
        });
    }

    private static MiniGameReturns get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    /**
     * The round of {@code player} is over: back where it stood now if it is on the server, else as soon as it
     * comes.
     *
     * @return true if it was brought back now
     */
    public static boolean bringBack(MinecraftServer server, UUID player, Return back) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
        // On his death screen: brought back once respawned (AFTER_RESPAWN), the respawn would undo a teleport now
        if (online != null && !online.isDead()) return teleport(online, back);
        MiniGameReturns state = get(server);
        state.pending.put(player, back);
        state.markDirty();
        return false;
    }

    /** @return true if {@code player} is to be brought back when it comes */
    public static boolean isPending(MinecraftServer server, UUID player) {
        return get(server).pending.containsKey(player);
    }

    /** For the tests: the pending returns are read back from what would be saved, as a restarted server reads them. */
    public static void simulateRestart(MinecraftServer server) {
        NbtCompound saved = get(server).writeNbt(new NbtCompound(), server.getRegistryManager());
        server.getOverworld().getPersistentStateManager().set(ID, fromNbt(saved));
    }

    private static boolean teleport(ServerPlayerEntity player, Return back) {
        ServerWorld world = player.server.getWorld(back.dimension());
        if (world == null) return false;
        if (player.hasVehicle()) player.stopRiding();
        // A teleport of the mod: it passes the border of any zone in session
        ZoneBubbles.allowTeleports(() -> MiniGameTeleports.teleport(player, world, back.pos(), back.yaw(), back.pitch()));
        return true;
    }

    /**
     * A player comes on the server: its own inventory if it was saved holding a session one, then the place a round
     * ended while it was away would have brought it back to. One line says what happened.
     */
    public static void onJoin(ServerPlayerEntity player) {
        boolean inventory = ZoneBubbles.settle(player);
        // Dead, it is brought back once respawned (AFTER_RESPAWN): only what it owns is settled now
        if (player.isDead()) {
            if (inventory) player.sendMessage(Text.translatable("message.steveparty.zone_bubble.inventory_back"), false);
            return;
        }
        if (!get(player.server).pending.containsKey(player.getUuid())) {
            bringBack(player, inventory);
            return;
        }
        // The tick after its join: the join itself puts it back where it was saved (in the arena) once the join hooks
        // have run, which would undo a teleport now
        MinecraftServer server = player.server;
        UUID id = player.getUuid();
        Steveparty.SCHEDULER.schedule(UUID.randomUUID(), 1, () -> {
            ServerPlayerEntity joined = server.getPlayerManager().getPlayer(id);
            if (joined != null && !joined.isDead()) bringBack(joined, inventory);
        });
    }

    /** Puts the player where its round would have brought it back, if one ended while it was away, and says so. */
    private static void bringBack(ServerPlayerEntity player, boolean inventory) {
        MiniGameReturns state = get(player.server);
        Return back = state.pending.remove(player.getUuid());
        boolean moved = false;
        if (back != null) {
            state.markDirty();
            moved = teleport(player, back);
        }
        @Nullable String key = moved && inventory ? "message.steveparty.minigame.back_while_away"
                : moved ? "message.steveparty.minigame.back_while_away.place"
                : inventory ? "message.steveparty.zone_bubble.inventory_back" : null;
        if (key != null) player.sendMessage(Text.translatable(key), false);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtCompound players = new NbtCompound();
        pending.forEach((uuid, back) -> players.put(uuid.toString(), back.toNbt()));
        nbt.put("Pending", players);
        return nbt;
    }

    private static MiniGameReturns fromNbt(NbtCompound nbt) {
        MiniGameReturns state = new MiniGameReturns();
        NbtCompound players = nbt.getCompound("Pending");
        for (String key : players.getKeys()) {
            try {
                state.pending.put(UUID.fromString(key), Return.fromNbt(players.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }
}
