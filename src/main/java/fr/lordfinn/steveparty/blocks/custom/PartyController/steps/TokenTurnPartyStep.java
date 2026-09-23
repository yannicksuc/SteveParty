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
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.UUID;

public class TokenTurnPartyStep extends PartyStep {
    /** Time given to an absent token (unloaded, dead, or owner offline) before its turn is skipped: 60 seconds. */
    public static final int ABSENT_TURN_DELAY_TICKS = 1200;
    /** How often the countdown checks whether the token came back. */
    private static final int ABSENT_CHECK_INTERVAL_TICKS = 20;
    /** How often the remaining time is shown in the action bar. */
    private static final int ABSENT_REMINDER_INTERVAL_TICKS = 100;

    private UUID tokenUUID;
    private UUID owner;
    /** Last known name of the token, to name it while it is not loaded. */
    private String tokenName;
    /**
     * World time at which the turn of the absent token is skipped, -1 while the turn is not waiting for it.
     * Saved, so the countdown goes on after a server restart (see {@link #resume}).
     */
    private long absentDeadline = -1;
    private UUID cancelTaskId = null;

    public TokenTurnPartyStep(NbtCompound nbt) {
        super(nbt);
        // Field initializers ran after super(nbt): read again what they reset
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
        absentDeadline = -1;
        if (partyControllerEntity.getWorld() instanceof ServerWorld serverWorld) {
            if (this.tokenUUID == null) {
                cancelTurn(partyControllerEntity.getPartyData().getOwners(serverWorld), partyControllerEntity);
                return;
            }
            if (isTokenAvailable(serverWorld)) {
                grantMove(serverWorld);
            } else {
                // Absent token: the turn waits for it for a while instead of being skipped right away
                startAbsentCountdown(partyControllerEntity, serverWorld, ABSENT_TURN_DELAY_TICKS);
            }
        }
    }

    @Override
    public void resume(PartyControllerEntity partyControllerEntity) {
        if (!(partyControllerEntity.getWorld() instanceof ServerWorld serverWorld)) return;
        if (tokenUUID == null) {
            cancelTurn(partyControllerEntity.getPartyData().getOwners(serverWorld), partyControllerEntity);
            return;
        }
        // The token status is saved with the entity, re-grant it anyway in case it was lost
        if (isTokenAvailable(serverWorld)) {
            absentDeadline = -1;
            grantMove(serverWorld);
            partyControllerEntity.markDirty();
            return;
        }
        // Never skip here: the token may simply not be loaded yet. Go on with the saved countdown, or restart
        // a full one if there was none or if it expired while the controller was not loaded.
        long remaining = absentDeadline - serverWorld.getTime();
        if (absentDeadline < 0 || remaining <= 0 || remaining > ABSENT_TURN_DELAY_TICKS)
            startAbsentCountdown(partyControllerEntity, serverWorld, ABSENT_TURN_DELAY_TICKS);
        else
            announceAbsence(partyControllerEntity, serverWorld, remaining);
    }

    @Override
    public void tick(PartyControllerEntity partyControllerEntity, ServerWorld world) {
        long now = world.getTime();
        if (absentDeadline < 0) {
            // The turn is being played: if the token becomes unavailable (owner disconnected, token unloaded...),
            // the same countdown starts, but never while the token is moving (the movement ends first)
            if (tokenUUID != null && now % ABSENT_CHECK_INTERVAL_TICKS == 0
                    && !isTokenAvailable(world) && !isTokenMoving(world))
                startAbsentCountdown(partyControllerEntity, world, ABSENT_TURN_DELAY_TICKS);
            return;
        }
        if (now < absentDeadline && now % ABSENT_CHECK_INTERVAL_TICKS != 0) return;

        if (isTokenAvailable(world)) {
            // Back in time: the turn is played normally
            absentDeadline = -1;
            grantMove(world);
            MessageUtils.sendToPlayers(partyControllerEntity.getPartyAudience(),
                    Text.translatableWithFallback("message.steveparty.absent_turn.back",
                            "%s is back, its turn can be played!", getTokenDisplayName(world)).formatted(Formatting.GREEN),
                    MessageUtils.MessageType.CHAT);
            partyControllerEntity.markDirty();
            partyControllerEntity.sendPacketToInterestedPlayers();
            return;
        }

        long remaining = absentDeadline - now;
        if (remaining <= 0) {
            skipAbsentTurn(partyControllerEntity);
            return;
        }
        if (now % ABSENT_REMINDER_INTERVAL_TICKS == 0) {
            MessageUtils.sendToPlayers(partyControllerEntity.getPartyAudience(),
                    Text.translatableWithFallback("message.steveparty.absent_turn.countdown",
                            "Turn of %1$s skipped in %2$s s", getTokenDisplayName(world), toSeconds(remaining))
                            .formatted(Formatting.GOLD),
                    MessageUtils.MessageType.ACTION_BAR);
        }
    }

    /**
     * @return true while this turn is the current one and waits for its absent token
     */
    public boolean isWaitingForAbsentToken() {
        return status == Status.IN_PROGRESS && absentDeadline >= 0;
    }

