package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.powerups.effects.PowerUpProtection;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.powerups.PowerUpTurn;
import fr.lordfinn.steveparty.service.DiceRollEffects;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
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
    /**
     * The extra turn a Replay tile gives (see {@link #grantReplay}): saved, and shown as such in the steps HUD. Landing
     * on a Replay tile during it gives no further turn.
     */
    private boolean replay;
    /** The die spent for this turn (a copy), given back with a replay. Not saved: lost across a restart. */
    private ItemStack spentDie = ItemStack.EMPTY;
    /** Total of the dice rolled for this turn by its player, 0 until they roll (shown by the party HUD). Not saved. */
    private int roll;
    /** What that roll does (coins, swap, a face 0...), {@link DiceOutcome#NONE} until they roll. Not saved. */
    private DiceOutcome outcome = DiceOutcome.NONE;
    /** The coins the roll really gave (negative: took), null until it did. */
    private @Nullable Integer rollCoins;
    /** The token the roll swapped this one with, empty until it did. */
    private String swapWith = "";
    /** The power-up used during this turn and what it remembers until the end of it. Saved. */
    private final PowerUpTurn powerUps = new PowerUpTurn();

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
        roll = 0;
        outcome = DiceOutcome.NONE;
        rollCoins = null;
        swapWith = "";
        powerUps.reset();
        if (partyControllerEntity.getWorld() instanceof ServerWorld serverWorld) {
            if (this.tokenUUID == null) {
                cancelTurn(partyControllerEntity.getPartyData().getOwners(serverWorld), partyControllerEntity);
                return;
            }
            // A Padlock lasts until its player's next turn (a Replay tile's extra turn is still the same turn)
            if (!replay) PowerUpProtection.expire(partyControllerEntity, tokenUUID);
            // A Trap took this turn: it is lost (a Replay tile's extra turn is still played)
            if (!replay && partyControllerEntity.getPartyData().getSkippedTokens().remove(this.tokenUUID)) {
                partyControllerEntity.markDirty();
                MessageUtils.sendToPlayers(partyControllerEntity.getPartyAudience(), Text.translatable("message.steveparty.trap.turn_lost",
                        getTokenDisplayName(serverWorld)).formatted(Formatting.RED), MessageUtils.MessageType.CHAT);
                cancelTaskId = UUID.randomUUID();
                Steveparty.SCHEDULER.schedule(cancelTaskId, 20, () -> {
                    cancelTaskId = null;
                    if (isStillActive(partyControllerEntity)) partyControllerEntity.nextStep();
                });
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

    /** @return true for the extra turn a Replay tile gave */
    public boolean isReplay() {
        return replay;
    }

    @Override
    public String getName() {
        return replay ? "party_step_type.token_turn_replay" : super.getName();
    }

    /** Remembers the die spent by this turn's player (for a replay: see {@link #grantReplay}). Never consumes the roll. */
    @Override
    public ActionResult onDiceRoll(DiceEntity dice, UUID ownerUUID, int rollValue, PartyControllerEntity partyControllerEntity) {
        if (status == Status.IN_PROGRESS && (owner == null || owner.equals(ownerUUID))) {
            spentDie = spentDie(dice);
            roll = rollValue;
            DiceOutcome rolled = dice.getOutcome();
            outcome = rolled.steps() == rollValue ? rolled : DiceOutcome.ofSteps(rollValue);
            rollCoins = null;
            swapWith = "";
        }
        return ActionResult.PASS;
    }

    /** @return what the roll of this turn does, {@link DiceOutcome#NONE} while its player has not rolled */
    public DiceOutcome getOutcome() {
        return outcome;
    }

    /** @return true once this turn's player rolled something that plays (not the blank side: they may roll again) */
    public boolean hasRolled() {
        return outcome.steps() != 0 || outcome.isSpecial() || outcome.zero();
    }

    /** The coins the roll gave (negative: took): what it really did once applied, what it is about to do before. */
    public int getRollCoins() {
        return rollCoins != null ? rollCoins : outcome.coins();
    }

    public void noteCoins(int coins) {
        this.rollCoins = coins;
    }

    /** @return the name of the token this one was swapped with, empty while it was not */
    public String getSwapWith() {
        return swapWith;
    }

    public void noteSwap(String with) {
        this.swapWith = with == null ? "" : with;
    }

    /** The power-up state of this turn: the power-up used, if any, and its data (see {@code PowerUpService}). */
    public PowerUpTurn getPowerUps() {
        return powerUps;
    }

    /** @return the total rolled for this turn by its player, 0 while they have not rolled */
    public int getRoll() {
        return roll;
    }

    /** @return the world time at which the turn of the absent token is skipped, -1 while it is not waited for */
    public long getAbsentDeadline() {
        return absentDeadline;
    }

    /**
     * The item of a rolled die (a group of linked dice: the one holding it), empty if nothing was spent: a die without
     * the Power-up module goes back to its owner by itself.
     */
    private static ItemStack spentDie(DiceEntity dice) {
        List<DiceEntity> group = new java.util.ArrayList<>(List.of(dice));
        if (dice.getWorld() instanceof ServerWorld world)
            for (UUID linked : dice.getLinkedDice())
                if (world.getEntity(linked) instanceof DiceEntity other) group.add(other);
        for (DiceEntity die : group) {
            ItemStack item = die.getItemReference();
            if (item != null && !item.isEmpty()) return DiceModules.returnsToRoller(item) ? ItemStack.EMPTY : item.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Rejouer / Roll Again: the token of this turn landed on a Replay tile. Its owner plays again right away: a turn of
     * the same token is inserted right after this one (the turn order then goes on as before), and the die spent for
     * this turn comes back to the owner (not in creative), so the replay can always be rolled, a forged die included.
     * The extra turn itself never gives another one (no endless chain).
     *
     * @return the extra turn (started by the caller's next step), null if this turn gives none
     */
    public @Nullable TokenTurnPartyStep grantReplay(PartyControllerEntity partyControllerEntity) {
        if (replay || tokenUUID == null || !isStillActive(partyControllerEntity)) return null;
        TokenTurnPartyStep extra = new TokenTurnPartyStep(tokenUUID, owner);
        extra.replay = true;
        extra.tokenName = tokenName;
        PartyData partyData = partyControllerEntity.getPartyData();
        partyData.getSteps().add(partyData.getStepIndex() + 1, extra);
        if (!spentDie.isEmpty() && owner != null && partyControllerEntity.getWorld() instanceof ServerWorld world) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
            if (player != null && !player.isInCreativeMode()) player.getInventory().offerOrDrop(spentDie.copy());
        }
        spentDie = ItemStack.EMPTY;
        partyControllerEntity.markDirty();
        return extra;
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
        if (Steveparty.SCHEDULER.isScheduled(tokenUUID) || DiceRollEffects.isResolving(tokenUUID)) return true;
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
        this.replay = nbt.getBoolean("Replay");
        // Field initializers run after super(nbt): the state may not exist yet on that first read
        if (powerUps != null) powerUps.fromNbt(nbt.getCompound("PowerUps"));
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
        if (replay)
            nbtCompound.putBoolean("Replay", true);
        if (powerUps.hasUsed())
            nbtCompound.put("PowerUps", powerUps.toNbt());
        return nbtCompound;
    }

    public UUID getTokenUUID() {
        return tokenUUID;
    }

    public UUID getOwnerUUID() {
        return owner;
    }
}
