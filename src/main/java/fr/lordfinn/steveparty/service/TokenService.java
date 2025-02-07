package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.custom.TokenPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TokenService extends PersistentState {
    private static final String OWNER_LIST_KEY = "tokenOwnersList";
    private final Map<UUID, TokenData> tokenOwners = new HashMap<>();
    public static final Identifier TOKEN_UPDATE_PACKET = Steveparty.id("token_update");

    public void setOwner(UUID entityId, UUID ownerUUID) {
        if (this.tokenOwners.containsKey(entityId)) {
            this.tokenOwners.get(entityId).setOwnerUuid(ownerUUID);
        } else {
            this.tokenOwners.put(entityId, new TokenData(ownerUUID, 0, 0));
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbtList = new NbtCompound();
        tokenOwners.forEach((tokenId, tokenData) -> nbtList.put(tokenId.toString(), tokenData.toNbt(new NbtCompound())));
        nbt.put(OWNER_LIST_KEY, nbtList);
        return null;
    }

    private static TokenService createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        TokenService state = new TokenService();
        NbtCompound nbtList = (NbtCompound) nbt.get(OWNER_LIST_KEY);
        if (nbtList != null) {
            nbtList.getKeys().forEach(key -> state.tokenOwners.put(UUID.fromString(key), new TokenData(nbt.getCompound(key))));
        }
        return state;
    }

    private static final Type<TokenService> type = new Type<>(
            TokenService::new, // If there's no 'TokenService' yet create one
            TokenService::createFromNbt, // If there is a 'TokenService' NBT, parse it with 'createFromNbt'
            null // Supposed to be an 'DataFixTypes' enum, but we can just pass null
    );

    public static TokenService get(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        TokenService state = getTokenService(world);
        if (state != null) {
            state.markDirty();
        }
        return state;
    }

    private static @Nullable TokenService getTokenService(ServerWorld world) {
        PersistentStateManager persistentStateManager = null;
        if (world != null) {
            persistentStateManager = world.getPersistentStateManager();
        }
        TokenService state = null;
        if (persistentStateManager != null) {
            state = persistentStateManager.getOrCreate(type, Steveparty.MOD_ID);
        }
        return state;
    }

    public void sendUpdate(List<ServerPlayerEntity> players, MinecraftServer server, @Nullable UUID tokenId) {
        TokenService service = TokenService.get(server);
        if (service == null) return;
        Map<UUID, TokenData> tokenOwnersToSend = service.tokenOwners;
        if (tokenId != null) {
            tokenOwnersToSend = new HashMap<>();
            tokenOwnersToSend.put(tokenId, service.tokenOwners.get(tokenId));
        }
        TokenPayload tokenPayload = new TokenPayload(tokenOwnersToSend);
        players.forEach(player -> ServerPlayNetworking.send(player, tokenPayload));
    }
}
