package fr.lordfinn.steveparty.blocks.custom.PartyController.journal;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One step of a party in the turn journal (a pawn's turn, or a mini-game): the party as it stood when the step started
 * (its data, its star, its pawns) and what the game did during it (items moved, board spaces changed), to go back to
 * its start.
 */
public final class JournalFrame {
    /** Past this many transfers the step can no longer be undone (never half-undone). */
    static final int MAX_TRANSFERS = 2048;

    public enum Kind {TURN, MINI_GAME}

    /** Where a pawn stood and looked. */
    public record Pose(double x, double y, double z, float yaw, float pitch, float headYaw, float bodyYaw) {
        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putDouble("X", x);
            nbt.putDouble("Y", y);
            nbt.putDouble("Z", z);
            nbt.putFloat("Yaw", yaw);
            nbt.putFloat("Pitch", pitch);
            nbt.putFloat("HeadYaw", headYaw);
            nbt.putFloat("BodyYaw", bodyYaw);
            return nbt;
        }

        static Pose fromNbt(NbtCompound nbt) {
            return new Pose(nbt.getDouble("X"), nbt.getDouble("Y"), nbt.getDouble("Z"), nbt.getFloat("Yaw"), nbt.getFloat("Pitch"),
                    nbt.getFloat("HeadYaw"), nbt.getFloat("BodyYaw"));
        }
    }

    final Kind kind;
    final int stepIndex;
    /** The party data when the step started ({@code PartyData#toNbt}). */
    final NbtCompound party;
    final @Nullable BlockPos star;
    final Map<UUID, Pose> pawns;
    final List<JournalTransfer> transfers = new ArrayList<>();
    /** The board spaces changed during the step, as they were before their first change. */
    final Map<BlockPos, NbtCompound> spaces = new LinkedHashMap<>();
    /** Too much happened during it to keep it all: it can't be undone. */
    boolean overflow;

    JournalFrame(Kind kind, int stepIndex, NbtCompound party, @Nullable BlockPos star, Map<UUID, Pose> pawns) {
        this.kind = kind;
        this.stepIndex = stepIndex;
        this.party = party;
        this.star = star;
        this.pawns = pawns;
    }

    public Kind kind() {
        return kind;
    }

    public int stepIndex() {
        return stepIndex;
    }

    public List<JournalTransfer> transfers() {
        return transfers;
    }

    /** Too much happened during it to keep it all: it can't be undone. */
    public boolean isOverflowing() {
        return overflow;
    }

    void add(JournalTransfer transfer) {
        if (overflow) return;
        if (transfers.size() >= MAX_TRANSFERS) {
            overflow = true;
            transfers.clear();
            return;
        }
        // The same move twice in a row (a stack given in several pieces): one line
        if (!transfers.isEmpty()) {
            JournalTransfer last = transfers.getLast();
            if (last.from().equals(transfer.from()) && last.to().equals(transfer.to())
                    && net.minecraft.item.ItemStack.areItemsAndComponentsEqual(last.item(), transfer.item())) {
                transfers.set(transfers.size() - 1, new JournalTransfer(last.from(), last.to(), last.item(), last.count() + transfer.count()));
                return;
            }
        }
        transfers.add(transfer);
    }

    NbtCompound toNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Kind", kind.name());
        nbt.putInt("StepIndex", stepIndex);
        nbt.put("Party", party);
        if (star != null) nbt.putLong("Star", star.asLong());
        NbtList pawnList = new NbtList();
        pawns.forEach((id, pose) -> {
            NbtCompound entry = pose.toNbt();
            entry.putUuid("Id", id);
            pawnList.add(entry);
        });
        nbt.put("Pawns", pawnList);
        NbtList transferList = new NbtList();
        transfers.forEach(transfer -> transferList.add(transfer.toNbt(registries)));
        nbt.put("Transfers", transferList);
        NbtList spaceList = new NbtList();
        spaces.forEach((pos, state) -> {
            NbtCompound entry = state.copy();
            entry.putLong("JournalPos", pos.asLong());
            spaceList.add(entry);
        });
        nbt.put("Spaces", spaceList);
        if (overflow) nbt.putBoolean("Overflow", true);
        return nbt;
    }

    static @Nullable JournalFrame fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        Kind kind;
        try {
            kind = Kind.valueOf(nbt.getString("Kind"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Map<UUID, Pose> pawns = new LinkedHashMap<>();
        for (NbtElement element : nbt.getList("Pawns", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            if (entry.containsUuid("Id")) pawns.put(entry.getUuid("Id"), Pose.fromNbt(entry));
        }
        JournalFrame frame = new JournalFrame(kind, nbt.getInt("StepIndex"), nbt.getCompound("Party"),
                nbt.contains("Star") ? BlockPos.fromLong(nbt.getLong("Star")) : null, pawns);
        for (NbtElement element : nbt.getList("Transfers", NbtElement.COMPOUND_TYPE)) {
            JournalTransfer transfer = JournalTransfer.fromNbt((NbtCompound) element, registries);
            // An item no longer known: the step can't be undone whole, so not at all
            if (transfer == null) frame.overflow = true;
            else frame.transfers.add(transfer);
        }
        for (NbtElement element : nbt.getList("Spaces", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = ((NbtCompound) element).copy();
            BlockPos pos = BlockPos.fromLong(entry.getLong("JournalPos"));
            entry.remove("JournalPos");
            frame.spaces.put(pos, entry);
        }
        frame.overflow |= nbt.getBoolean("Overflow");
        return frame;
    }
}
