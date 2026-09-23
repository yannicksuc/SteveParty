package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PartyStep {
    protected Status status = Status.WAITING;
    private PartyStepType type = PartyStepType.DEFAULT;

    public static final Codec<PartyStep> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Status.CODEC.fieldOf("status").forGetter(PartyStep::getStatus),
            PartyStepType.CODEC.fieldOf("type").forGetter(PartyStep::getType)
    ).apply(instance, PartyStep::new));

    // Constructor for the codec
    public PartyStep(Status status, PartyStepType type) {
        this.status = status;
        this.type = type;
    }

    public PartyStep() {
    }

    public PartyStep(NbtCompound step) {
        fromNbt(step);
    }

    public void fromNbt(NbtCompound step) {
        if (step.contains("Status")) {
            try {
                this.status = Status.valueOf(step.getString("Status"));
            } catch (IllegalArgumentException e) {
                Steveparty.LOGGER.warn("Unknown party step status '{}', defaulting to {}", step.getString("Status"), this.status);
            }
        }
        if (step.contains("Type")) {
            try {
                this.type = PartyStepType.valueOf(step.getString("Type").toUpperCase());
            } catch (IllegalArgumentException e) {
                Steveparty.LOGGER.warn("Unknown party step type '{}', defaulting to {}", step.getString("Type"), this.type);
            }
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString("Status", this.status.toString());
        nbtCompound.putString("Type", this.type.toString());
        return nbtCompound;
    }

    public void start(PartyControllerEntity partyControllerEntity) {
        this.status = Status.IN_PROGRESS;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }

    public PartyStepType getType() {
        return type;
    }

    protected void setType(PartyStepType type) {
        this.type = type;
    }

    public ActionResult onDiceRoll(DiceEntity dice, UUID ownerUUID, int rollValue, PartyControllerEntity partyControllerEntity) {
        return ActionResult.PASS;
    }

    public void printInfo(ServerPlayerEntity player) {
        MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.party_step_info", this.getType().getTranslatedText(), this.getStatus().getTranslatedText()), MessageUtils.MessageType.CHAT);
    }

    public void end(PartyControllerEntity partyControllerEntity) {
    }

    /**
     * Called once after the controller has been reloaded (server restart / chunk reload) while this step
     * was IN_PROGRESS. Scheduled tasks are not persisted, so steps that rely on them must re-kick them here.
     * Must be idempotent and must not replay what was already done.
     */
    public void resume(PartyControllerEntity partyControllerEntity) {
    }

    /**
     * @return true if a delayed callback started by this step may still act on the controller.
     */
    protected boolean isStillActive(PartyControllerEntity partyControllerEntity) {
        return partyControllerEntity != null
                && !partyControllerEntity.isRemoved()
                && this.status == Status.IN_PROGRESS
                && partyControllerEntity.getPartyData().getCurrentStep() == this;
    }

    public String getName() {
        return this.getType().getTranslationKey();
    }

    public int getColor() {
        Integer color = this.getStatus().color.getColorValue();
        return color == null ? 0xFFFFFF : color;
    }

    public ActionResult onTileReached(@NotNull MobEntity token, @NotNull BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyControllerEntity) {
        boardSpaceEntity.onTileReached(token, partyControllerEntity);
        if (boardSpaceEntity.getWorld() instanceof ServerWorld world && partyControllerEntity.getWorld() instanceof ServerWorld) {
            if (ABoardSpaceBlock.countsAsStep(world.getBlockState(boardSpaceEntity.getPos()).getBlock())
                    && ((TokenizedEntityInterface) token).steveparty$isTokenized()
                    && ((TokenizedEntityInterface) token).steveparty$getNbSteps() == 0) {
                boardSpaceEntity.onDestinationReached(token, partyControllerEntity);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    public enum Status {
        WAITING("status.waiting", Formatting.YELLOW),
        IN_PROGRESS("status.in_progress", Formatting.GREEN),
        FINISHED("status.finished", Formatting.GRAY);

        private final String translationKey;
        private final Formatting color;

        public static final Codec<Status> CODEC = Codec.STRING.xmap(Status::valueOf, Status::name);

        Status(String translationKey, Formatting color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public Formatting getColor() {
            return color;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        public Text getTranslatedText() {
            return Text.translatable(translationKey).formatted(color);
        }
    }
}
