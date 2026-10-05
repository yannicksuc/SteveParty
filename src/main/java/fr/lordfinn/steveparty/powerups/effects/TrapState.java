package fr.lordfinn.steveparty.powerups.effects;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The hidden traps of one party (see {@link TrapEffect}): at most one per board space, keyed by the board space's
 * position (a large tile: its main block). Part of the party's data ({@code PartyData}), saved with the Party Controller
 * and never sent to the clients; a new party starts without traps.
 */
public final class TrapState {
    /** NBT key of the traps in the party's data. */
    public static final String NBT_KEY = "PowerupTraps";

    /**
     * A trap waiting on a board space.
     *
     * @param placer      the player who set it: they get the coins, and never spring it
     * @param placerToken their token when they set it (null if unknown): it never springs it either
     */
    public record Trap(UUID placer, @Nullable UUID placerToken) {
        /** Whether the trap is this player's or this token's own (it does not spring for them). */
        public boolean isOwnedBy(@Nullable UUID player, UUID token) {
            return placer.equals(player) || token.equals(placerToken);
        }
    }

    private final Map<BlockPos, Trap> traps = new LinkedHashMap<>();

    /** The trap on the board space at {@code pos}, or null. */
    public @Nullable Trap get(BlockPos pos) {
        return traps.get(pos);
    }

    /**
     * Sets a trap on the board space at {@code pos}: one trap per space, a new one replaces the one there.
     *
     * @return the trap it replaced, or null
     */
    public @Nullable Trap set(BlockPos pos, Trap trap) {
        return traps.put(pos.toImmutable(), trap);
    }

    /** Removes the trap of the board space at {@code pos}; returns it, or null if there was none. */
    public @Nullable Trap remove(BlockPos pos) {
        return traps.remove(pos);
    }

    /** All the traps, by board space (read only). */
    public Map<BlockPos, Trap> all() {
        return Collections.unmodifiableMap(traps);
    }

    public boolean isEmpty() {
        return traps.isEmpty();
    }

    public void clear() {
        traps.clear();
    }

    /** Writes the traps under {@link #NBT_KEY} (nothing without trap). */
    public void writeNbt(NbtCompound nbt) {
        if (traps.isEmpty()) return;
        NbtList list = new NbtList();
        traps.forEach((pos, trap) -> {
            NbtCompound entry = new NbtCompound();
            entry.putLong("Pos", pos.asLong());
            entry.putUuid("Placer", trap.placer());
            if (trap.placerToken() != null) entry.putUuid("Token", trap.placerToken());
            list.add(entry);
        });
        nbt.put(NBT_KEY, list);
    }

    /** Reads the traps written by {@link #writeNbt} (replaces the current ones). */
    public void readNbt(NbtCompound nbt) {
        traps.clear();
        for (NbtElement element : nbt.getList(NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            if (!entry.containsUuid("Placer")) continue;
            traps.put(BlockPos.fromLong(entry.getLong("Pos")),
                    new Trap(entry.getUuid("Placer"), entry.containsUuid("Token") ? entry.getUuid("Token") : null));
        }
    }
}
