package fr.lordfinn.steveparty.utils;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UserCache;

import java.util.Optional;
import java.util.UUID;

public class EntitiesUtils {

    /**
     * Retrieves a player's name by their UUID, even if they are offline.
     *
     * @param server The MinecraftServer instance.
     * @param uuid   The UUID of the player.
     * @return The player's name if available, or null if not found.
     */
    public static String getPlayerNameByUuid(MinecraftServer server, UUID uuid) {
        // Check online players first
        ServerPlayerEntity onlinePlayer = server.getPlayerManager().getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getName().getString();
        }

        // Query the user cache for offline players
        UserCache userCache = server.getUserCache();
        Optional<GameProfile> cachedProfile;
        if (userCache != null) {
            cachedProfile = userCache.getByUuid(uuid);
            return cachedProfile.map(GameProfile::getName).orElse(null);
        }
        return null;
    }
}
