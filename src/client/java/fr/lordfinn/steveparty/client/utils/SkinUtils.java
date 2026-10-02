package fr.lordfinn.steveparty.client.utils;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

public class SkinUtils {
    /**
     * Skin of a player, online or not. Online: the skin of the player list. Offline: the profile is looked up like
     * vanilla player heads do (same cache, one request per player), with the default skin of the UUID meanwhile.
     * Cheap enough to be called every frame.
     */
    public static Identifier getPlayerSkin(UUID uuid) {
        return getSkinTextures(uuid).texture();
    }

    /** Same, with the model of the skin (wide or slim arms). */
    public static net.minecraft.client.util.SkinTextures getSkinTextures(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler != null) {
            PlayerListEntry playerListEntry = networkHandler.getPlayerListEntry(uuid);
            if (playerListEntry != null) return playerListEntry.getSkinTextures();
        }
        Optional<GameProfile> profile = SkullBlockEntity.fetchProfileByUuid(uuid).getNow(Optional.empty());
        return profile.map(p -> client.getSkinProvider().getSkinTextures(p))
                .orElseGet(() -> DefaultSkinHelper.getSkinTextures(uuid));
    }
}
