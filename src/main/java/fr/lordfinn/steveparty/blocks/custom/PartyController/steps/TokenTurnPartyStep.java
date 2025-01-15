package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

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
    UUID tokenUUID = null;

    public TokenTurnPartyStep(UUID token) {
        super();
        this.tokenUUID = token;
        setType(PartyStepType.TOKEN_TURN);
    }

    public TokenTurnPartyStep(NbtCompound nbt) {

        super(nbt);
    }

    @Override
    public void start(PartyControllerEntity partyControllerEntity) {
        super.start(partyControllerEntity);
        if (partyControllerEntity.getWorld() instanceof ServerWorld serverWorld) {
            List<PlayerEntity> players = partyControllerEntity.getPartyData().getOwners(serverWorld);
            if (this.tokenUUID == null) {
                cancelTurn(players);
                return;
            }
            Entity token = serverWorld.getEntity(tokenUUID);
            if (!(token instanceof TokenizedEntityInterface tokenInterface)) {
                cancelTurn(players);
                return;
            }
            int status = tokenInterface.steveparty$getStatus();
            tokenInterface.steveparty$setStatus(TokenStatus.setStatus(status, TokenStatus.CAN_MOVE));
        }
    }

    @Override
    public void end(PartyControllerEntity partyControllerEntity) {
        super.end(partyControllerEntity);
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

    private void cancelTurn(List<PlayerEntity> players) {
        MessageUtils.sendToPlayers(players, Text.translatable("message.steveparty.cancel_turn_no_token", tokenUUID)
                .withColor(Color.RED.hashCode()), MessageUtils.MessageType.CHAT);
    }

    @Override
    public void fromNbt(NbtCompound nbt) {
        super.fromNbt(nbt);
        if (nbt.contains("Token")) {
            this.tokenUUID = UUID.fromString(nbt.getString("Token"));
        }
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbtCompound = super.toNbt();
        if (tokenUUID != null)
            nbtCompound.putString("Token", tokenUUID.toString());
        return nbtCompound;
    }
}
