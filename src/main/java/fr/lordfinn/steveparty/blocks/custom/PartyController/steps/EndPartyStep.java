package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EndPartyStep extends PartyStep {
    // No initializer: it would run after super(nbt) and wipe what fromNbt just read
    List<UUID> tokens;

    public EndPartyStep(List<UUID> tokens) {
        if (tokens == null)
            tokens = new ArrayList<>();
        this.tokens = tokens;
        setType(PartyStepType.END);
    }

    public EndPartyStep(NbtCompound nbt) {
        super(nbt);
        if (this.tokens == null)
            this.tokens = new ArrayList<>();
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

        MessageUtils.sendToNearby(
                serverWorld,
                partyControllerEntity.getPos().toCenterPos(), 100,
                Text.translatableWithFallback("message.steveparty.game_ended", "The party is over !"),
                MessageUtils.MessageType.CHAT);
        partyControllerEntity.markDirty();
    }

    @Override
    public void onTokenExcluded(UUID tokenUUID, PartyControllerEntity partyControllerEntity) {
        tokens.remove(tokenUUID);
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
        return nbtCompound;
    }
}
