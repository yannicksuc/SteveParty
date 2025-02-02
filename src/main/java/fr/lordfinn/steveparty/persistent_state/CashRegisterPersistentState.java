package fr.lordfinn.steveparty.persistent_state;

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

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtCompound elem = new NbtCompound();
        elem.put("CashRegister", nbt);
        return super.writeNbt(elem, registries);
    }

    @Override
    protected void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("CashRegister")) {
            super.readFromNbt(nbt.getCompound("CashRegister"));
        }
    }

    public static CashRegisterPersistentState get(MinecraftServer server) {
        return CashRegisterPersistentState.getOrCreate(server, type, "cash_registers");
    }
}
