package fr.lordfinn.steveparty.entities.custom;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mulas on their way to be reborn after bursting (one list per dimension, saved with the world): the whole Mula (its
 * NBT: colour, owner, name...) and where and when it comes back.
 * <p>
 * Once its shooting star has landed (the due time), it is reborn as soon as its chunk is loaded: checked once a second
 * over this (almost always empty) list, a map lookup per pending Mula, nothing loaded or generated for it. It keeps its
 * UUID, which rules out duplicates: nothing is spawned if a Mula with that UUID is already in the world, and an old
 * copy of a pending Mula that shows up (a chunk saved before its burst, after a crash) is removed on load.
 * <p>
 * Bounded, so that a storm of bursts can never flood the world back: at most {@value #MAX_PENDING} Mulas wait (a wild
 * one bursting beyond that is not reborn: its star is its end), at most {@value #MAX_PER_CHECK} come back per second,
 * and a wild one whose chunk stayed unloaded {@value #WILD_EXPIRY_TICKS} ticks after its due time is forgotten. A Mula
 * that is someone's (tamed or named) is always kept. {@code /mula rebirths clear} empties the list
 * (after a {@code /kill}, nothing comes back).
 */
public class MulaRebirths extends PersistentState {
    private static final String ID = "steveparty_mula_rebirths";
    public static final Type<MulaRebirths> TYPE = new Type<>(MulaRebirths::new, MulaRebirths::fromNbt, null);
    /** Height above the ground where it reappears (blocks). */
    private static final double ABOVE_GROUND = 2.5;
    /** Status sent to the clients when it is reborn (sparkle ring, chime). */
    public static final byte REBORN_STATUS = 100;
    /** Most Mulas waiting to be reborn per dimension (someone's Mulas are always taken in, even beyond). */
    public static final int MAX_PENDING = 256;
    /** Most Mulas reborn per check (once a second): a long list comes back as a trickle, never as a wave. */
    public static final int MAX_PER_CHECK = 4;
    /** A wild Mula whose chunk was not loaded this long after its due time is forgotten (a Minecraft day). */
    public static final long WILD_EXPIRY_TICKS = 24000;

    public record Entry(UUID id, int x, int z, double y, long due, NbtCompound mula) {
    }

    /** By Mula id, in the order they burst. */
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 == 7) {
                MulaRebirths rebirths = peek(world);
                if (rebirths != null && !rebirths.entries.isEmpty()) rebirths.tick(world);
            }
        });
        // an old copy of a Mula that is on its way to be reborn (crash between saves): the pending one is the real one
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof MulaEntity) {
                MulaRebirths rebirths = peek(world);
                // removed right after (not in the middle of loading it)
                if (rebirths != null && rebirths.isPending(entity.getUuid())) world.getServer().execute(entity::discard);
            }
        });
    }

    public static MulaRebirths get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    /** The list if there is one (loaded or on disk), without creating an empty one. */
    private static @Nullable MulaRebirths peek(ServerWorld world) {
        return world.getPersistentStateManager().get(TYPE, ID);
    }

    /**
     * Records a Mula to be reborn. @return false when the list is full and it is a wild Mula: it is not reborn.
     */
    public boolean add(Entry entry) {
        boolean replaced = entries.remove(entry.id()) != null;
        if (!replaced && entries.size() >= MAX_PENDING && !isKept(entry.mula())) return false;
        entries.put(entry.id(), entry);
        markDirty();
        return true;
    }

    public void remove(UUID id) {
        if (entries.remove(id) != null) markDirty();
    }

    /** Forgets every pending rebirth (none of them comes back). @return how many there were. */
    public int clear() {
        int n = entries.size();
        if (n > 0) {
            entries.clear();
            markDirty();
        }
        return n;
    }

    public int size() {
        return entries.size();
    }

    public boolean isPending(UUID id) {
        return entries.containsKey(id);
    }

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    /** Someone's Mula (tamed or named): never dropped from the list. */
    public static boolean isKept(NbtCompound mula) {
        return mula.containsUuid("Owner") || mula.contains("CustomName");
    }

    /**
     * Rebirths whose time has come and whose chunk is loaded, at most {@value #MAX_PER_CHECK}; wild Mulas long overdue in
     * an unloaded chunk are forgotten.
     */
    public void tick(ServerWorld world) {
        long now = world.getTime();
        int reborn = 0;
        for (Iterator<Entry> it = entries.values().iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (entry.due() > now) continue;
            WorldChunk chunk = world.getChunkManager().getWorldChunk(entry.x() >> 4, entry.z() >> 4);
            if (chunk == null) {
                if (now - entry.due() > WILD_EXPIRY_TICKS && !isKept(entry.mula())) {
                    it.remove();
                    markDirty();
                }
                continue;
            }
            if (reborn >= MAX_PER_CHECK) continue; // the next ones next second
            it.remove();
            markDirty();
            reborn++;
            rebirth(world, entry);
        }
    }

    private static void rebirth(ServerWorld world, Entry entry) {
        if (world.getEntity(entry.id()) != null) return; // already there
        Entity entity = EntityType.getEntityFromNbt(entry.mula(), world).orElse(null);
        if (!(entity instanceof MulaEntity mula)) return;
        double y = entry.y();
        if (!world.getDimension().hasCeiling()) {
            int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, entry.x(), entry.z());
            if (top > world.getBottomY()) y = top + ABOVE_GROUND;
        }
        y = Math.min(y, world.getTopY() - 3);
        mula.refreshPositionAndAngles(entry.x() + 0.5, y, entry.z() + 0.5, mula.getYaw(), 0);
        // only in free space (leaves, a cave ceiling...): up to 16 blocks higher
        for (int i = 0; i < 32 && !world.isSpaceEmpty(mula); i++) {
            mula.refreshPositionAndAngles(mula.getX(), mula.getY() + 0.5, mula.getZ(), mula.getYaw(), 0);
        }
        mula.setVelocity(Vec3d.ZERO);
        mula.onReborn();
        if (world.spawnEntity(mula)) world.sendEntityStatus(mula, REBORN_STATUS);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (Entry e : entries.values()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("Id", e.id());
            c.putInt("X", e.x());
            c.putInt("Z", e.z());
            c.putDouble("Y", e.y());
            c.putLong("Due", e.due());
            c.put("Mula", e.mula());
            list.add(c);
        }
        nbt.put("Pending", list);
        return nbt;
    }

    public static MulaRebirths fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        MulaRebirths rebirths = new MulaRebirths();
        NbtList list = nbt.getList("Pending", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            if (!c.containsUuid("Id")) continue;
            UUID id = c.getUuid("Id");
            rebirths.entries.put(id, new Entry(id, c.getInt("X"), c.getInt("Z"), c.getDouble("Y"),
                    c.getLong("Due"), c.getCompound("Mula")));
        }
        return rebirths;
    }
}
