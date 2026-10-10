package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The end of a party: its tokens are taken out of the game and go back onto their start tiles. A step controller can
 * still bring the party back to its previous step: the tokens then go back where they stood.
 */
public class EndPartyStep extends PartyStep {
    // No initializer: it would run after super(nbt) and wipe what fromNbt just read
    List<UUID> tokens;
    /** The board space each token stood on when the party ended, before it went back to its start tile. */
    Map<UUID, BlockPos> leftFrom;

    public EndPartyStep(List<UUID> tokens) {
        if (tokens == null)
            tokens = new ArrayList<>();
        this.tokens = tokens;
        this.leftFrom = new LinkedHashMap<>();
        setType(PartyStepType.END);
    }

    public EndPartyStep(NbtCompound nbt) {
        super(nbt);
        if (this.tokens == null)
            this.tokens = new ArrayList<>();
        if (this.leftFrom == null)
            this.leftFrom = new LinkedHashMap<>();
    }

    @Override
    public void start(PartyControllerEntity partyControllerEntity) {
        super.start(partyControllerEntity);
        if (!(partyControllerEntity.getWorld() instanceof ServerWorld serverWorld)) return;

        // Release every token of the party so they are no longer considered in game
        // (the tokens that are not loaded are released as soon as they are loaded again)
        Set<UUID> allTokens = new LinkedHashSet<>(partyControllerEntity.getPartyData().getTokens());
        allTokens.addAll(tokens);
        for (UUID tokenUUID : allTokens) {
            partyControllerEntity.releaseToken(serverWorld, tokenUUID);
        }
        // Each token back onto its start tile (a start resumed after a load keeps where they stood before the end)
        partyControllerEntity.sendTokensHome(serverWorld, allTokens).forEach(leftFrom::putIfAbsent);

        MessageUtils.sendToNearby(
                serverWorld,
                partyControllerEntity.getPos().toCenterPos(), 100,
                Text.translatableWithFallback("message.steveparty.game_ended", "The party is over !"),
                MessageUtils.MessageType.CHAT);
        partyControllerEntity.markDirty();
        fr.lordfinn.steveparty.api.event.PartyEvents.ENDED.invoker().onPartyEnded(partyControllerEntity);
    }

    /** The party is brought back from its end: its tokens go back where they stood when it ended. */
    public void sendTokensBack(PartyControllerEntity partyControllerEntity) {
        if (partyControllerEntity.getWorld() instanceof ServerWorld serverWorld)
            partyControllerEntity.sendTokensBack(serverWorld, leftFrom);
        leftFrom.clear();
    }

    @Override
    public void onTokenExcluded(UUID tokenUUID, PartyControllerEntity partyControllerEntity) {
        tokens.remove(tokenUUID);
        leftFrom.remove(tokenUUID);
    }

    @Override
    public void fromNbt(NbtCompound nbt) {
        super.fromNbt(nbt);
        if (nbt.contains("Tokens")) {
            if (tokens == null)
                tokens = new ArrayList<>();
            nbt.getList("Tokens", 8).forEach(token -> {
                String uuidStr = token.asString();
                UUID uuid = UUID.fromString(uuidStr);
                this.tokens.add(uuid);
            });
        }
        if (leftFrom == null)
            leftFrom = new LinkedHashMap<>();
        NbtCompound leftNbt = nbt.getCompound("LeftFrom");
        for (String key : leftNbt.getKeys()) {
            try {
                leftFrom.put(UUID.fromString(key), BlockPos.fromLong(leftNbt.getLong(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbtCompound = super.toNbt();
        NbtList tokensNbtList = new NbtList();
        for (UUID uuid : tokens) {
            tokensNbtList.add(NbtString.of(uuid.toString()));
        }
        if (!tokens.isEmpty())
            nbtCompound.put("Tokens", tokensNbtList);
        if (!leftFrom.isEmpty()) {
            NbtCompound leftNbt = new NbtCompound();
            leftFrom.forEach((token, space) -> leftNbt.putLong(token.toString(), space.asLong()));
            nbtCompound.put("LeftFrom", leftNbt);
        }
        return nbtCompound;
    }
}
