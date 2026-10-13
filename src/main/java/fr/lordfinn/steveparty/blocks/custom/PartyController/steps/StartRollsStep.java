package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.dice.OneThrowPerRoll;
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
    /** <player UUID, the first die of their throw> while it rolls. Not saved (no initializer: see rolls). */
    private Map<UUID, DiceEntity> thrown;

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
        thrown().clear();
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
            thrown().remove(ownerUUID);
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
                partyControllerEntity.ringMoment(PartyMoment.DICE_ROLLED, rollValue);
                // The party HUD shows each roll as it comes
                partyControllerEntity.sendPacketToInterestedPlayers();
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

    /**
     * One throw per roll: a player's throw waits for the result of the one on its way, and once each of their tokens
     * has its roll they throw no more (a Step Controller's replay asks everyone again).
     */
    @Override
    public boolean refusesThrow(UUID player, PartyControllerEntity controller) {
        if (status != Status.IN_PROGRESS || rolls == null) return false;
        if (OneThrowPerRoll.inFlight(thrown().get(player))) return true;
        if (!(controller.getWorld() instanceof ServerWorld world)) return false;
        boolean owns = false;
        for (Map.Entry<TokenizedEntityInterface, PlayerEntity> entry : controller.getPartyData().getTokensWithOwners(world).entrySet()) {
            if (entry.getValue() == null || !player.equals(entry.getValue().getUuid())) continue;
            owns = true;
            if (!rolls.containsKey(((Entity) entry.getKey()).getUuid())) return false;
        }
        return owns;
    }

    @Override
    public void onDieThrown(UUID player, DiceEntity lead) {
        if (status == Status.IN_PROGRESS) thrown().put(player, lead);
    }

    private Map<UUID, DiceEntity> thrown() {
        if (thrown == null) thrown = new HashMap<>();
        return thrown;
    }

    /** Each player rolls for the turn order: their token waits until it has its roll. */
    @Override
    public boolean waitsForChoiceOf(UUID token) {
        return status == Status.IN_PROGRESS && !rolls.containsKey(token);
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

    /**
     * A Step Controller passes: the turn order is made with the rolls already in (who has not rolled plays last), and
     * the party goes on.
     */
    public boolean pass(PartyControllerEntity partyControllerEntity) {
        if (!isStillActive(partyControllerEntity)) return false;
        sortTokensByRoll(partyControllerEntity);
        partyControllerEntity.nextStep();
        return true;
    }

    /** A Step Controller replays: the rolls are forgotten, everyone rolls again. */
    public boolean rollAgain(PartyControllerEntity partyControllerEntity) {
        if (!isStillActive(partyControllerEntity) || !(partyControllerEntity.getWorld() instanceof ServerWorld world)) return false;
        rolls.clear();
        partyControllerEntity.markDirty();
        MessageUtils.sendToPlayers(partyControllerEntity.getPartyData().getOwners(world).stream().map(e -> (ServerPlayerEntity) e).toList(),
                Text.translatable("message.steveparty.start_rolls"), MessageUtils.MessageType.CHAT);
        partyControllerEntity.sendPacketToInterestedPlayers();
        return true;
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
