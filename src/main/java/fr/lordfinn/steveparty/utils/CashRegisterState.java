package fr.lordfinn.steveparty.utils;

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

public class CashRegisterState extends PersistentState {
    private final Set<BlockPos> cashRegisterPositions = new HashSet<>();
    private static final Type<CashRegisterState> type = new Type<>(
            CashRegisterState::new,
            CashRegisterState::createFromNbt,
            null
    );

    public void addPosition(BlockPos pos) {
        cashRegisterPositions.add(pos);
        markDirty();
    }

    public void removePosition(BlockPos pos) {
        cashRegisterPositions.remove(pos);
        markDirty();
    }

    public Set<BlockPos> getPositions() {
        return cashRegisterPositions;
    }

    private static CashRegisterState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CashRegisterState state = new CashRegisterState();
        NbtList list = nbt.getList("CashRegisters", 10); // 10 = NbtCompound type
        for (int i = 0; i < list.size(); i++) {
            NbtCompound posNbt = list.getCompound(i);
            BlockPos pos = new BlockPos(posNbt.getInt("x"), posNbt.getInt("y"), posNbt.getInt("z"));
            state.addPosition(pos);
        }
        return state;
    }

    public static CashRegisterState get(MinecraftServer server) {
        if (server == null || server.getWorld(World.OVERWORLD) == null) return null;
        PersistentStateManager manager = Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getPersistentStateManager();
        if (manager == null) return null;
        CashRegisterState state = manager.getOrCreate(type, "cash_registers");
        state.markDirty();
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (BlockPos pos : cashRegisterPositions) {
            NbtCompound posNbt = new NbtCompound();
            posNbt.putInt("x", pos.getX());
            posNbt.putInt("y", pos.getY());
            posNbt.putInt("z", pos.getZ());
            list.add(posNbt);
        }
        nbt.put("CashRegisters", list);
        return nbt;
    }
}
