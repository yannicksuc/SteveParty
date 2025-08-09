package fr.lordfinn.steveparty.persistent_state;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.BlockPosesMapPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.*;

public class BoardSpaceRoutersPersistentState extends PersistentState {

    private final Map<BlockPos, BlockPos> boardSpaces = Collections.synchronizedMap(new HashMap<>());

    // Fabric's PersistentState loader
    private static final Type<BoardSpaceRoutersPersistentState> TYPE = new Type<>(
            BoardSpaceRoutersPersistentState::new,
            BoardSpaceRoutersPersistentState::fromNbt,
            null
    );

    // Factory for reading from disk
    private static BoardSpaceRoutersPersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        BoardSpaceRoutersPersistentState state = new BoardSpaceRoutersPersistentState();
        state.readFromNbt(nbt);
        return state;
    }

    // Get or create persistent state for OVERWORLD
    public static BoardSpaceRoutersPersistentState get(MinecraftServer server) {
        if (server == null || server.getWorld(World.OVERWORLD) == null) return null;
        PersistentStateManager manager = server.getWorld(World.OVERWORLD).getPersistentStateManager();
        return manager.getOrCreate(TYPE, "board_space_routers");
    }

    /** Add or update a mapping **/
    public void put(BlockPos boardSpacePos, BlockPos routerPos) {
        boardSpaces.put(boardSpacePos, routerPos);
        markDirty();
    }

    /** Remove a mapping **/
    public void remove(BlockPos boardSpacePos) {
        if (boardSpaces.remove(boardSpacePos) != null) {
            markDirty();
        }
    }

    /** Get all mappings **/
    public Map<BlockPos, BlockPos> getAll() {
        synchronized (boardSpaces) {
            return Collections.unmodifiableMap(new HashMap<>(boardSpaces));
        }
    }

    /** Clear all board spaces that belong to a router **/
    public void clear(BlockPos routerPos, ServerWorld serverWorld) {
        Iterator<Map.Entry<BlockPos, BlockPos>> iterator = boardSpaces.entrySet().iterator();
        boolean removed = false;

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BlockPos> entry = iterator.next();
            if (entry.getValue().equals(routerPos)) {
                iterator.remove();
                removed = true;

                if (serverWorld.getBlockEntity(entry.getKey()) instanceof BoardSpaceBlockEntity be) {
                    be.markDirty();
                }
            }
        }

        if (removed) {
            markDirty();
        }
    }

    /** Add multiple board spaces to a router **/
    public void putAll(List<BlockPos> boardSpacesList, BlockPos router, ServerWorld serverWorld) {
        boolean changed = false;
        for (BlockPos boardSpacePos : boardSpacesList) {
            boardSpaces.put(boardSpacePos, router);
            changed = true;

            if (serverWorld.getBlockEntity(boardSpacePos) instanceof BoardSpaceBlockEntity be) {
                be.markDirty();
            }
        }
        if (changed) {
            markDirty();
        }
    }

    /** Get router linked to a board space **/
    public BlockPos get(BlockPos boardSpacePos) {
        return boardSpaces.get(boardSpacePos);
    }

    /** Replace the whole mapping **/
    public void set(Map<BlockPos, BlockPos> newBoardSpaces) {
        boardSpaces.clear();
        boardSpaces.putAll(newBoardSpaces);
        markDirty();
    }

    /** Save to NBT **/
    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (Map.Entry<BlockPos, BlockPos> entry : boardSpaces.entrySet()) {
            NbtCompound spaceNbt = new NbtCompound();
            spaceNbt.putInt("boardSpaceX", entry.getKey().getX());
            spaceNbt.putInt("boardSpaceY", entry.getKey().getY());
            spaceNbt.putInt("boardSpaceZ", entry.getKey().getZ());
            spaceNbt.putInt("routerX", entry.getValue().getX());
            spaceNbt.putInt("routerY", entry.getValue().getY());
            spaceNbt.putInt("routerZ", entry.getValue().getZ());
            list.add(spaceNbt);
        }
        nbt.put("BoardSpacesRouters", list);
        return nbt;
    }

    /** Load from NBT **/
    protected void readFromNbt(NbtCompound nbt) {
        boardSpaces.clear();
        NbtList list = nbt.getList("BoardSpacesRouters", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound spaceNbt = list.getCompound(i);
            BlockPos boardSpacePos = new BlockPos(
                    spaceNbt.getInt("boardSpaceX"),
                    spaceNbt.getInt("boardSpaceY"),
                    spaceNbt.getInt("boardSpaceZ")
            );
            BlockPos routerPos = new BlockPos(
                    spaceNbt.getInt("routerX"),
                    spaceNbt.getInt("routerY"),
                    spaceNbt.getInt("routerZ")
            );
            boardSpaces.put(boardSpacePos, routerPos);
        }
    }

    /** Send to one player **/
    public static void sendToPlayer(ServerPlayerEntity player, MinecraftServer server) {
        BoardSpaceRoutersPersistentState state = get(server);
        if (state != null) {
            Map<BlockPos, BlockPos> snapshot = state.getAll();
            ServerPlayNetworking.send(player, new BlockPosesMapPayload(snapshot));
        }
    }

    /** Send to all players **/
    public static void sendToOnlinePlayers(MinecraftServer server) {
        BoardSpaceRoutersPersistentState state = get(server);
        if (state != null) {
            Map<BlockPos, BlockPos> snapshot = state.getAll();
            server.getPlayerManager().getPlayerList().forEach(player ->
                    ServerPlayNetworking.send(player, new BlockPosesMapPayload(snapshot))
            );
        }
    }
}
