package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.powerups.effects.TrapState;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EventPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepFactory;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;

import fr.lordfinn.steveparty.service.ThresholdGates;
import fr.lordfinn.steveparty.service.TrichaudronPrizes;
import java.util.*;
import java.util.stream.Collectors;

public class PartyData {
    private PartyStepType type = PartyStepType.DEFAULT;
    private List<PartyStep> steps = new ArrayList<>();
    private List<UUID> tokens = new ArrayList<>();
    private int stepIndex = -1;
    private int nbTurn = 10;
    /**
     * Tokens shielded by a Padlock power-up until their next turn. Managed by
     * {@link fr.lordfinn.steveparty.powerups.effects.PowerUpProtection}: saved with the party, never sent to clients
     * (the HUD shows it through the standings' bonuses).
     */
    private final Set<UUID> protectedTokens = new LinkedHashSet<>();
    /** Tokens whose next turn is lost (a Trap): saved with the party, see TokenTurnPartyStep#start. */
    private final Set<UUID> skippedTokens = new LinkedHashSet<>();
    /** The hidden traps of the Trap power-up: saved with the party, never sent to the clients. */
    private final TrapState traps = new TrapState();
    /** Who cleared each Threshold barrier during this party: saved with the party, see ThresholdGates. */
    private final ThresholdGates.State thresholdGates = new ThresholdGates.State();
    /** The prizes won at each Trichaudron space during this party: saved with the party, see TrichaudronPrizes. */
    private final TrichaudronPrizes.Won trichaudronWon = new TrichaudronPrizes.Won();
    /** Bad spaces each player landed on during this party (advancements; not saved: counted again after a restart). */
    private final Map<UUID, Integer> badLandings = new HashMap<>();

    // Constructor
    public PartyData() {
    }

    public PartyData(NbtCompound compound) {
        fromNbt(compound);
    }

    public PartyData(PartyStepType type, List<PartyStep> partySteps, List<UUID> tokens, Integer integer, Integer integer1) {
        this.type = type;
        this.steps = partySteps;
        this.tokens = tokens;
        this.stepIndex = integer;
        this.nbTurn = integer1;
    }

