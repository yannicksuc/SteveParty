package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.*;
import java.util.stream.Collectors;

public class StartRollsStep extends PartyStep {
    public Map<UUID, Integer> rolls = new HashMap<>(); // <UUID, Integer>

    public StartRollsStep(NbtCompound nbt) {
        super(nbt);
    }

    public StartRollsStep() {
        super();
        setType(PartyStepType.START_ROLLS);
    }

    @Override
    public void start(PartyControllerEntity partyControllerEntity) {
        super.start(partyControllerEntity);

        if (partyControllerEntity.getPartyData().getTokens().isEmpty()) {
            partyControllerEntity.nextStep();
            return;
        }

        ServerWorld world = (ServerWorld) partyControllerEntity.getWorld();
        if (world == null) return;
        PartyData partyData = partyControllerEntity.getPartyData();
        MessageUtils.sendToPlayers(partyData.getOwners(world).stream().map(e->(ServerPlayerEntity)e).toList(),
                Text.translatable("message.steveparty.start_rolls"),
                MessageUtils.MessageType.CHAT);
    }

    @Override
    public ActionResult onDiceRoll(DiceEntity dice, UUID ownerUUID, int rollValue, PartyControllerEntity partyControllerEntity) {
        if (dice.getWorld().isClient || partyControllerEntity == null || partyControllerEntity.getWorld() == null || partyControllerEntity.getWorld().isClient) return ActionResult.PASS;
        if (status != Status.IN_PROGRESS)
            return super.onDiceRoll(dice, ownerUUID, rollValue, partyControllerEntity);
        Steveparty.LOGGER.info("onDiceRoll");

        ServerWorld world = (ServerWorld) partyControllerEntity.getWorld();
        if (world == null) return ActionResult.PASS;
        Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners = partyControllerEntity.getPartyData().getTokensWithOwners(world);
        PlayerEntity player = world.getPlayerByUuid(ownerUUID);
        if (player != null && tokensWithOwners.containsValue(player)) {
            TokenizedEntityInterface diceTarget = null;
            if (dice.getTarget().isPresent()) {
                UUID diceTargetUUID = dice.getTarget().get();
                diceTarget = tokensWithOwners.keySet().stream()
                        .filter(token -> ((Entity)token).getUuid().equals(diceTargetUUID) && isOwnerValid(ownerUUID, token, tokensWithOwners))
                        .findFirst().orElse(null);
            }
            if (diceTarget == null) {
                diceTarget = tokensWithOwners.keySet().stream()
                        .filter(token -> !rolls.containsKey(((Entity)token).getUuid()) && isOwnerValid(ownerUUID, token, tokensWithOwners))
                        .findFirst().orElse(null);
            }
            if (diceTarget != null) {
                rolls.put(((Entity) diceTarget).getUuid(), rollValue);
            }

            if (rolls.size() == tokensWithOwners.size()) {
                sortTokensByRoll(partyControllerEntity);
                partyControllerEntity.nextStep();
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    private static boolean isOwnerValid(UUID ownerUUID, TokenizedEntityInterface token, Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners) {
        return ownerUUID.equals(tokensWithOwners.get(token).getUuid());
    }

    private void sortTokensByRoll(PartyControllerEntity partyControllerEntity) {
        ServerWorld world = (ServerWorld) partyControllerEntity.getWorld();
        if (world == null) return;
        List<TokenizedEntityInterface> tokens = partyControllerEntity.getPartyData().getTokens(world);
        tokens.sort((p1, p2) -> rolls.get(((Entity)p2).getUuid()).compareTo(rolls.get(((Entity)p1).getUuid())));
        partyControllerEntity.getPartyData().setTokens(tokens.stream().map(p -> ((Entity)p).getUuid()).collect(Collectors.toCollection(ArrayList::new)));
    }
}
