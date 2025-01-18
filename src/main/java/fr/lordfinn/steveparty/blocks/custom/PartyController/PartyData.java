package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepFactory;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.stream.Collectors;

public class PartyData {
    private PartyStepType type = PartyStepType.DEFAULT;
    private List<PartyStep> steps = new ArrayList<>();
    private List<UUID> tokens = new ArrayList<>();
    private int stepIndex = -1;
    private int nbTurn = 10;

    /*public static final Codec<PartyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PartyStepType.CODEC.fieldOf("Type").forGetter(PartyData::getType),
            PartyStep.CODEC.listOf().fieldOf("Steps").forGetter(PartyData::getSteps),
            Codec.list(UUIDCodec.CODEC).fieldOf("Tokens").forGetter(PartyData::getTokens),
            Codec.INT.fieldOf("StepIndex").orElse(-1).forGetter(PartyData::getStepIndex),
            Codec.INT.fieldOf("NbTurn").orElse(10).forGetter(PartyData::getNbTurn)
    ).apply(instance, PartyData::new));*/

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
     * Populates the TokenData instance from an NBT tag.
     *
     * @param nbt the NBT data
     */
    public void fromNbt(NbtCompound nbt) {
        if (nbt.contains("Steps")) {
            nbt.getList("Steps", 10).forEach(nbtStep -> {
                if (PartyStepFactory.get((NbtCompound) nbtStep) instanceof PartyStep step)
                    this.steps.add(step);
            });
        }
        if (nbt.contains("Tokens")) {
            nbt.getList("Tokens", 8).forEach(token -> {
                this.tokens.add(UUID.fromString(token.asString()));
            });
        }
        if (nbt.contains("StepIndex")) {
            this.stepIndex = nbt.getInt("StepIndex");
        }
        if (nbt.contains("NbTurn")) {
            this.nbTurn = nbt.getInt("NbTurn");
        }
    }

    /**
     * Saves the TokenData instance to an NBT tag.
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
        return nbt;
    }

    public void writeToPacket(PacketByteBuf buf) {
        // Write steps to the buffer
        buf.writeInt(steps.size()); // Write the size of the steps list
        for (PartyStep step : steps) {
            NbtCompound stepNbt = step.toNbt();
            buf.writeNbt(stepNbt); // Write each step as NBT
        }

        // Write tokens to the buffer
        buf.writeInt(tokens.size()); // Write the size of the tokens list
        for (UUID uuid : tokens) {
            buf.writeUuid(uuid); // Write each UUID directly
        }

        buf.writeInt(stepIndex);
        buf.writeInt(nbTurn);
    }

    public static PartyData fromBuf(PacketByteBuf buf) {
        PartyData party = new PartyData();

        // Read steps from the buffer
        int stepCount = buf.readInt(); // Read the size of the steps list
        for (int i = 0; i < stepCount; i++) {
            NbtCompound stepNbt = buf.readNbt(); // Read each step as NBT
            if (stepNbt != null && PartyStepFactory.get(stepNbt) instanceof PartyStep step) {
                party.steps.add(step);
            }
        }

        // Read tokens from the buffer
        int tokenCount = buf.readInt(); // Read the size of the tokens list
        for (int i = 0; i < tokenCount; i++) {
            UUID uuid = buf.readUuid(); // Read each UUID directly
            party.tokens.add(uuid);
        }

        party.stepIndex = buf.readInt();
        party.nbTurn = buf.readInt();
        return party;
    }

    public boolean isStarted() {
        return stepIndex > -1 && stepIndex < steps.size();
    }

    public List<PartyStep> getSteps() {
        return steps;
    }

    public void setSteps(List<PartyStep> steps) {
        this.steps = steps;
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

    public void removeStep(PartyStep step) {
        this.steps.remove(step);
    }

    public void removeToken(UUID uuid) {
        this.tokens.remove(uuid);
    }

    public void reset() {
        this.tokens.clear();
        this.stepIndex = -1;
        this.steps.clear();
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

    public Map<TokenizedEntityInterface, PlayerEntity> getAllTokensWithOwners(ServerWorld world) {
        Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners = new HashMap<>();
        getTokens().forEach(tokenUUID -> {
            if (world.getEntity(tokenUUID) instanceof TokenizedEntityInterface token) {
                UUID ownerUUID = token.steveparty$getTokenOwner();
                if (world.getEntity(ownerUUID) instanceof PlayerEntity player) {
                    tokensWithOwners.put(token, player);
                } else {
                    tokensWithOwners.put(token, null);
                }
            }
        });
        return tokensWithOwners;
    }

    public Text getParticipantsAsString(ServerWorld world) {
        Map<TokenizedEntityInterface, PlayerEntity> tokensWithOwners = getAllTokensWithOwners(world);

        MutableText result = Text.empty();
        Iterator<Map.Entry<TokenizedEntityInterface, PlayerEntity>> iterator = tokensWithOwners.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<TokenizedEntityInterface, PlayerEntity> entry = iterator.next();
            Text name = entry.getValue() != null
                    ? entry.getValue().getName()
                    : Text.literal("Disconnected User").styled(style -> style.withColor(Formatting.GRAY));
            MutableText participantText = Text.translatable(
                    "message.steveparty.played_by",
                    ((Entity) entry.getKey()).getCustomName(),
                    name
            );

            result.append(participantText);
            if (iterator.hasNext()) {
                result.append(Text.literal(", "));
            }
        }

        return result;
    }
}