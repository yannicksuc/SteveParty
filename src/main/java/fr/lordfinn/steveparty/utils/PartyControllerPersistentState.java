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

    public static PartyControllerPersistentState get(MinecraftServer server) {
        return BlockListPersistentState.getOrCreate(server, type, "party_controllers");
    }
}
