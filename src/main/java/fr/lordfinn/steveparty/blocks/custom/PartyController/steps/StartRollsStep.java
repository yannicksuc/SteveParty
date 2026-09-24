package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
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

public class StartRollsStep extends PartyStep {
    // <token UUID, roll>. No initializer: it would run after super(nbt) and wipe what fromNbt just read
    public Map<UUID, Integer> rolls;

    public StartRollsStep(NbtCompound nbt) {
        super(nbt);
        if (rolls == null)
            rolls = new HashMap<>();
    }

    public StartRollsStep() {
        super();
        rolls = new HashMap<>();
        setType(PartyStepType.START_ROLLS);
    }

    @Override
    public void start(PartyControllerEntity partyControllerEntity) {
        super.start(partyControllerEntity);
        // (Re)starting the step means everybody rolls again
        rolls.clear();
        partyControllerEntity.markDirty();

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
                partyControllerEntity.markDirty();
            }

            // Every token currently played by a connected owner has rolled
            if (tokensWithOwners.keySet().stream().allMatch(token -> rolls.containsKey(((Entity) token).getUuid()))) {
                sortTokensByRoll(partyControllerEntity);
                partyControllerEntity.nextStep();
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    @Override
    public void onTokenExcluded(UUID tokenUUID, PartyControllerEntity partyControllerEntity) {
        if (rolls.remove(tokenUUID) != null)
            partyControllerEntity.markDirty();
        // The excluded token may have been the last one this step was waiting for: check it one tick later
        if (status != Status.IN_PROGRESS || rolls.isEmpty() || !(partyControllerEntity.getWorld() instanceof ServerWorld world))
            return;
        Steveparty.SCHEDULER.schedule(UUID.randomUUID(), 1, () -> {
            if (!isStillActive(partyControllerEntity)) return;
            Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners = partyControllerEntity.getPartyData().getTokensWithOwners(world);
            if (tokensWithOwners.keySet().stream().allMatch(token -> rolls.containsKey(((Entity) token).getUuid()))) {
                sortTokensByRoll(partyControllerEntity);
                partyControllerEntity.nextStep();
            }
        });
    }

    private static boolean isOwnerValid(UUID ownerUUID, TokenizedEntityInterface token, Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners) {
        return ownerUUID.equals(tokensWithOwners.get(token).getUuid());
    }

    private void sortTokensByRoll(PartyControllerEntity partyControllerEntity) {
        // Sort the UUIDs themselves so tokens that are unloaded or whose owner is offline are kept.
        // Tokens without a roll go last; the sort is stable so ties keep their previous order.
        ArrayList<UUID> tokens = new ArrayList<>(partyControllerEntity.getPartyData().getTokens());
        tokens.sort(Comparator.comparingInt((UUID uuid) -> rolls.getOrDefault(uuid, Integer.MIN_VALUE)).reversed());
        partyControllerEntity.getPartyData().setTokens(tokens);
        partyControllerEntity.markDirty();
    }

    @Override
    public void fromNbt(NbtCompound nbt) {
        super.fromNbt(nbt);
        if (rolls == null)
            rolls = new HashMap<>();
        if (nbt.contains("Rolls")) {
            NbtCompound rollsNbt = nbt.getCompound("Rolls");
            for (String key : rollsNbt.getKeys()) {
                try {
                    rolls.put(UUID.fromString(key), rollsNbt.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbtCompound = super.toNbt();
        if (!rolls.isEmpty()) {
            NbtCompound rollsNbt = new NbtCompound();
            rolls.forEach((uuid, roll) -> rollsNbt.putInt(uuid.toString(), roll));
            nbtCompound.put("Rolls", rollsNbt);
        }
        return nbtCompound;
    }
}
