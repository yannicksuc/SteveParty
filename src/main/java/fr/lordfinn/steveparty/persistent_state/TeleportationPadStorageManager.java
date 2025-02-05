package fr.lordfinn.steveparty.persistent_state;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentStateManager;

public class TeleportationPadStorageManager {
    public static TeleportationPadBooksStorage getBooksStorage(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TeleportationPadBooksStorage.TYPE, "teleportation_pad_storage");
    }

    public static TeleportationHistoryStorage getTeleportationHistoryStorage(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TeleportationHistoryStorage.TYPE, "last_used_teleportation_pad_by_player_storage");
    }
}