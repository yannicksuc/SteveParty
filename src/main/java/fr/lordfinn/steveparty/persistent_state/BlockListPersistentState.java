package fr.lordfinn.steveparty.persistent_state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class BlockListPersistentState extends PersistentState {
    protected final Set<BlockPos> positions = new HashSet<>();

    public void addPosition(BlockPos pos) {
        positions.add(pos);
        markDirty();
    }

    public void removePosition(BlockPos pos) {
        positions.remove(pos);
        markDirty();
    }

    public Set<BlockPos> getPositions() {
        return positions;
    }

    protected static <T extends BlockListPersistentState> T getOrCreate(
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
        for (BlockPos pos : positions) {
            NbtCompound posNbt = new NbtCompound();
            posNbt.putInt("x", pos.getX());
            posNbt.putInt("y", pos.getY());
            posNbt.putInt("z", pos.getZ());
            list.add(posNbt);
        }
        nbt.put("Positions", list);
        return nbt;
    }

    protected void readFromNbt(NbtCompound nbt) {
        NbtList list = nbt.getList("Positions", 10); // 10 = NbtCompound type
        for (int i = 0; i < list.size(); i++) {
            NbtCompound posNbt = list.getCompound(i);
            BlockPos pos = new BlockPos(posNbt.getInt("x"), posNbt.getInt("y"), posNbt.getInt("z"));
            positions.add(pos);
        }
    }
}
