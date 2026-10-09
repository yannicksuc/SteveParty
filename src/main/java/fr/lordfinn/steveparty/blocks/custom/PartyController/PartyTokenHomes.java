package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.DiceRollEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Where the tokens of a party come from and go back to: the start tile each token started from, the tokens to take
 * out of the game and the tokens of a stopped party to send back to their start tile, both as soon as they are
 * loaded when they were not. Saved with the controller.
 */
public final class PartyTokenHomes {
    /** Tokens that left the party (excluded / party over) while not loaded: released as soon as they are loaded. */
    private final Set<UUID> tokensToRelease = new LinkedHashSet<>();
    /** The start tile each token of the running party started from (where a stopped party sends it back). */
    private final Map<UUID, BlockPos> startTiles = new LinkedHashMap<>();
    /** Tokens of a stopped party that were not loaded: sent back to their start tile as soon as they are loaded. */
    private final Map<UUID, BlockPos> tokensToSendHome = new LinkedHashMap<>();

    void writeNbt(NbtCompound nbt) {
        if (!tokensToRelease.isEmpty()) {
            NbtList releaseNbt = new NbtList();
            tokensToRelease.forEach(uuid -> releaseNbt.add(NbtString.of(uuid.toString())));
            nbt.put("TokensToRelease", releaseNbt);
        }
        if (!startTiles.isEmpty()) nbt.put("StartTiles", writeTokenTiles(startTiles));
        if (!tokensToSendHome.isEmpty()) nbt.put("TokensToSendHome", writeTokenTiles(tokensToSendHome));
    }

    void readNbt(NbtCompound nbt) {
        tokensToRelease.clear();
        nbt.getList("TokensToRelease", NbtElement.STRING_TYPE).forEach(element -> {
            try {
                tokensToRelease.add(UUID.fromString(element.asString()));
            } catch (IllegalArgumentException ignored) {
            }
        });
        readTokenTiles(nbt.getCompound("StartTiles"), startTiles);
        readTokenTiles(nbt.getCompound("TokensToSendHome"), tokensToSendHome);
    }

    /** Tokens and board spaces: the UUIDs as keys, the positions as longs. */
    private static NbtCompound writeTokenTiles(Map<UUID, BlockPos> tiles) {
        NbtCompound nbt = new NbtCompound();
        tiles.forEach((token, tile) -> nbt.putLong(token.toString(), tile.asLong()));
        return nbt;
    }

