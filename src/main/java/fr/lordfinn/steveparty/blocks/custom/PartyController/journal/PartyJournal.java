package fr.lordfinn.steveparty.blocks.custom.PartyController.journal;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The turn journal of a party: its last {@value #MAX_FRAMES} steps (pawn turns and mini-games), each with the party as it
 * stood when it started and what the game did during it, newest last. Kept by the Party Controller and saved with it, so
 * that a Step Controller can replay a turn or go back to an earlier one (see {@link JournalRewind}), even after a restart.
 * Only what the game does is written down (see {@link TurnJournal}): never what players or the map maker do themselves.
 */
public final class PartyJournal {
    public static final int MAX_FRAMES = 12;
    /** Its key in the controller's data: kept by the server, never sent to the clients. */
    public static final String KEY = "TurnJournal";

    private final List<JournalFrame> frames = new ArrayList<>();

    /** The step at {@code stepIndex} starts: a new frame, the party as it stands now. */
    public void open(PartyControllerEntity controller, JournalFrame.Kind kind, int stepIndex) {
        Map<UUID, JournalFrame.Pose> pawns = new LinkedHashMap<>();
        if (controller.getWorld() instanceof ServerWorld world) {
            for (UUID token : controller.getPartyData().getTokens()) {
                Entity entity = world.getEntity(token);
                if (entity == null) continue;
                float body = entity instanceof LivingEntity living ? living.bodyYaw : entity.getYaw();
                pawns.put(token, new JournalFrame.Pose(entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), entity.getPitch(),
                        entity.getHeadYaw(), body));
            }
        }
        frames.add(new JournalFrame(kind, stepIndex, controller.getPartyData().toNbt(new NbtCompound()), controller.getStarSpace(), pawns));
        while (frames.size() > MAX_FRAMES) frames.removeFirst();
    }

    /** The frame of the step being played, null for none (the party not started, or nothing kept). */
    public @Nullable JournalFrame current() {
        return frames.isEmpty() ? null : frames.getLast();
    }

    public List<JournalFrame> frames() {
        return frames;
    }

    /** Nothing kept (a party starts, or ends). */
    public void clear() {
        frames.clear();
    }

    void record(JournalTransfer transfer) {
        JournalFrame frame = current();
        if (frame != null) frame.add(transfer);
    }

    /** {@code space} is about to change: as it is now, once per frame (its first change counts). */
    void space(BoardSpaceBlockEntity space) {
        JournalFrame frame = current();
        if (frame != null && !frame.spaces.containsKey(space.getPos())) frame.spaces.put(space.getPos().toImmutable(), space.journalState());
    }

    /**
     * Takes out the frames from the newest down to the {@code count}-th newest: they are being undone, never twice.
     *
     * @return them, newest first
     */
    public List<JournalFrame> takeLast(int count) {
        List<JournalFrame> taken = new ArrayList<>();
        for (int i = 0; i < count && !frames.isEmpty(); i++) taken.add(frames.removeLast());
        return taken;
    }

    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        if (frames.isEmpty()) return;
        NbtList list = new NbtList();
        for (JournalFrame frame : frames) list.add(frame.toNbt(registries));
        nbt.put(KEY, list);
    }

    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        frames.clear();
        for (NbtElement element : nbt.getList(KEY, NbtElement.COMPOUND_TYPE)) {
            JournalFrame frame = JournalFrame.fromNbt((NbtCompound) element, registries);
            if (frame != null) frames.add(frame);
        }
    }
}
