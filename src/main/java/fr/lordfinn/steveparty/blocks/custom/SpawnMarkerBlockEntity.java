package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Spawn Bubble's settings (its menu): when its mob shows ({@link #isResident}), how high above the plate its bubble
 * floats and its mob appears ({@link #getLift}: the plate may be hidden under the floor or in a wall), and the board
 * space linked to it last ({@link #getOwner}).
 * <p>
 * The cartridges know their bubble, not the reverse: the server tells it which spaces link it and the kinds of mob
 * they summon ({@link #getLinkedMobs}, worked out by {@link fr.lordfinn.steveparty.service.MarkerMobs} when a link is
 * made or cut, a space's cartridges change or a space breaks, when it loads, and again every second), and whether a
 * party runs on their board ({@link #isPartyRunning}). Seen by the clients (its menu, its bubble and the mob inside,
 * the tile's info).
 */
public class SpawnMarkerBlockEntity extends SyncedBlockEntity {
    /** The lift, in half blocks: from -4 to +8 blocks. */
    public static final int MIN_LIFT = -8, MAX_LIFT = 16;
    /** The synced block event: placed, its bubble comes out of the plate. */
    public static final int INFLATE = 1;
    private boolean resident;
    /** In half blocks. */
    private int lift;
    private @Nullable BlockPos owner;
    /** The spaces linking it and the kinds of mob their cartridges linked to it summon (a space unloaded keeps what it had). */
    private final Map<BlockPos, List<EntityType<?>>> links = new LinkedHashMap<>();
    /** The kinds of mob of {@link #links}, each once. */
    private List<EntityType<?>> mobs = List.of();
    private boolean party;
    /** Client: the world time its bubble started inflating ({@link Long#MIN_VALUE}: long ago, full size). */
    public long inflatedAt = Long.MIN_VALUE;
    /** Client: when its renderer first saw it (its first ticks it waits for the inflating event, never drawn whole). */
    public long seenAt = Long.MIN_VALUE;

    public SpawnMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPAWN_MARKER_ENTITY, pos, state);
    }

    /**
     * Its mob shows all the party long (« always visible »: it appears on the bubble when the party starts, waits
     * there, plays its show when a token lands on its space, comes back, goes when the party ends); false (the
     * default): only when a token lands on its space.
     */
    public boolean isResident() {
        return resident;
    }

    public void setResident(boolean resident) {
        if (this.resident == resident) return;
        this.resident = resident;
        changed();
    }

    /** How many blocks above the plate its bubble floats and its mob appears (-4 to +8, by half blocks). */
    public double getLift() {
        return lift / 2.0;
    }

    /** The lift in half blocks. */
    public int getLiftSteps() {
        return lift;
    }

    /** Sets the lift, in half blocks (clamped to {@link #MIN_LIFT}..{@link #MAX_LIFT}). */
    public void setLiftSteps(int steps) {
        int clamped = MathHelper.clamp(steps, MIN_LIFT, MAX_LIFT);
        if (clamped == lift) return;
        lift = clamped;
        changed();
    }

    /** The board space whose cartridge links it (the last one linked to it), null for none known. */
    public @Nullable BlockPos getOwner() {
        return owner;
    }

    public void setOwner(@Nullable BlockPos owner) {
        this.owner = owner == null ? null : owner.toImmutable();
        changed();
    }

    /** The spaces known to link it, with the kinds of mob they summon. */
    public Map<BlockPos, List<EntityType<?>>> getLinks() {
        return Collections.unmodifiableMap(links);
    }

    /** The spaces to look at to know what links it: those known to, and its owner. */
    public Set<BlockPos> getLinkers() {
        Set<BlockPos> linkers = new LinkedHashSet<>(links.keySet());
        if (owner != null) linkers.add(owner);
        return linkers;
    }

    /**
     * The kinds of mob summoned by the spaces linked to it (each once, in the order of the spaces), empty for none: an
     * arrow in its bubble then, else a miniature of the mob (taking turns when there are several).
     */
    public List<EntityType<?>> getLinkedMobs() {
        return mobs;
    }

    /** Whether a party runs on the board of a space linked to it (its bubble then shows no miniature). */
    public boolean isPartyRunning() {
        return party;
    }

    /** Server: the space at {@code tile} links it (looked at by {@link fr.lordfinn.steveparty.service.MarkerMobs}). */
    public void addLinker(BlockPos tile) {
        if (links.containsKey(tile)) return;
        links.put(tile.toImmutable(), List.of());
        markDirty();
    }

    /** Server: what links it now, and whether a party runs there; sent to the clients if it changed. */
    public void setLinks(Map<BlockPos, List<EntityType<?>>> now, boolean partyRunning) {
        if (links.equals(now) && party == partyRunning) return;
        links.clear();
        now.forEach((tile, kinds) -> links.put(tile.toImmutable(), List.copyOf(kinds)));
        party = partyRunning;
        updateMobs();
        changed();
    }

    private void updateMobs() {
        Set<EntityType<?>> kinds = new LinkedHashSet<>();
        links.values().forEach(kinds::addAll);
        mobs = List.copyOf(kinds);
    }

    /** {@link #INFLATE}: its bubble comes out of the plate (clients). */
    @Override
    public boolean onSyncedBlockEvent(int type, int data) {
        if (type != INFLATE) return super.onSyncedBlockEvent(type, data);
        if (world != null && world.isClient) inflatedAt = world.getTime();
        return true;
    }

    private void changed() {
        markDirty();
        syncToClients();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (resident) nbt.putBoolean("Resident", true);
        if (lift != 0) nbt.putInt("Lift", lift);
        if (owner != null) nbt.putLong("Owner", owner.asLong());
        if (!links.isEmpty()) {
            NbtList list = new NbtList();
            links.forEach((tile, kinds) -> {
                NbtCompound link = new NbtCompound();
                link.putLong("Pos", tile.asLong());
                NbtList ids = new NbtList();
                for (EntityType<?> kind : kinds) ids.add(NbtString.of(EntityType.getId(kind).toString()));
                link.put("Mobs", ids);
                list.add(link);
            });
            nbt.put("Links", list);
        }
        if (party) nbt.putBoolean("Party", true);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        resident = nbt.getBoolean("Resident");
        lift = MathHelper.clamp(nbt.getInt("Lift"), MIN_LIFT, MAX_LIFT);
        owner = nbt.contains("Owner") ? BlockPos.fromLong(nbt.getLong("Owner")) : null;
        links.clear();
        for (NbtElement element : nbt.getList("Links", NbtElement.COMPOUND_TYPE)) {
            NbtCompound link = (NbtCompound) element;
            List<EntityType<?>> kinds = new ArrayList<>();
            for (NbtElement id : link.getList("Mobs", NbtElement.STRING_TYPE)) {
                Identifier identifier = Identifier.tryParse(id.asString());
                if (identifier != null) Registries.ENTITY_TYPE.getOrEmpty(identifier).ifPresent(kinds::add);
            }
            links.put(BlockPos.fromLong(link.getLong("Pos")), List.copyOf(kinds));
        }
        party = nbt.getBoolean("Party");
        updateMobs();
    }
}
