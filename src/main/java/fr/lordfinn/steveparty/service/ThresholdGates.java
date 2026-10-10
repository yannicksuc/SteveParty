package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Threshold barriers (a Threshold obstacle Cartridge in barrier mode, see ThresholdTileBehavior): the tokens that
 * cleared each one. In a party they are part of its data ({@link State}, saved with the Party Controller; a new party
 * starts with every barrier closed); outside a party they are kept until the server stops. A barrier opened for all is
 * open once anyone cleared it; else it is open for the tokens that cleared it. Server thread only.
 */
public final class ThresholdGates {
    /** Outside a party: who cleared each barrier, until the server stops. */
    private static final Map<GlobalPos, Set<UUID>> FREE = ServerMemory.forgetOnStop(new HashMap<>());
    /** How far from a barrier the party it belongs to is looked for, when no token says (its info panel, its look). */
    private static final int PARTY_RADIUS = 256;

    private ThresholdGates() {
    }

    /** The barriers of one party: the tokens that cleared each, by board space. */
    public static final class State {
        /** NBT key of the barriers in the party's data. */
        public static final String NBT_KEY = "ThresholdGates";
        private final Map<BlockPos, Set<UUID>> cleared = new LinkedHashMap<>();

        /** The tokens that cleared the barrier at {@code pos}, in order (read only). */
        public Set<UUID> clearedAt(BlockPos pos) {
            Set<UUID> tokens = cleared.get(pos);
            return tokens == null ? Set.of() : Collections.unmodifiableSet(tokens);
        }

        /** {@code token} cleared the barrier at {@code pos}; false if it already had. */
        public boolean add(BlockPos pos, UUID token) {
            return cleared.computeIfAbsent(pos.toImmutable(), p -> new LinkedHashSet<>()).add(token);
        }

        public boolean isEmpty() {
            return cleared.isEmpty();
        }

        public void reset() {
            cleared.clear();
        }

        /** Writes the barriers under {@link #NBT_KEY} (nothing if none was cleared). */
        public void writeNbt(NbtCompound nbt) {
            if (cleared.isEmpty()) return;
            NbtList list = new NbtList();
            cleared.forEach((pos, tokens) -> {
                NbtCompound entry = new NbtCompound();
                entry.putLong("Pos", pos.asLong());
                NbtList ids = new NbtList();
                tokens.forEach(id -> ids.add(NbtString.of(id.toString())));
                entry.put("Cleared", ids);
                list.add(entry);
            });
            nbt.put(NBT_KEY, list);
        }

        /** Reads the barriers written by {@link #writeNbt} (replaces the current ones). */
        public void readNbt(NbtCompound nbt) {
            cleared.clear();
            for (NbtElement element : nbt.getList(NBT_KEY, NbtElement.COMPOUND_TYPE)) {
                NbtCompound entry = (NbtCompound) element;
                Set<UUID> tokens = new LinkedHashSet<>();
                for (NbtElement id : entry.getList("Cleared", NbtElement.STRING_TYPE)) {
                    try {
                        tokens.add(UUID.fromString(id.asString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!tokens.isEmpty()) cleared.put(BlockPos.fromLong(entry.getLong("Pos")), tokens);
            }
        }
    }

    /** The running party of {@code token}, if any. */
    private static @Nullable PartyControllerEntity partyOf(MobEntity token) {
        return PartyControllerEntity.getRunningPartyOf(token.getUuid()).orElse(null);
    }

    /**
     * The running party a barrier belongs to, when no token says: the one in this world that has it cleared, else the
     * nearest one. Null: none (free play).
     */
    public static @Nullable PartyControllerEntity partyAt(ServerWorld world, BlockPos pos) {
        for (PartyControllerEntity party : PartyControllerEntity.getActivePartyControllers()) {
            if (party.getWorld() == world && !party.isRemoved() && party.getPartyData().isStarted()
                    && !party.getPartyData().getThresholdGates().clearedAt(pos).isEmpty()) return party;
        }
        return PartyControllerEntity.getClosestSteppablePartyControllerEntity(world, pos, PARTY_RADIUS, false).orElse(null);
    }

    private static Set<UUID> cleared(ServerWorld world, BlockPos pos, @Nullable PartyControllerEntity party) {
        if (party != null) return party.getPartyData().getThresholdGates().clearedAt(pos);
        Set<UUID> tokens = FREE.get(GlobalPos.create(world.getRegistryKey(), pos));
        return tokens == null ? Set.of() : Collections.unmodifiableSet(tokens);
    }

    /** The tokens that cleared the barrier at {@code pos}, in the party it belongs to (see {@link #partyAt}). */
    public static Set<UUID> cleared(ServerWorld world, BlockPos pos) {
        return cleared(world, pos, partyAt(world, pos));
    }

    /** True if the barrier at {@code pos} lets {@code token} through: opened for all ({@code shared}), or cleared by it. */
    public static boolean isOpenFor(ServerWorld world, BlockPos pos, MobEntity token, boolean shared) {
        Set<UUID> tokens = cleared(world, pos, partyOf(token));
        return shared ? !tokens.isEmpty() : tokens.contains(token.getUuid());
    }

    /** True if the barrier at {@code pos} was opened for all (someone cleared it), in the party it belongs to. */
    public static boolean isOpen(ServerWorld world, BlockPos pos) {
        return !cleared(world, pos).isEmpty();
    }

    /**
     * {@code token} cleared the barrier at {@code pos}.
     *
     * @return true if it is the first token to clear it (a barrier opened for all opens now)
     */
    public static boolean clear(ServerWorld world, BlockPos pos, MobEntity token) {
        PartyControllerEntity party = partyOf(token);
        boolean first = cleared(world, pos, party).isEmpty();
        if (party != null) {
            party.getPartyData().getThresholdGates().add(pos, token.getUuid());
            party.markDirty();
        } else {
            FREE.computeIfAbsent(GlobalPos.create(world.getRegistryKey(), pos.toImmutable()), p -> new LinkedHashSet<>())
                    .add(token.getUuid());
        }
        return first;
    }
}
