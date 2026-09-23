package fr.lordfinn.steveparty.persistent_state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Which router powers which board space, for one world (dimension).
 * <p>
 * Only read when something changes (a board space loads or a router's cartridge changes), never per tick,
 * and never sent to clients: board spaces sync their resolved active slot themselves.
 * The overworld keeps the historical file name, so existing boards keep their routing.
 */
public class BoardSpaceRoutersPersistentState extends PersistentState {
    private static final String ID = "board_space_routers";

    private final Map<BlockPos, BlockPos> boardSpaces = new HashMap<>();

    private static final Type<BoardSpaceRoutersPersistentState> TYPE = new Type<>(
            BoardSpaceRoutersPersistentState::new,
            BoardSpaceRoutersPersistentState::fromNbt,
            null
    );

    public static BoardSpaceRoutersPersistentState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    @Nullable
    public BlockPos getRouter(BlockPos boardSpacePos) {
        return boardSpaces.get(boardSpacePos);
    }

    /**
     * Makes {@code router} the router of exactly {@code routedBoardSpaces}.
     * @return every board space whose router changed (added to or removed from this router)
     */
    public Set<BlockPos> setRoutedBoardSpaces(BlockPos router, Collection<BlockPos> routedBoardSpaces) {
        Set<BlockPos> changed = new HashSet<>();
        Iterator<Map.Entry<BlockPos, BlockPos>> it = boardSpaces.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, BlockPos> entry = it.next();
            if (entry.getValue().equals(router) && !routedBoardSpaces.contains(entry.getKey())) {
                it.remove();
                changed.add(entry.getKey());
            }
        }
        for (BlockPos boardSpace : routedBoardSpaces) {
            BlockPos previous = boardSpaces.put(boardSpace.toImmutable(), router.toImmutable());
            if (!router.equals(previous)) changed.add(boardSpace);
        }
        if (!changed.isEmpty()) markDirty();
        return changed;
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

    private static BoardSpaceRoutersPersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        BoardSpaceRoutersPersistentState state = new BoardSpaceRoutersPersistentState();
        NbtList list = nbt.getList("BoardSpacesRouters", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound spaceNbt = list.getCompound(i);
            state.boardSpaces.put(
                    new BlockPos(spaceNbt.getInt("boardSpaceX"), spaceNbt.getInt("boardSpaceY"), spaceNbt.getInt("boardSpaceZ")),
                    new BlockPos(spaceNbt.getInt("routerX"), spaceNbt.getInt("routerY"), spaceNbt.getInt("routerZ")));
        }
        return state;
    }
}
