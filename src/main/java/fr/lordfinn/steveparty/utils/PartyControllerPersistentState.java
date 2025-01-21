package fr.lordfinn.steveparty.utils;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;

public class PartyControllerPersistentState extends BlockListPersistentState {
    private static final Type<PartyControllerPersistentState> type = new Type<>(
            PartyControllerPersistentState::new,
            PartyControllerPersistentState::createFromNbt,
            null
    );

    private static PartyControllerPersistentState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        PartyControllerPersistentState state = new PartyControllerPersistentState();
        state.readFromNbt(nbt);
        return state;
    }


    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtCompound elem = new NbtCompound();
        elem.put("PartyController", nbt);
        return super.writeNbt(elem, registries);
    }

    @Override
    protected void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("PartyController")) {
            super.readFromNbt(nbt.getCompound("PartyController"));
        }
    }

    public static PartyControllerPersistentState get(MinecraftServer server) {
        return PartyControllerPersistentState.getOrCreate(server, type, "party_controllers");
    }
}
