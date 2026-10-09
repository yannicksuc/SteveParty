package fr.lordfinn.steveparty.entities.custom;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Mulas on their way to be reborn after bursting (one list per dimension, saved with the world): the whole Mula (its
 * NBT: colour, owner, name...) and where and when it comes back.
 * <p>
 * Once its shooting star has landed (the due time), it is reborn as soon as its chunk is loaded: checked once a second
 * over this (almost always empty) list, a map lookup per pending Mula, nothing loaded or generated for it. It keeps its
 * UUID, which rules out duplicates: nothing is spawned if a Mula with that UUID is already in the world, and an old
 * copy of a pending Mula that shows up (a chunk saved before its burst, after a crash) is removed on load.
 */
public class MulaRebirths extends PersistentState {
    private static final String ID = "steveparty_mula_rebirths";
    public static final Type<MulaRebirths> TYPE = new Type<>(MulaRebirths::new, MulaRebirths::fromNbt, null);
    /** Height above the ground where it reappears (blocks). */
    private static final double ABOVE_GROUND = 2.5;
    /** Status sent to the clients when it is reborn (sparkle ring, chime). */
    public static final byte REBORN_STATUS = 100;

    public record Entry(UUID id, int x, int z, double y, long due, NbtCompound mula) {
    }

    private final List<Entry> entries = new ArrayList<>();

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

    public void add(Entry entry) {
        entries.removeIf(e -> e.id().equals(entry.id()));
        entries.add(entry);
        markDirty();
    }

    public void remove(UUID id) {
        if (entries.removeIf(e -> e.id().equals(id))) markDirty();
    }

    public boolean isPending(UUID id) {
        for (Entry e : entries) if (e.id().equals(id)) return true;
        return false;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Rebirths whose time has come and whose chunk is loaded. */
    public void tick(ServerWorld world) {
        long now = world.getTime();
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (entry.due() > now) continue;
            WorldChunk chunk = world.getChunkManager().getWorldChunk(entry.x() >> 4, entry.z() >> 4);
            if (chunk == null) continue;
            it.remove();
            markDirty();
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
        for (Entry e : entries) {
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
            rebirths.entries.add(new Entry(c.getUuid("Id"), c.getInt("X"), c.getInt("Z"), c.getDouble("Y"),
                    c.getLong("Due"), c.getCompound("Mula")));
        }
        return rebirths;
    }
}
