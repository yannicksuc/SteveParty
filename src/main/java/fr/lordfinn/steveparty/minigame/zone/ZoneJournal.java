package fr.lordfinn.steveparty.minigame.zone;

import fr.lordfinn.steveparty.Steveparty;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryEntryLookup;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The block each position of a zone held before a session first changed it: one entry per changed position, so
 * that the cost of a session follows what it changes, never the size of its zone.
 * <p>
 * A new entry costs a hash map insert and 13 bytes in a buffer (the position and the number its block state has in
 * the session: a state is spelled out once, the first time it is met). The buffer goes to the journal file at the
 * end of the tick, off the server thread ({@link ZoneStorage}).
 */
final class ZoneJournal {
    private static final int STATE = 1, CHANGE = 2;

    private final Long2ObjectOpenHashMap<BlockState> originals = new Long2ObjectOpenHashMap<>();
    private final Reference2IntOpenHashMap<BlockState> stateNumbers = new Reference2IntOpenHashMap<>();
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
    private final DataOutputStream out = new DataOutputStream(buffer);
    private final @Nullable Path file;
    private final int cap;
    private boolean full;

    /** @param file where the entries are written, null for a journal read back from its file */
    ZoneJournal(@Nullable Path file, int cap) {
        this.file = file;
        this.cap = cap;
        stateNumbers.defaultReturnValue(-1);
    }

    int size() {
        return originals.size();
    }

    /** @return true once an entry was refused because the journal holds all it may */
    boolean isFull() {
        return full;
    }

    boolean has(long pos) {
        return originals.containsKey(pos);
    }

    @Nullable BlockState original(long pos) {
        return originals.get(pos);
    }

    long[] positions() {
        return originals.keySet().toLongArray();
    }

    /**
     * The position {@code pos}, holding {@code old}, is about to change.
     *
     * @param unbounded the change is made by the engine itself: remembered whatever the cap
     * @param written   the session is going on: the entry goes to the file (not once the zone is being put back)
     * @return false if it is a new position and the journal is full: the change must not happen
     */
    boolean record(long pos, BlockState old, boolean unbounded, boolean written) {
        if (originals.containsKey(pos)) return true;
        if (!unbounded && originals.size() >= cap) {
            full = true;
            return false;
        }
        originals.put(pos, old);
        if (written && file != null) write(pos, old);
        return true;
    }

    private void write(long pos, BlockState old) {
        try {
            int number = stateNumbers.getInt(old);
            if (number < 0) {
                number = stateNumbers.size();
                stateNumbers.put(old, number);
                out.writeByte(STATE);
                NbtIo.writeCompound(NbtHelper.fromBlockState(old), out);
            }
            out.writeByte(CHANGE);
            out.writeLong(pos);
            out.writeInt(number);
        } catch (IOException e) {
            // a byte array never fails
            throw new IllegalStateException(e);
        }
    }

    /** What was journaled since the last call goes to the file. */
    void flush() {
        if (file == null || buffer.size() == 0) return;
        ZoneStorage.append(file, buffer.toByteArray());
        buffer.reset();
    }

    /** Reads a journal file back; a last entry cut short by a crash is left out. */
    static ZoneJournal read(byte[] bytes, RegistryEntryLookup<Block> blocks) {
        ZoneJournal journal = new ZoneJournal(null, Integer.MAX_VALUE);
        List<BlockState> states = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            while (true) {
                int kind = in.read();
                if (kind < 0) break;
                if (kind == STATE) {
                    NbtCompound nbt = NbtIo.readCompound(in, NbtSizeTracker.ofUnlimitedBytes());
                    states.add(NbtHelper.toBlockState(blocks, nbt));
                } else if (kind == CHANGE) {
                    long pos = in.readLong();
                    int number = in.readInt();
                    if (number < 0 || number >= states.size()) break;
                    journal.originals.putIfAbsent(pos, states.get(number));
                } else break;
            }
        } catch (EOFException e) {
            // the server stopped while this entry was being written: the block it is about was not saved changed
        } catch (IOException | RuntimeException e) {
            Steveparty.LOGGER.error("A mini-game bubble journal is damaged: what could be read of it is restored", e);
        }
        return journal;
    }
}
