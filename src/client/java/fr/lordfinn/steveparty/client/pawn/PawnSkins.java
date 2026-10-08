package fr.lordfinn.steveparty.client.pawn;

import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Skin of a pawn's statue: its player's, from the player list while they are online, else looked up once by UUID
 * (like a player head); the default skin of that UUID meanwhile, or when there is none.
 */
public final class PawnSkins {
    private static final Map<UUID, Supplier<SkinTextures>> LOOKED_UP = new HashMap<>();

    private PawnSkins() {
    }

    public static SkinTextures of(PlayerPawnEntity pawn) {
        UUID id = pawn.getSkinOwner();
        if (id == null) return DefaultSkinHelper.getSkinTextures(net.minecraft.util.Util.NIL_UUID);
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler network = client.getNetworkHandler();
        PlayerListEntry entry = network == null ? null : network.getPlayerListEntry(id);
        if (entry != null) return entry.getSkinTextures();
        return LOOKED_UP.computeIfAbsent(id, PawnSkins::lookUp).get();
    }

    private static Supplier<SkinTextures> lookUp(UUID id) {
        SkinTextures fallback = DefaultSkinHelper.getSkinTextures(id);
        Found found = new Found();
        SkullBlockEntity.fetchProfileByUuid(id).thenAccept(profile -> profile.ifPresent(gameProfile ->
                MinecraftClient.getInstance().execute(() ->
                        found.skin = MinecraftClient.getInstance().getSkinProvider().getSkinTexturesSupplier(gameProfile))));
        return () -> found.skin != null ? found.skin.get() : fallback;
    }

    private static final class Found {
        volatile Supplier<SkinTextures> skin;
    }

    /** Forgets the looked up skins (leaving the world). */
    public static void clear() {
        LOOKED_UP.clear();
    }
}
