package fr.lordfinn.steveparty.blocks.custom.PartyController;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The party controllers kept loaded while their party plays a mini-game, in a world (saved with it).
 * <p>
 * A mini-game is played away from the board, sometimes in another dimension, with nobody left near the party
 * controller: without it loaded, the podiums and the step controllers of the mini-game would find no party to end,
 * pay and bring back. So a controller on a mini-game step holds a chunk ticket of its own kind on its chunk (it stays
 * loaded and ticking, like a forced chunk, without being one: {@code /forceload} neither sees it nor undoes it), given
 * back when the step is over, the party stopped or the controller broken ({@code PartyControllerEntity#syncChunkHold}).
 * <p>
 * Tickets are not saved by the game: the held controllers are, and their tickets are taken again when the world
 * loads, so that a mini-game in progress when the server stopped can still end. A hold whose controller is gone, or
 * no longer on a mini-game, is dropped by a check every few seconds. Only the controller's chunk is held: never the
 * place the mini-game is played in.
 */
public final class PartyChunkHolds extends PersistentState {
    /** Like a forced chunk: the chunk and what is in it tick. */
    private static final int TICKET_RADIUS = 2;
    private static final int VERIFY_INTERVAL_TICKS = 200;
    public static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("steveparty_mini_game", Comparator.comparingLong(ChunkPos::toLong));
    private static final String ID = "steveparty_party_chunk_holds";
    private static final Type<PartyChunkHolds> TYPE = new Type<>(PartyChunkHolds::new, PartyChunkHolds::fromNbt, null);

    /** The controllers holding their chunk. */
    private final Set<BlockPos> controllers = new LinkedHashSet<>();

    public static void initialize() {
        ServerWorldEvents.LOAD.register((server, world) -> restore(world));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % VERIFY_INTERVAL_TICKS != 0) return;
            for (ServerWorld world : server.getWorlds()) verify(world);
        });
    }

    /** The holds of a world. */
    public static PartyChunkHolds get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    private boolean holdsChunk(ChunkPos chunk) {
        for (BlockPos controller : controllers) if (new ChunkPos(controller).equals(chunk)) return true;
        return false;
    }

    /** @return true if the controller at {@code controller} keeps its chunk loaded. */
    public static boolean isHeld(ServerWorld world, BlockPos controller) {
        return get(world).controllers.contains(controller);
    }

    /** @return true if a chunk is kept loaded for a controller in it. */
    public static boolean isChunkHeld(ServerWorld world, ChunkPos chunk) {
        return get(world).holdsChunk(chunk);
    }

    /** The controllers of a world that keep their chunk loaded. */
    public static List<BlockPos> held(ServerWorld world) {
        return new ArrayList<>(get(world).controllers);
    }

    /** The controller at {@code controller} keeps its chunk loaded from now on. */
    public static void hold(ServerWorld world, BlockPos controller) {
        PartyChunkHolds holds = get(world);
        ChunkPos chunk = new ChunkPos(controller);
        boolean first = !holds.holdsChunk(chunk);
        if (!holds.controllers.add(controller.toImmutable())) return;
        holds.markDirty();
        if (first) world.getChunkManager().addTicket(TICKET, chunk, TICKET_RADIUS, chunk);
    }

    /** The controller at {@code controller} no longer needs its chunk: let go once no controller of the chunk does. */
    public static void release(ServerWorld world, BlockPos controller) {
        PartyChunkHolds holds = get(world);
        if (!holds.controllers.remove(controller)) return;
        holds.markDirty();
        ChunkPos chunk = new ChunkPos(controller);
        if (!holds.holdsChunk(chunk)) world.getChunkManager().removeTicket(TICKET, chunk, TICKET_RADIUS, chunk);
    }

    /** The world was loaded: the tickets of the controllers that held their chunk are taken again. */
    static void restore(ServerWorld world) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (BlockPos controller : get(world).controllers) chunks.add(new ChunkPos(controller));
        for (ChunkPos chunk : chunks) world.getChunkManager().addTicket(TICKET, chunk, TICKET_RADIUS, chunk);
    }

    /** Drops the holds whose controller is gone or no longer on a mini-game step (its chunk is loaded: it can be asked). */
    static void verify(ServerWorld world) {
        for (BlockPos controller : held(world)) {
            ChunkPos chunk = new ChunkPos(controller);
            if (!world.isChunkLoaded(chunk.x, chunk.z)) continue;
            if (!(world.getBlockEntity(controller) instanceof PartyControllerEntity entity) || !entity.wantsChunk()) release(world, controller);
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        nbt.putLongArray("Controllers", controllers.stream().mapToLong(BlockPos::asLong).toArray());
        return nbt;
    }

    public static PartyChunkHolds fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        PartyChunkHolds holds = new PartyChunkHolds();
        for (long pos : nbt.getLongArray("Controllers")) holds.controllers.add(BlockPos.fromLong(pos));
        return holds;
    }
}
