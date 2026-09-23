package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.List;
import java.util.UUID;

public class TokenTurnPartyStep extends PartyStep {
    private UUID tokenUUID;
    private UUID owner;
    private UUID cancelTaskId = null;

    public TokenTurnPartyStep(NbtCompound nbt) {
        super(nbt);
        fromNbt(nbt);
    }

    public TokenTurnPartyStep(UUID token, UUID owner) {
        super();
        this.tokenUUID = token;
        this.owner = owner;
        setType(PartyStepType.TOKEN_TURN);
    }

    @Override
    public void start(PartyControllerEntity partyControllerEntity) {
        super.start(partyControllerEntity);
        if (partyControllerEntity.getWorld() instanceof ServerWorld serverWorld) {
            List<PlayerEntity> players = partyControllerEntity.getPartyData().getOwners(serverWorld);
            if (this.tokenUUID == null) {
                cancelTurn(players, partyControllerEntity);
                return;
            }
            Entity token = serverWorld.getEntity(tokenUUID);
            if (!(token instanceof TokenizedEntityInterface tokenInterface)) {
                cancelTurn(players, partyControllerEntity);
                return;
            }
            int status = tokenInterface.steveparty$getStatus();
            tokenInterface.steveparty$setStatus(TokenStatus.setStatus(status, TokenStatus.CAN_MOVE));
        }
    }

    @Override
    public void resume(PartyControllerEntity partyControllerEntity) {
        // The token status is saved with the entity, re-grant it anyway in case it was lost.
        // Never cancel the turn here: the token may simply not be loaded yet.
        if (tokenUUID != null && partyControllerEntity.getWorld() instanceof ServerWorld serverWorld
                && serverWorld.getEntity(tokenUUID) instanceof TokenizedEntityInterface tokenInterface) {
            tokenInterface.steveparty$setStatus(TokenStatus.setStatus(tokenInterface.steveparty$getStatus(), TokenStatus.CAN_MOVE));
        }
    }

    @Override
    public void end(PartyControllerEntity partyControllerEntity) {
        super.end(partyControllerEntity);
        if (cancelTaskId != null) {
            Steveparty.SCHEDULER.cancel(cancelTaskId);
            cancelTaskId = null;
        }
        if (partyControllerEntity.getWorld() instanceof ServerWorld serverWorld) {
            if (this.tokenUUID == null) {
                return;
            }
            Entity token = serverWorld.getEntity(tokenUUID);
            if (!(token instanceof TokenizedEntityInterface tokenInterface)) {
                return;
            }
            int status = tokenInterface.steveparty$getStatus();
            tokenInterface.steveparty$setStatus(TokenStatus.clearStatus(status, TokenStatus.CAN_MOVE));
        }
    }

    private void cancelTurn(List<PlayerEntity> players, PartyControllerEntity partyControllerEntity) {
        MessageUtils.sendToPlayers(players, Text.translatable("message.steveparty.cancel_turn_no_token", String.valueOf(tokenUUID))
                .withColor(Color.RED.hashCode()), MessageUtils.MessageType.CHAT);
        // Skip to the next step one tick later (not re-entrantly), unless something else moved the party meanwhile
        cancelTaskId = UUID.randomUUID();
        Steveparty.SCHEDULER.schedule(cancelTaskId, 1, () -> {
            cancelTaskId = null;
            if (isStillActive(partyControllerEntity))
                partyControllerEntity.nextStep();
        });
    }

    @Override
    public void fromNbt(NbtCompound nbt) {
        super.fromNbt(nbt);
        if (nbt.contains("Token")) {
            this.tokenUUID = UUID.fromString(nbt.getString("Token"));
        }
        if (nbt.contains("Owner")) {
            this.owner = UUID.fromString(nbt.getString("Owner"));
        }
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbtCompound = super.toNbt();
        if (tokenUUID != null)
            nbtCompound.putString("Token", tokenUUID.toString());
        if (owner != null)
            nbtCompound.putString("Owner", owner.toString());
        return nbtCompound;
    }

    public UUID getTokenUUID() {
        return tokenUUID;
    }

    public UUID getOwnerUUID() {
        return owner;
    }
}
