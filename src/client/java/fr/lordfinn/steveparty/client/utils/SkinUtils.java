package fr.lordfinn.steveparty.client.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
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
    public static SkinTextures getSkinTextures(UUID uuid) {
        return getSkinTextures(uuid, "", "", "");
    }

    /**
     * Same, for a player whose skin was kept ({@code textures}: the "textures" property of their profile, "" if
     * unknown): while they are offline that skin is worn, with no lookup that may fail (and show a default skin).
     */
    public static SkinTextures getSkinTextures(UUID uuid, String name, String textures, String signature) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler != null) {
            PlayerListEntry playerListEntry = networkHandler.getPlayerListEntry(uuid);
            if (playerListEntry != null) return playerListEntry.getSkinTextures();
        }
        if (!textures.isEmpty()) {
            GameProfile kept = new GameProfile(uuid, name.isEmpty() ? "Player" : name);
            kept.getProperties().put("textures", new Property("textures", textures, signature.isEmpty() ? null : signature));
            return client.getSkinProvider().getSkinTextures(kept);
        }
        Optional<GameProfile> profile = SkullBlockEntity.fetchProfileByUuid(uuid).getNow(Optional.empty());
        return profile.map(p -> client.getSkinProvider().getSkinTextures(p))
                .orElseGet(() -> DefaultSkinHelper.getSkinTextures(uuid));
    }
}
