package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.payloads.custom.PartyDataPayload;
import fr.lordfinn.steveparty.payloads.custom.PartyLivePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who follows a party and what they are sent: the interested players (saved), the party's steps
 * ({@link PartyDataPayload}) and its live state ({@link PartyLiveData}, sent again only when it changed).
 */
public final class PartyAudience {
    private final PartyControllerEntity controller;
    private final Set<UUID> interestedPlayers = new HashSet<>();
    /** The live state last sent to the interested players (only a change is sent). */
    private PartyLiveData lastLiveData = PartyLiveData.EMPTY;

    PartyAudience(PartyControllerEntity controller) {
        this.controller = controller;
    }

    void writeNbt(NbtCompound nbt) {
        NbtCompound playersNbt = new NbtCompound();
        int i = 0;
        for (UUID playerUUID : interestedPlayers) {
            playersNbt.putString("player_" + i, playerUUID.toString());
            i++;
        }
        nbt.put("interestedPlayers", playersNbt);
    }

    void readNbt(NbtCompound nbt) {
        interestedPlayers.clear();
        NbtCompound playersNbt = nbt.getCompound("interestedPlayers");
        for (String key : playersNbt.getKeys()) {
            interestedPlayers.add(UUID.fromString(playersNbt.getString(key)));
        }
    }

    /** The interested players (the set itself). */
    Set<UUID> ids() {
        return interestedPlayers;
    }

    PartyLiveData lastLiveData() {
        return lastLiveData;
    }

    /** No party any more: nothing live was sent. */
    void forgetLiveData() {
        lastLiveData = PartyLiveData.EMPTY;
    }

    void add(ServerPlayerEntity player) {
        boolean added = interestedPlayers.add(player.getUuid());
        memberships().ifPresent(memberships -> memberships.follow(player.getUuid(), here()));
        sendTo(player);
        controller.markDirty();
        if (added && controller.getPartyData().isStarted()) PartyLeave.tellHowToLeave(player, controller);
    }

    void remove(ServerPlayerEntity player) {
        forget(player.getUuid());
    }

    /**
     * {@code playerUUID} stops following the party: his HUD is cleared if he is connected (in any dimension).
     *
     * @return false if he did not follow it
     */
    boolean forget(UUID playerUUID) {
        memberships().ifPresent(memberships -> memberships.unfollow(playerUUID, here()));
        if (!interestedPlayers.remove(playerUUID)) return false;
        ServerPlayerEntity player = controller.getWorld() != null && controller.getWorld().getServer() != null
                ? controller.getWorld().getServer().getPlayerManager().getPlayer(playerUUID) : null;
        if (player != null) sendClear(player);
        controller.markDirty();
        return true;
    }

    /** Where the controller is (dimension and position). */
    GlobalPos here() {
        return GlobalPos.create(controller.getWorld() != null ? controller.getWorld().getRegistryKey() : World.OVERWORLD, controller.getPos());
    }

    private Optional<PartyMemberships> memberships() {
        return controller.getWorld() != null && controller.getWorld().getServer() != null
                ? Optional.of(PartyMemberships.get(controller.getWorld().getServer())) : Optional.empty();
    }

    /** Everyone stops following the party: their steps HUD is cleared, in any dimension. */
    void clear() {
        if (controller.getWorld() == null) return;
        for (UUID playerUUID : interestedPlayers) {
            // In any dimension (a player still on a mini-game's arena)
            ServerPlayerEntity player = controller.getWorld().getServer() != null
                    ? controller.getWorld().getServer().getPlayerManager().getPlayer(playerUUID) : null;
            if (player != null) sendClear(player);
        }
        memberships().ifPresent(memberships -> interestedPlayers.forEach(uuid -> memberships.unfollow(uuid, here())));
        interestedPlayers.clear();
        controller.markDirty();
    }

    /** The controller is gone: the connected interested players (in any dimension) get an empty party. */
    void clearHuds(ServerWorld serverWorld) {
        for (UUID playerUUID : interestedPlayers) {
            ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(playerUUID);
            if (player != null) sendClear(player);
        }
        memberships().ifPresent(memberships -> interestedPlayers.forEach(uuid -> memberships.unfollow(uuid, here())));
    }