    /**
     * Reads the party (its steps, tokens, step and number of turns) from an NBT tag.
     *
     * @param nbt the NBT data
     */
    public void fromNbt(NbtCompound nbt) {
        if (nbt.contains("Steps")) {
            // The factory never returns null (unknown steps become placeholders) so the step index stays aligned
            nbt.getList("Steps", 10).forEach(nbtStep -> this.steps.add(PartyStepFactory.get((NbtCompound) nbtStep)));
        }
        if (nbt.contains("Tokens")) {
            nbt.getList("Tokens", 8).forEach(token -> this.tokens.add(UUID.fromString(token.asString())));
        }
        if (nbt.contains("StepIndex")) {
            this.stepIndex = nbt.getInt("StepIndex");
        }
        if (nbt.contains("NbTurn")) {
            this.nbTurn = nbt.getInt("NbTurn");
        }
        protectedTokens.clear();
        nbt.getList("ProtectedTokens", 8).forEach(token -> {
            try {
                protectedTokens.add(UUID.fromString(token.asString()));
            } catch (IllegalArgumentException ignored) {
            }
        });
        traps.readNbt(nbt);
        thresholdGates.readNbt(nbt);
        trichaudronWon.readNbt(nbt);
        skippedTokens.clear();
        nbt.getList("SkippedTokens", 8).forEach(token -> {
            try {
                skippedTokens.add(UUID.fromString(token.asString()));
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    /**
     * Saves the party to an NBT tag.
     *
     * @param nbt the NBT tag to populate
     * @return the populated NBT tag
     */
    public NbtCompound toNbt(NbtCompound nbt) {
        NbtList stepNbtList = new NbtList();
        steps.forEach(step -> stepNbtList.add(step.toNbt()));
        nbt.put("Steps", stepNbtList);
        NbtList tokensNbtList = new NbtList();
        for (UUID uuid : tokens) {
            tokensNbtList.add(NbtString.of(uuid.toString()));
        }
        nbt.put("Tokens", tokensNbtList);
        nbt.putInt("StepIndex", stepIndex);
        nbt.putInt("NbTurn", nbTurn);
        if (!protectedTokens.isEmpty()) {
            NbtList protectedNbt = new NbtList();
            protectedTokens.forEach(uuid -> protectedNbt.add(NbtString.of(uuid.toString())));
            nbt.put("ProtectedTokens", protectedNbt);
        }
        traps.writeNbt(nbt);
        thresholdGates.writeNbt(nbt);
        trichaudronWon.writeNbt(nbt);
        if (!skippedTokens.isEmpty()) {
            NbtList skippedNbt = new NbtList();
            skippedTokens.forEach(uuid -> skippedNbt.add(NbtString.of(uuid.toString())));
            nbt.put("SkippedTokens", skippedNbt);
        }
        return nbt;
    }

    /**
     * The steps as NBT, made now: what the clients are sent. Never hand the live party to a packet, Netty encodes it
     * later on its own thread while the server thread is still adding steps.
     */
    public List<NbtCompound> stepsNbt() {
        List<NbtCompound> nbt = new ArrayList<>(steps.size());
        for (PartyStep step : steps) nbt.add(step.toNbt());
        return List.copyOf(nbt);
    }

    /** The client's copy of a party sent as {@link #stepsNbt()}, its tokens, step and number of turns. */
    public static PartyData received(List<NbtCompound> stepsNbt, List<UUID> tokens, int stepIndex, int nbTurn) {
        PartyData party = new PartyData();
        for (NbtCompound stepNbt : stepsNbt) party.steps.add(stepNbt != null ? PartyStepFactory.get(stepNbt) : new PartyStep());
        party.tokens.addAll(tokens);
        party.stepIndex = stepIndex;
        party.nbTurn = nbTurn;
        return party;
    }

    /**
     * A party is running while the step index points to a real step, except the END step:
     * once the END step is reached the party is over and the controller can be booted again.
     */
    public boolean isStarted() {
        return stepIndex > -1 && stepIndex < steps.size()
                && steps.get(stepIndex).getType() != PartyStepType.END;
    }

    /**
     * True when the party reached its END step: it is over (see {@link #isStarted()}), but a step controller
     * can still bring it back to the previous step.
     */
    public boolean isAtEnd() {
        return stepIndex > -1 && stepIndex < steps.size()
                && steps.get(stepIndex).getType() == PartyStepType.END;
    }

    public List<PartyStep> getSteps() {
        return steps;
    }

    public List<UUID> getTokens() {
        return tokens;
    }

    public List<TokenizedEntityInterface> getTokens(ServerWorld world) {
        return tokens.stream().map(world::getEntity).filter(Objects::nonNull)
                .map(TokenizedEntityInterface.class::cast)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public void setTokens(ArrayList<UUID> tokens) {
        this.tokens = tokens;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public void addToken(UUID uuid) {
        this.tokens.add(uuid);
    }

    public void addStep(PartyStep step) {
        this.steps.addLast(step);
    }

    public void removeToken(UUID uuid) {
        this.tokens.remove(uuid);
        this.protectedTokens.remove(uuid);
    }

    /**
     * The tokens currently shielded by a Padlock (live set). Read and change it through
     * {@link fr.lordfinn.steveparty.powerups.effects.PowerUpProtection} only.
     */
    public Set<UUID> getProtectedTokens() {
        return protectedTokens;
    }

    /** The tokens whose next turn is lost (live set). */
    public Set<UUID> getSkippedTokens() {
        return skippedTokens;
    }

    public void reset() {
        this.tokens.clear();
        this.protectedTokens.clear();
        this.skippedTokens.clear();
        this.stepIndex = -1;
        this.steps.clear();
        this.traps.clear();
        this.thresholdGates.reset();
        this.trichaudronWon.reset();
        this.badLandings.clear();
    }

    /** The hidden traps set with the Trap power-up during this party (see {@code TrapEffect}). */
    public TrapState getTraps() {
        return traps;
    }

    /** The Threshold barriers cleared during this party (see {@code ThresholdGates}). */
    public ThresholdGates.State getThresholdGates() {
        return thresholdGates;
    }

    /** The prizes won at the Trichaudron spaces during this party (see {@code TrichaudronPrizes}). */
    public TrichaudronPrizes.Won getTrichaudronWon() {
        return trichaudronWon;
    }

    /** {@code player} landed on a bad space once more. @return how many they landed on during this party */
    public int addBadLanding(UUID player) {
        return badLandings.merge(player, 1, Integer::sum);
    }

    public int getNbTurn() {
        return nbTurn;
    }

    public void setNbTurn(int nbTurn) {
        this.nbTurn = nbTurn;
    }

    public PartyStepType getType() {
        return type;
    }

    public void setType(PartyStepType type) {
        this.type = type;
    }

    public List<PlayerEntity> getOwners(ServerWorld world) {
        List<PlayerEntity> owners = new ArrayList<>();
        getTokens().forEach(tokenUUID -> {
            if (world.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                UUID ownerUUID = token.steveparty$getTokenOwner();
                if (world.getEntity(ownerUUID) instanceof PlayerEntity player) {
                    owners.add(player);
                }
            }
        });
        return owners;
    }

    /**
     * Round of the step at {@code index}: a round starts on each group of consecutive token turns (the transition
     * steps inserted to wait for a party bell do not split a group). 0 before the first round.
     */
    public int getRoundAt(int index) {
        int round = 0;
        PartyStep previous = null;
        for (int i = 0; i <= index && i < steps.size(); i++) {
            PartyStep step = steps.get(i);
            if (step instanceof EventPartyStep event && event.isTransition()) continue;
            if (step.getType() == PartyStepType.TOKEN_TURN && (previous == null || previous.getType() != PartyStepType.TOKEN_TURN))
                round++;
            previous = step;
        }
        return round;
    }

    /** @return true if a new round starts with the step at {@code index} (see {@link #getRoundAt}). */
    public boolean isRoundStart(int index) {
        if (index < 0 || index >= steps.size() || steps.get(index).getType() != PartyStepType.TOKEN_TURN) return false;
        for (int i = index - 1; i >= 0; i--) {
            PartyStep step = steps.get(i);
            if (step instanceof EventPartyStep event && event.isTransition()) continue;
            return step.getType() != PartyStepType.TOKEN_TURN;
        }
        return true;
    }

    /** @return the rank (1 = first) of the token in the play order, 0 if it is not part of the party. */
    public int getTokenRank(UUID token) {
        return tokens.indexOf(token) + 1;
    }

    public PartyStep getCurrentStep() {
        if (stepIndex < 0) return null;
        if (stepIndex >= steps.size()) return null;
        return steps.get(stepIndex);
    }

    public Map<TokenizedEntityInterface, PlayerEntity> getTokensWithOwners(ServerWorld world) {
        Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners = new HashMap<>();
        getTokens().forEach(tokenUUID -> {
            if (world.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                UUID ownerUUID = token.steveparty$getTokenOwner();
                if (world.getEntity(ownerUUID) instanceof PlayerEntity player) {
                    tokensWithOwners.put(token, player);
                }
            }
        });
        return tokensWithOwners;
    }
}
