package fr.lordfinn.steveparty.utils;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;

public class CashRegisterPersistentState extends BlockListPersistentState {
    private static final Type<CashRegisterPersistentState> type = new Type<>(
            CashRegisterPersistentState::new,
            CashRegisterPersistentState::createFromNbt,
            null
    );

    private static CashRegisterPersistentState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CashRegisterPersistentState state = new CashRegisterPersistentState();
        state.readFromNbt(nbt);
        return state;
    }

    public static CashRegisterPersistentState get(MinecraftServer server) {
        return BlockListPersistentState.getOrCreate(server, type, "cash_registers");
    }
}
