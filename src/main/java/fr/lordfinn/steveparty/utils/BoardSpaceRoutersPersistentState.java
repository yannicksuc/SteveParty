package fr.lordfinn.steveparty.utils;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.payloads.BlockPosesMapPayload;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.*;

public class BoardSpaceRoutersPersistentState extends PersistentState {
    protected static Map<BlockPos, BlockPos> boardSpaces = new HashMap<>();
    private static final Type<BoardSpaceRoutersPersistentState> type = new Type<>(
            BoardSpaceRoutersPersistentState::new,
            BoardSpaceRoutersPersistentState::createFromNbt,
            null
    );

    private static BoardSpaceRoutersPersistentState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        BoardSpaceRoutersPersistentState state = new BoardSpaceRoutersPersistentState();
        state.readFromNbt(nbt);
        return state;
    }

    public static BoardSpaceRoutersPersistentState get(MinecraftServer server) {
        return BoardSpaceRoutersPersistentState.getOrCreate(server, type, "board_space_routers");
    }

    public void put(BlockPos boardSpacePos, BlockPos routerPos) {
        boardSpaces.put(boardSpacePos, routerPos);
        markDirty();
    }

    public void remove(BlockPos boardSpacePos) {
        boardSpaces.remove(boardSpacePos);
        markDirty();
    }

    public Map<BlockPos, BlockPos> getAll() {
        return boardSpaces;
    }

    protected static <T extends BoardSpaceRoutersPersistentState> T getOrCreate(
            MinecraftServer server,
            Type<T> type,
            String name
    ) {
        if (server == null || server.getWorld(World.OVERWORLD) == null) return null;
        PersistentStateManager manager = Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getPersistentStateManager();
        if (manager == null) return null;
        T state = manager.getOrCreate(type, name);
        state.markDirty();
        return state;
    }

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

    protected void readFromNbt(NbtCompound nbt) {
        NbtList list = nbt.getList("BoardSpacesRouters", 10); // 10 = NbtCompound type
        for (int i = 0; i < list.size(); i++) {
            NbtCompound spaceNbt = list.getCompound(i);
            BlockPos boardSpacePos = new BlockPos(spaceNbt.getInt("boardSpaceX"), spaceNbt.getInt("boardSpaceY"), spaceNbt.getInt("boardSpaceZ"));
            BlockPos routerPos = new BlockPos(spaceNbt.getInt("routerX"), spaceNbt.getInt("routerY"), spaceNbt.getInt("routerZ"));
            boardSpaces.put(boardSpacePos, routerPos);
        }
    }

    public void clear(BlockPos routerPos, ServerWorld serverWorld) {
        Iterator<Map.Entry<BlockPos, BlockPos>> iterator = boardSpaces.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BlockPos> entry = iterator.next();
            if (entry.getValue().equals(routerPos)) {
                iterator.remove(); // Safely removes the current entry from the map
                if (serverWorld.getBlockEntity(entry.getKey()) instanceof BoardSpaceBlockEntity boardSpaceBlockEntity)
                    boardSpaceBlockEntity.markDirty();
            }
        }
    }

    public void putAll(List<BlockPos> boardSpaces, BlockPos router, ServerWorld serverWorld) {
        for (BlockPos boardSpacePos : boardSpaces) {
            put(boardSpacePos, router);
            if (serverWorld.getBlockEntity(boardSpacePos) instanceof BoardSpaceBlockEntity boardSpaceBlockEntity)
                boardSpaceBlockEntity.markDirty();
        }
    }



    public static BlockPos get(BlockPos boardSpacePos) {
        return boardSpaces.getOrDefault(boardSpacePos, null);
    }

    public static void set(Map<BlockPos, BlockPos> boardSpaces) {
        BoardSpaceRoutersPersistentState.boardSpaces = boardSpaces;
    }

    public static void sendToPlayer(ServerPlayerEntity player, MinecraftServer server) {
        ServerPlayNetworking.send(player, new BlockPosesMapPayload(BoardSpaceRoutersPersistentState.get(server).getAll()));
    }
    public static void sendToOnlinePlayers(MinecraftServer server) {
        server.getPlayerManager().getPlayerList().forEach(player -> sendToPlayer(player, server));
    }
}
