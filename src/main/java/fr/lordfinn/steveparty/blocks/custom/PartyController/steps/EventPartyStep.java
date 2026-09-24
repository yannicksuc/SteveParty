package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rings party bells, one moment after the other, and waits wherever a bell in waiting mode listens to the moment:
 * the party goes on once that bell receives a redstone signal (or once no bell waits for it any more).
 * <ul>
 *     <li>An "event" card of the party program gives an event step (moment {@link PartyMoment#EVENT}).</li>
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

    public EventPartyStep(NbtCompound nbt) {
        super(nbt);
        if (moments == null) moments = new ArrayList<>();
        if (values == null) values = new ArrayList<>();
    }

    private EventPartyStep(List<PartyMoment> moments, List<Integer> values, boolean transition) {
        super();
        this.moments = new ArrayList<>(moments);
        this.values = new ArrayList<>(values);
        this.transition = transition;
        this.waitingIndex = -1;
        setType(PartyStepType.EVENT);
    }

    /** Step of an "event" card: rings the event bells with {@code channel} as value. */
    public static EventPartyStep eventCard(int channel) {
        return new EventPartyStep(List.of(PartyMoment.EVENT), List.of(channel), false);
    }

    /** Step inserted by the controller to ring the moments of a transition, waiting where a bell waits. */
    public static EventPartyStep transition(List<PartyMoment> moments, List<Integer> values) {
        return new EventPartyStep(moments, values, true);
    }

    public boolean isTransition() {
        return transition;
    }

    public int getChannel() {
        return values.isEmpty() ? 0 : values.getFirst();
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
        ringFrom(0, partyControllerEntity);
    }

    @Override
    public void resume(PartyControllerEntity partyControllerEntity) {
        // A bell was waited for: keep waiting (the moment already rang). Otherwise the step was about to go on.
        if (waitingIndex < 0) scheduleContinue(partyControllerEntity);
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
        scheduleContinue(controller);
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
        ringFrom(waitingIndex + 1, partyControllerEntity);
        return true;
    }

    @Override
    public void tick(PartyControllerEntity partyControllerEntity, ServerWorld world) {
        // The waiting bell was broken, moved away or set back to the simple mode: nothing to wait for any more
        if (waitingIndex >= 0 && world.getTime() % BELL_CHECK_INTERVAL_TICKS == 0
                && !PartyBellBlockEntity.hasWaitingBell(partyControllerEntity, moments.get(waitingIndex)))
            ringFrom(waitingIndex + 1, partyControllerEntity);
    }

    @Override
    public void end(PartyControllerEntity partyControllerEntity) {
        super.end(partyControllerEntity);
        waitingIndex = -1;
        if (continueTaskId != null) {
            Steveparty.SCHEDULER.cancel(continueTaskId);
            continueTaskId = null;
        }
    }

    @Override
    public void printInfo(ServerPlayerEntity player) {
        super.printInfo(player);
        PartyMoment waited = getWaitedMoment();
        if (waited != null && status == Status.IN_PROGRESS)
            player.sendMessage(Text.translatableWithFallback("message.steveparty.event_step.waiting",
                    "Waiting for a signal on a party bell: %s", waited.getText()), false);
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
        return nbt;
    }
}
