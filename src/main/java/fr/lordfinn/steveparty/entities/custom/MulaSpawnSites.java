package fr.lordfinn.steveparty.entities.custom;

import fr.lordfinn.steveparty.entities.ModEntities;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where Mulas came to the world after an ephemeride (MulaEphemeride), one list per dimension, saved with the world:
 * position, time, colours, and whether they have appeared yet. A group appears as soon as its chunk is loaded (checked
 * once a second over the pending sites, nothing loaded or generated for it), once.
 * <p>
 * Kept after the Mulas appeared, for what comes later: a structure generated at the site, a tool finding the nearest
 * one ({@link #nearest}).
 */
public class MulaSpawnSites extends PersistentState {
    private static final String ID = "steveparty_mula_spawn_sites";
    public static final Type<MulaSpawnSites> TYPE = new Type<>(MulaSpawnSites::new, MulaSpawnSites::fromNbt, null);
    /** At most this many sites waiting for their chunk (older events beyond it are not recorded). */
    public static final int MAX_PENDING = 16;

    /** One site: its column (y found when the Mulas appear, then stored), the game time, the colours (variant ids). */
    public static final class Site {
        public final int id;
        public final BlockPos pos;
        public final long time;
        public final int[] colours;
        public boolean spawned;

        public Site(int id, BlockPos pos, long time, int[] colours, boolean spawned) {
            this.id = id;
            this.pos = pos;
            this.time = time;
            this.colours = colours;
            this.spawned = spawned;
        }
    }

    private final List<Site> sites = new ArrayList<>();
    private int nextId = 1;

    public static MulaSpawnSites get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    static @Nullable MulaSpawnSites peek(ServerWorld world) {
        return world.getPersistentStateManager().get(TYPE, ID);
    }

    /** Records a site (null when too many are already waiting). */
    public @Nullable Site add(BlockPos pos, long time, int[] colours) {
        if (pendingCount() >= MAX_PENDING) return null;
        Site site = new Site(nextId++, pos.toImmutable(), time, colours.clone(), false);
        sites.add(site);
        markDirty();
        return site;
    }

    public int pendingCount() {
        int n = 0;
        for (Site s : sites) if (!s.spawned) n++;
        return n;
    }

    public List<Site> sites() {
        return List.copyOf(sites);
    }

    /** The nearest site (horizontally) to a position, spawned ones included or not. */
    public Optional<Site> nearest(BlockPos from, boolean includeSpawned) {
        Site best = null;
        double bestSq = Double.MAX_VALUE;
        for (Site s : sites) {
            if (s.spawned && !includeSpawned) continue;
            double dx = s.pos.getX() - from.getX(), dz = s.pos.getZ() - from.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestSq) {
                bestSq = d;
                best = s;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Pending sites whose chunk is loaded: their Mulas appear there (once). */
    public void tick(ServerWorld world) {
        for (Site s : sites) {
            if (s.spawned || world.getChunkManager().getWorldChunk(s.pos.getX() >> 4, s.pos.getZ() >> 4) == null) continue;
            s.spawned = true;
            markDirty();
            spawn(world, s);
        }
    }

    private static void spawn(ServerWorld world, Site site) {
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, site.pos.getX(), site.pos.getZ());
        double y = (top > world.getBottomY() ? top : site.pos.getY()) + 3;
        for (int i = 0; i < site.colours.length; i++) {
            MulaEntity mula = ModEntities.MULA_ENTITY.create(world, SpawnReason.EVENT);
            if (mula == null) continue;
            double a = i * 2.39996;
            mula.refreshPositionAndAngles(site.pos.getX() + 0.5 + Math.cos(a) * 1.5, y + (i % 3) * 0.6,
                    site.pos.getZ() + 0.5 + Math.sin(a) * 1.5, world.random.nextFloat() * 360, 0);
            mula.initialize(world, world.getLocalDifficulty(mula.getBlockPos()), SpawnReason.EVENT, null);
            mula.setVariant(MulaEntity.MulaVariant.byId(site.colours[i]));
            for (int k = 0; k < 16 && !world.isSpaceEmpty(mula); k++) {
                mula.refreshPositionAndAngles(mula.getX(), mula.getY() + 1, mula.getZ(), mula.getYaw(), 0);
            }
            world.spawnEntity(mula);
        }
        fr.lordfinn.steveparty.Steveparty.LOGGER.info("{} Mulas came down from the ephemeride at {} {} {}",
                site.colours.length, site.pos.getX(), (int) y, site.pos.getZ());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (Site s : sites) {
            NbtCompound c = new NbtCompound();
            c.putInt("Id", s.id);
            c.putIntArray("Pos", new int[]{s.pos.getX(), s.pos.getY(), s.pos.getZ()});
            c.putLong("Time", s.time);
            c.putIntArray("Colours", s.colours);
            c.putBoolean("Spawned", s.spawned);
            list.add(c);
        }
        nbt.put("Sites", list);
        nbt.putInt("NextId", nextId);
        return nbt;
    }

    public static MulaSpawnSites fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        MulaSpawnSites state = new MulaSpawnSites();
        NbtList list = nbt.getList("Sites", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            int[] p = c.getIntArray("Pos");
            if (p.length != 3) continue;
            state.sites.add(new Site(c.getInt("Id"), new BlockPos(p[0], p[1], p[2]), c.getLong("Time"),
                    c.getIntArray("Colours"), c.getBoolean("Spawned")));
        }
        state.nextId = Math.max(nbt.getInt("NextId"), state.sites.size() + 1);
        return state;
    }
}
