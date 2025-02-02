package fr.lordfinn.steveparty.persistent_state;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentStateManager;

public class TeleportationPadStorageManager {
    public static TeleportationPadStorage getStorage(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TeleportationPadStorage.TYPE, "teleportation_pad_storage");
    }
}