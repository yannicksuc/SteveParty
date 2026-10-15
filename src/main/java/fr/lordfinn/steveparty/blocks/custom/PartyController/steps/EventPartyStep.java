package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.EndPipe;
import fr.lordfinn.steveparty.blocks.custom.PartyController.EventTimeOut;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.minigame.MiniGameReturns;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rings party bells, one moment after the other, and waits wherever a bell in waiting mode listens to the moment:
 * the party goes on once that bell receives a redstone signal (or once no bell waits for it any more).
 * <ul>
 *     <li>An "event" card of the party program, or an Event space a pawn landed on, gives an event step (moment
 *     {@link PartyMoment#EVENT}): a time out ({@link EventTimeOut}). Its players come out of its pipes, if any, and
 *     come back at its end; its hourglass, if any, ends it when it runs out, even with a bell still waiting; a waiting
 *     bell released ends it at once.</li>
 *     <li>A <em>transition</em> step is inserted by the controller between two steps when a bell waits at one of the
 *     moments of that transition (end of turn, start of round...). It removes itself once done.</li>
 * </ul>
 */
public class EventPartyStep extends PartyStep {
    private static final int BELL_CHECK_INTERVAL_TICKS = 20;

    // No initializers: they would run after super(nbt) and wipe what fromNbt just read
    private List<PartyMoment> moments;
    private List<Integer> values;
    private boolean transition;
    /** Index (in {@link #moments}) of the moment waited for, -1 while not waiting. */
    private int waitingIndex;
    private UUID continueTaskId;
    /** Where the players come out for the event (none: they stay where they are). */
    private List<EndPipe> pipes;
    /** Its hourglass, in seconds (0: none). */
    private int seconds;
    /** Slipped in by an Event space (not a card of the program). */
    private boolean fromSpace;
    /** The game time its hourglass runs out at (-1: none, or not started). */
    private long deadline;
    /** Where the players sent out stood. */
    private Map<UUID, MiniGameReturns.Return> returns;
    /** Its hourglass bar while it runs. Not saved: made again on resume. */
    private ServerBossBar hourglass;

    public EventPartyStep(NbtCompound nbt) {
        super(nbt);
        if (moments == null) moments = new ArrayList<>();
        if (values == null) values = new ArrayList<>();
        if (pipes == null) pipes = new ArrayList<>();
        if (returns == null) returns = new LinkedHashMap<>();
    }

    private EventPartyStep(List<PartyMoment> moments, List<Integer> values, boolean transition) {
        super();
        this.moments = new ArrayList<>(moments);
        this.values = new ArrayList<>(values);
        this.transition = transition;
        this.waitingIndex = -1;
        this.pipes = new ArrayList<>();
        this.returns = new LinkedHashMap<>();
        this.deadline = -1;
        setType(PartyStepType.EVENT);
    }

    /** Step of an "event" card: rings the event bells with {@code channel} as value. */
    public static EventPartyStep eventCard(int channel) {
        return eventCard(channel, List.of(), 0);
    }

    /**
     * Step of an "event" card: rings the event bells with {@code channel} as value, the players out of {@code pipes},
     * an hourglass of {@code seconds} (0: none).
     */
    public static EventPartyStep eventCard(int channel, List<EndPipe> pipes, int seconds) {
        EventPartyStep step = new EventPartyStep(List.of(PartyMoment.EVENT), List.of(channel), false);
        step.pipes.addAll(pipes);
        step.seconds = Math.max(0, seconds);
        return step;
    }

    /** The event of an Event space: as an event card, slipped into the round it is played in. */
    public static EventPartyStep eventSpace(int channel, List<EndPipe> pipes, int seconds) {
        EventPartyStep step = eventCard(channel, pipes, seconds);
        step.fromSpace = true;
        return step;
    }

    /** Step inserted by the controller to ring the moments of a transition, waiting where a bell waits. */
    public static EventPartyStep transition(List<PartyMoment> moments, List<Integer> values) {
        return new EventPartyStep(moments, values, true);
    }

    public boolean isTransition() {
        return transition;
    }

    @Override
    public boolean isInterlude() {
        return transition || fromSpace;
    }

    public int getChannel() {
        return values.isEmpty() ? 0 : values.getFirst();
    }

    public List<EndPipe> getPipes() {
        return List.copyOf(pipes);
    }

    /** Its hourglass, in seconds (0: none). */
    public int getSeconds() {
        return seconds;
    }

    /** @return true while its hourglass runs */
    public boolean isTimed() {
        return status == Status.IN_PROGRESS && deadline >= 0;
    }

    /** @return true while players sent out by it are still to come back */
    public boolean hasPlayersOut() {
        return !returns.isEmpty();
    }

    @Override
    public boolean isWaitingForBell() {
        return status == Status.IN_PROGRESS && waitingIndex >= 0;
    }

    public PartyMoment getWaitedMoment() {
        return waitingIndex >= 0 && waitingIndex < moments.size() ? moments.get(waitingIndex) : null;
    }

    @Override
    public void start(PartyControllerEntity partyControllerEntity) {
        super.start(partyControllerEntity);
        waitingIndex = -1;
        deadline = -1;
        if (!transition && partyControllerEntity.getWorld() instanceof ServerWorld world) {
            // The time out: the players out of its pipes, its hourglass turned
            returns.putAll(EventTimeOut.sendOut(partyControllerEntity, world, pipes));
            if (seconds > 0) {
                deadline = world.getTime() + seconds * 20L;
                updateHourglass(partyControllerEntity, world);
            }
        }
        ringFrom(0, partyControllerEntity);
    }

    @Override
    public void resume(PartyControllerEntity partyControllerEntity) {
        if (deadline >= 0 && partyControllerEntity.getWorld() instanceof ServerWorld world) updateHourglass(partyControllerEntity, world);
        // A bell was waited for, or the hourglass runs: keep waiting. Otherwise the step was about to go on.
        if (waitingIndex < 0 && deadline < 0) scheduleContinue(partyControllerEntity);
    }

    /** Rings the moments from {@code index}, stopping on the first one a bell waits for. */
    private void ringFrom(int index, PartyControllerEntity controller) {
        for (int i = index; i < moments.size(); i++) {
            if (PartyBellBlockEntity.ring(controller, moments.get(i), values.get(i))) {
                waitingIndex = i;
                controller.markDirty();
                controller.sendPacketToInterestedPlayers();
                return;
            }
        }
        waitingIndex = -1;
        controller.markDirty();
        // The hourglass still runs: the event goes on until it runs out
        if (deadline < 0) scheduleContinue(controller);
    }

    /** Goes on one tick later, never re-entrantly (this may run from inside the controller's step change). */
    private void scheduleContinue(PartyControllerEntity controller) {
        if (continueTaskId != null) Steveparty.SCHEDULER.cancel(continueTaskId);
        continueTaskId = UUID.randomUUID();
        Steveparty.SCHEDULER.schedule(continueTaskId, 1, () -> {
            continueTaskId = null;
            if (isStillActive(controller) && waitingIndex < 0) controller.nextStep();
        });
    }

    @Override
    public boolean onMomentReleased(PartyMoment moment, PartyControllerEntity partyControllerEntity) {
        if (!isWaitingForBell() || getWaitedMoment() != moment) return false;
        if (!transition && deadline >= 0) {
            // The redstone of the waiting bell ends the event before its hourglass
            waitingIndex = -1;
            deadline = -1;
            scheduleContinue(partyControllerEntity);
            return true;
        }
        ringFrom(waitingIndex + 1, partyControllerEntity);
        return true;
    }

    @Override
    public void tick(PartyControllerEntity partyControllerEntity, ServerWorld world) {
        if (deadline >= 0) {
            if (world.getTime() >= deadline) {
                // The hourglass ran out: the event is over, a bell still waiting or not
                deadline = -1;
                waitingIndex = -1;
                partyControllerEntity.markDirty();
                scheduleContinue(partyControllerEntity);
                return;
            }
            if (world.getTime() % 10 == 0) updateHourglass(partyControllerEntity, world);
        }
        // The waiting bell was broken, moved away or set back to the simple mode: nothing to wait for any more
        if (waitingIndex >= 0 && world.getTime() % BELL_CHECK_INTERVAL_TICKS == 0
                && !PartyBellBlockEntity.isHeld(partyControllerEntity, moments.get(waitingIndex)))
            ringFrom(waitingIndex + 1, partyControllerEntity);
    }

    private void updateHourglass(PartyControllerEntity controller, ServerWorld world) {
        if (hourglass == null) hourglass = EventTimeOut.hourglass();
        EventTimeOut.showHourglass(hourglass, controller, world, deadline - world.getTime(), seconds * 20);
    }

    @Override
    public void end(PartyControllerEntity partyControllerEntity) {
        super.end(partyControllerEntity);
        waitingIndex = -1;
        deadline = -1;
        if (continueTaskId != null) {
            Steveparty.SCHEDULER.cancel(continueTaskId);
            continueTaskId = null;
        }
        if (hourglass != null) {
            hourglass.clearPlayers();
            hourglass = null;
        }
        // However the event ends (hourglass, bell, Step Controller, the party stopped), the players come back
        if (partyControllerEntity.getWorld() instanceof ServerWorld world) EventTimeOut.bringBack(world, returns);
    }

    @Override
    public void fromNbt(NbtCompound nbt) {
        super.fromNbt(nbt);
        moments = new ArrayList<>();
        values = new ArrayList<>();
        NbtList momentsNbt = nbt.getList("Moments", NbtElement.STRING_TYPE);
        int[] valuesNbt = nbt.getIntArray("Values");
        for (int i = 0; i < momentsNbt.size(); i++) {
            moments.add(PartyMoment.byName(momentsNbt.getString(i), PartyMoment.EVENT));
            values.add(i < valuesNbt.length ? valuesNbt[i] : 0);
        }
        transition = nbt.getBoolean("Transition");
        waitingIndex = nbt.contains("WaitingIndex") ? nbt.getInt("WaitingIndex") : -1;
        if (waitingIndex >= moments.size()) waitingIndex = -1;
        pipes = new ArrayList<>();
        if (nbt.contains("Pipes"))
            EndPipe.CODEC.listOf().parse(NbtOps.INSTANCE, nbt.get("Pipes")).result().ifPresent(pipes::addAll);
        seconds = nbt.getInt("Seconds");
        fromSpace = nbt.getBoolean("FromSpace");
        deadline = nbt.contains("Deadline") ? nbt.getLong("Deadline") : -1;
        returns = new LinkedHashMap<>();
        NbtCompound returnsNbt = nbt.getCompound("Returns");
        for (String key : returnsNbt.getKeys()) {
            try {
                returns.put(UUID.fromString(key), MiniGameReturns.Return.fromNbt(returnsNbt.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound nbt = super.toNbt();
        NbtList momentsNbt = new NbtList();
        moments.forEach(moment -> momentsNbt.add(NbtString.of(moment.asString())));
        nbt.put("Moments", momentsNbt);
        nbt.putIntArray("Values", values.stream().mapToInt(Integer::intValue).toArray());
        if (transition) nbt.putBoolean("Transition", true);
        if (waitingIndex >= 0) nbt.putInt("WaitingIndex", waitingIndex);
        if (!pipes.isEmpty())
            EndPipe.CODEC.listOf().encodeStart(NbtOps.INSTANCE, pipes).result().ifPresent(element -> nbt.put("Pipes", element));
        if (seconds > 0) nbt.putInt("Seconds", seconds);
        if (fromSpace) nbt.putBoolean("FromSpace", true);
        if (deadline >= 0) nbt.putLong("Deadline", deadline);
        if (!returns.isEmpty()) {
            NbtCompound returnsNbt = new NbtCompound();
            returns.forEach((uuid, back) -> returnsNbt.put(uuid.toString(), back.toNbt()));
            nbt.put("Returns", returnsNbt);
        }
        return nbt;
    }
}