    private static void readTokenTiles(NbtCompound nbt, Map<UUID, BlockPos> tiles) {
        tiles.clear();
        for (String key : nbt.getKeys()) {
            try {
                tiles.put(UUID.fromString(key), BlockPos.fromLong(nbt.getLong(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /** Something waits for its token to be loaded. */
    boolean hasPending() {
        return !tokensToRelease.isEmpty() || !tokensToSendHome.isEmpty();
    }

    @Nullable BlockPos startTile(UUID token) {
        return startTiles.get(token);
    }

    /** A party starts from these tiles: its tokens are no longer sent home. */
    void startFrom(Map<UUID, BlockPos> tiles) {
        startTiles.clear();
        startTiles.putAll(tiles);
        tiles.keySet().forEach(tokensToSendHome::remove);
    }

    /** The start tiles of the running party (a copy). */
    Map<UUID, BlockPos> startTiles() {
        return new LinkedHashMap<>(startTiles);
    }

    void forgetStartTiles() {
        startTiles.clear();
    }

    /**
     * Takes a token out of the game (clears IN_GAME / CAN_MOVE). A token that is not loaded is released as soon as it
     * is loaded again, unless it joined a running party meanwhile.
     */
    void release(ServerWorld serverWorld, UUID tokenUUID) {
        if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
            token.steveparty$setStatus(TokenStatus.clearStatuses(token.steveparty$getStatus(), TokenStatus.IN_GAME, TokenStatus.CAN_MOVE));
            tokensToRelease.remove(tokenUUID);
        } else {
            tokensToRelease.add(tokenUUID);
        }
    }

    /** Back into the game (a party leaving its end): its tokens are in game again. */
    void restoreInGame(ServerWorld serverWorld, Collection<UUID> tokens) {
        for (UUID tokenUUID : tokens) {
            tokensToRelease.remove(tokenUUID);
            if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token)
                token.steveparty$setStatus(TokenStatus.setStatus(token.steveparty$getStatus(), TokenStatus.IN_GAME));
        }
    }

    /** A token of a stopped party: sent back to {@code home} now if loaded, else as soon as it is. */
    void sendHome(ServerWorld serverWorld, UUID tokenUUID, BlockPos home) {
        if (serverWorld.getEntity(tokenUUID) instanceof MobEntity mob) sendHome(serverWorld, mob, home);
        else tokensToSendHome.put(tokenUUID, home);
    }

    /**
     * The tokens loaded since are released or sent home (unless they play again).
     *
     * @return true if something changed
     */
    boolean processPending(ServerWorld serverWorld) {
        boolean changed = false;
        Iterator<UUID> iterator = tokensToRelease.iterator();
        while (iterator.hasNext()) {
            UUID tokenUUID = iterator.next();
            if (PartyControllers.isTokenInRunningParty(tokenUUID)) {
                iterator.remove(); // it plays again: nothing to release
                changed = true;
            } else if (serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                token.steveparty$setStatus(TokenStatus.clearStatuses(token.steveparty$getStatus(), TokenStatus.IN_GAME, TokenStatus.CAN_MOVE));
                iterator.remove();
                changed = true;
            }
        }
        Iterator<Map.Entry<UUID, BlockPos>> homes = tokensToSendHome.entrySet().iterator();
        while (homes.hasNext()) {
            Map.Entry<UUID, BlockPos> home = homes.next();
            if (PartyControllers.isTokenInRunningParty(home.getKey())) {
                homes.remove(); // it plays again: it stays where it is
                changed = true;
            } else if (serverWorld.getEntity(home.getKey()) instanceof MobEntity mob) {
                sendHome(serverWorld, mob, home.getValue());
                homes.remove();
                changed = true;
            }
        }
        return changed;
    }

    /** Gives the dice thrown for a stopped party (for one of its tokens, or by one of its players) back to their owners. */
    static void dropDiceOf(ServerWorld serverWorld, BlockPos controller, Set<UUID> tokens, List<UUID> players) {
        Box around = new Box(controller).expand(PartyControllerEntity.START_TILES_SEARCH_RADIUS);
        for (DiceEntity dice : serverWorld.getEntitiesByClass(DiceEntity.class, around, dice ->
                dice.getTarget().filter(tokens::contains).isPresent() || dice.getOwner().filter(players::contains).isPresent())) {
            dice.kill();
        }
    }

    /**
     * Puts a token of a stopped party back on its start tile, standing still as at the beginning: out of a pipe or a
     * teleport, its move and the destinations it was choosing from forgotten.
     */
    private static void sendHome(ServerWorld serverWorld, MobEntity mob, BlockPos home) {
        if (mob.getVehicle() instanceof PipeCarrierEntity) mob.stopRiding();
        TileTeleport.cancel(mob);
        BoardSpaceBlockEntity space = BoardSpaces.boardSpaceOf(mob);
        if (space != null) space.hideDestinations();
        if (mob instanceof TokenizedEntityInterface token) {
            token.steveparty$setNbSteps(0);
            token.steveparty$stopMoving();
        }
        DiceRollEffects.clearMoveModules(mob.getUuid());
        Vec3d stand = BoardSpaces.standPos(serverWorld, home);
        mob.requestTeleport(stand.x, stand.y, stand.z);
        mob.setVelocity(Vec3d.ZERO);
        mob.fallDistance = 0;
        // Its path starts again there
        AdvanceBackMoves.forgetTrail(mob.getUuid());
        AdvanceBackMoves.noteAt(mob, home);
    }
}