    /**
     * Skips this turn now if it is waiting for its absent token.
     *
     * @return false if the turn was not waiting (nothing done)
     */
    public boolean skipAbsentTurn(PartyControllerEntity partyControllerEntity) {
        if (!isWaitingForAbsentToken() || !isStillActive(partyControllerEntity)) return false;
        absentDeadline = -1;
        if (partyControllerEntity.getWorld() instanceof ServerWorld serverWorld) {
            MessageUtils.sendToPlayers(partyControllerEntity.getPartyAudience(),
                    Text.translatableWithFallback("message.steveparty.absent_turn.skipped",
                            "The turn of %s has been skipped.", getTokenDisplayName(serverWorld)).formatted(Formatting.GRAY),
                    MessageUtils.MessageType.CHAT);
        }
        partyControllerEntity.nextStep();
        return true;
    }

    @Override
    public void end(PartyControllerEntity partyControllerEntity) {
        super.end(partyControllerEntity);
        absentDeadline = -1;
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

    /**
     * A token is available when it is loaded in the controller's world, alive, still a token, and when its owner
     * (if any: an ownerless token can be moved by anyone) is connected.
     */
    private boolean isTokenAvailable(ServerWorld world) {
        if (tokenUUID == null) return false;
        Entity entity = world.getEntity(tokenUUID);
        if (!(entity instanceof TokenizedEntityInterface token) || !entity.isAlive() || !token.steveparty$isTokenized())
            return false;
        UUID tokenOwner = token.steveparty$getTokenOwner();
        this.owner = tokenOwner;
        if (entity.getCustomName() != null)
            this.tokenName = entity.getCustomName().getString();
        return tokenOwner == null || world.getServer().getPlayerManager().getPlayer(tokenOwner) != null;
    }

    /**
     * @return true if the loaded token still has steps to walk, or a dice roll is about to move it
     * (TokenMovementService schedules the movement under the token's UUID)
     */
    private boolean isTokenMoving(ServerWorld world) {
        if (Steveparty.SCHEDULER.isScheduled(tokenUUID)) return true;
        return world.getEntity(tokenUUID) instanceof TokenizedEntityInterface token && token.steveparty$getNbSteps() > 0;
    }

    private void grantMove(ServerWorld world) {
        if (world.getEntity(tokenUUID) instanceof TokenizedEntityInterface tokenInterface) {
            // IN_GAME too: a token that was not loaded when the party booted never received it
            tokenInterface.steveparty$setStatus(TokenStatus.setStatuses(tokenInterface.steveparty$getStatus(), TokenStatus.IN_GAME, TokenStatus.CAN_MOVE));
        }
    }

    private void startAbsentCountdown(PartyControllerEntity partyControllerEntity, ServerWorld world, long delay) {
        absentDeadline = world.getTime() + delay;
        partyControllerEntity.markDirty();
        announceAbsence(partyControllerEntity, world, delay);
    }

    private void announceAbsence(PartyControllerEntity partyControllerEntity, ServerWorld world, long remaining) {
        BlockPos pos = partyControllerEntity.getPos();
        String target = tokenUUID + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        MutableText message = Text.translatableWithFallback("message.steveparty.absent_turn",
                        "%1$s is absent: its turn will be skipped in %2$s seconds.",
                        getTokenDisplayName(world), toSeconds(remaining))
                .formatted(Formatting.GOLD)
                .append(" ")
                .append(clickable(Text.translatableWithFallback("message.steveparty.absent_turn.skip_button", "[Skip the turn]"),
                        Formatting.YELLOW, "/steveparty skip_turn " + target,
                        Text.translatableWithFallback("message.steveparty.absent_turn.skip_hover", "Skip this turn now")))
                .append(" ")
                .append(clickable(Text.translatableWithFallback("message.steveparty.absent_turn.exclude_button", "[Exclude from the party]"),
                        Formatting.RED, "/steveparty exclude " + target,
                        Text.translatableWithFallback("message.steveparty.absent_turn.exclude_hover",
                                "Remove this token and all its next turns from the party")));
        MessageUtils.sendToPlayers(partyControllerEntity.getPartyAudience(), message, MessageUtils.MessageType.CHAT);
    }

    private static Text clickable(MutableText label, Formatting color, String command, Text hover) {
        return label.styled(style -> style.withColor(color).withUnderline(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static long toSeconds(long ticks) {
        return (ticks + 19) / 20;
    }

    /**
     * Name of the token: its current name if loaded, else the last known one, else the beginning of its UUID.
     */
    public Text getTokenDisplayName(@Nullable ServerWorld world) {
        if (world != null && tokenUUID != null && world.getEntity(tokenUUID) instanceof Entity entity)
            return entity.getCustomName() != null ? entity.getCustomName() : entity.getName();
        if (tokenName != null)
            return Text.literal(tokenName);
        return Text.literal(tokenUUID == null ? "?" : tokenUUID.toString().substring(0, 8));
    }

    /** Remembers the token name, used while the token is not loaded. */
    public void setTokenName(@Nullable String tokenName) {
        this.tokenName = tokenName;
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
        if (nbt.contains("TokenName")) {
            this.tokenName = nbt.getString("TokenName");
        }
        this.absentDeadline = nbt.contains("AbsentDeadline") ? nbt.getLong("AbsentDeadline") : -1;
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbtCompound = super.toNbt();
        if (tokenUUID != null)
            nbtCompound.putString("Token", tokenUUID.toString());
        if (owner != null)
            nbtCompound.putString("Owner", owner.toString());
        if (tokenName != null)
            nbtCompound.putString("TokenName", tokenName);
        if (absentDeadline >= 0)
            nbtCompound.putLong("AbsentDeadline", absentDeadline);
        return nbtCompound;
    }

    public UUID getTokenUUID() {
        return tokenUUID;
    }

    public UUID getOwnerUUID() {
        return owner;
    }
}
