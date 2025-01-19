package fr.lordfinn.steveparty.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class SkinUtils {
    public static Identifier getPlayerSkin(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler == null) {
            return null; // Skin not found
        }
        PlayerListEntry playerListEntry = networkHandler.getPlayerListEntry(uuid);
        if (playerListEntry != null) {
            return playerListEntry.getSkinTextures().texture();
        }
        return null; // Skin not found
    }
}