    /** The owners of the party's loaded tokens follow it. */
    void addFromTokens(ServerWorld serverWorld) {
        for (UUID tokenUUID : controller.getPartyData().getTokens()) {
            if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                UUID ownerUUID = token.steveparty$getTokenOwner();
                if (serverWorld.getEntity(ownerUUID) instanceof ServerPlayerEntity player) add(player);
            }
        }
    }

    /** @return true if the player follows this running party: he gets its HUD and the way to leave it */
    boolean onPlayerJoin(ServerPlayerEntity player) {
        UUID playerUUID = player.getUuid();
        if (!interestedPlayers.contains(playerUUID)) {
            // A player of the party who was away when it started follows it from now on
            PartyData data = controller.getPartyData();
            if (!data.isStarted() || data.getTokens().stream().noneMatch(token -> controller.isTokenOwnedBy(token, playerUUID)))
                return false;
            add(player);
            return true;
        }
        sendTo(player);
        if (!controller.getPartyData().isStarted()) return false;
        PartyLeave.tellHowToLeave(player, controller);
        return true;
    }

    /** The connected interested players, in the controller's dimension. */
    List<ServerPlayerEntity> entities() {
        List<ServerPlayerEntity> players = new ArrayList<>();
        if (controller.getWorld() instanceof ServerWorld serverWorld) {
            for (UUID playerUUID : interestedPlayers) {
                PlayerEntity player = serverWorld.getPlayerByUuid(playerUUID);
                if (player instanceof ServerPlayerEntity serverPlayer) players.add(serverPlayer);
            }
        }
        return players;
    }

    /** The connected interested players and the players near the controller. */
    List<ServerPlayerEntity> partyAudience() {
        List<ServerPlayerEntity> audience = new ArrayList<>();
        if (controller.getWorld() instanceof ServerWorld serverWorld) {
            for (UUID playerUUID : interestedPlayers) {
                ServerPlayerEntity player = serverWorld.getServer().getPlayerManager().getPlayer(playerUUID);
                if (player != null) audience.add(player);
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                if (!audience.contains(player)
                        && player.getPos().isInRange(controller.getPos().toCenterPos(), PartyControllerEntity.PARTY_AUDIENCE_RADIUS))
                    audience.add(player);
            }
        }
        return audience;
    }

    /** The steps changed: they go to every interested player, with the live state up to date. */
    void sendToAll() {
        if (controller.getWorld() instanceof ServerWorld serverWorld) {
            PartyData partyData = controller.getPartyData();
            lastLiveData = partyData.isStarted() ? PartyLiveData.capture(controller, serverWorld) : PartyLiveData.EMPTY;
            for (UUID playerUUID : interestedPlayers) {
                PlayerEntity player = serverWorld.getPlayerByUuid(playerUUID);
                if (player instanceof ServerPlayerEntity serverPlayer) sendTo(serverPlayer);
            }
        }
    }

    void sendTo(ServerPlayerEntity player) {
        PartyData partyData = controller.getPartyData();
        send(player, partyData);
        if (partyData.isStarted() && controller.getWorld() instanceof ServerWorld serverWorld) {
            if (lastLiveData == PartyLiveData.EMPTY) lastLiveData = PartyLiveData.capture(controller, serverWorld);
            ServerPlayNetworking.send(player, new PartyLivePayload(lastLiveData));
        }
    }

    /** Sends the live state to the connected interested players when it changed. */
    void syncLiveData(ServerWorld serverWorld) {
        if (!controller.getPartyData().isStarted() || interestedPlayers.isEmpty()) {
            lastLiveData = PartyLiveData.EMPTY;
            return;
        }
        PartyLiveData live = PartyLiveData.capture(controller, serverWorld);
        if (live.sameAs(lastLiveData)) return;
        lastLiveData = live;
        PartyLivePayload payload = new PartyLivePayload(live);
        for (ServerPlayerEntity player : entities())
            ServerPlayNetworking.send(player, payload);
    }

    void sendClear(ServerPlayerEntity player) {
        send(player, new PartyData());
    }

    static void send(ServerPlayerEntity player, PartyData partyData) {
        ServerPlayNetworking.send(player, PartyDataPayload.fromPartyData(partyData));
    }
}
