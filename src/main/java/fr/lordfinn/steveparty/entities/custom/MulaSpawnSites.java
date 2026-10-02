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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Where Mulas came to the world after an ephemeride (MulaEphemeride), one list per dimension, saved with the world:
 * position, time, colours, and whether they have appeared yet. A group appears as soon as its chunk is loaded (checked
 * once a second over the pending sites, nothing loaded or generated for it), once.
 * <p>
 * Kept after the Mulas appeared, for what comes later: a structure generated at the site, a tool finding the nearest
 * one ({@link #nearest}).
 * <p>
 * Also kept here, per player: the sites he found at a Telescope (his guide stars) and those he has been to. Nothing a
 * player finds or visits changes a site or what another player sees.
 */
public class MulaSpawnSites extends PersistentState {
    private static final String ID = "steveparty_mula_spawn_sites";
    public static final Type<MulaSpawnSites> TYPE = new Type<>(MulaSpawnSites::new, MulaSpawnSites::fromNbt, null);
    /** At most this many sites waiting for their chunk (older events beyond it are not recorded). */
    public static final int MAX_PENDING = 16;

    /**
     * One site: its column (y found when the Mulas appear, then stored), the game time, the day of its night (the
     * world's day count), the colours (variant ids).
     */
    public static final class Site {
        public final int id;
        public final BlockPos pos;
        public final long time;
        public final long day;
        public final int[] colours;
        public boolean spawned;

        public Site(int id, BlockPos pos, long time, long day, int[] colours, boolean spawned) {
            this.id = id;
            this.pos = pos;
            this.time = time;
            this.day = day;
            this.colours = colours;
            this.spawned = spawned;
        }
    }

    /** What one player knows of the sites: those found at a Telescope (guide stars), those he has been to. */
    private static final class Knowledge {
        final Set<Integer> found = new TreeSet<>();
        final Set<Integer> visited = new TreeSet<>();
    }

    private final List<Site> sites = new ArrayList<>();
    private final Map<UUID, Knowledge> players = new HashMap<>();
    private int nextId = 1;

    public static MulaSpawnSites get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    public static @Nullable MulaSpawnSites peek(ServerWorld world) {
        return world.getPersistentStateManager().get(TYPE, ID);
    }

    /** Records a site (null when too many are already waiting), its night being the day of that game time. */
    public @Nullable Site add(BlockPos pos, long time, int[] colours) {
        return add(pos, time, time / 24000L, colours);
    }

    /** Records a site of the night of that day (null when too many are already waiting). */
    public @Nullable Site add(BlockPos pos, long time, long day, int[] colours) {
        if (pendingCount() >= MAX_PENDING) return null;
        return record(pos, time, day, colours);
    }

    /**
     * Records a site whatever the number already waiting: one set by hand, not by an ephemeride (the cap of
     * {@link #add} only bounds what the events pile up; a world can be at it for good, its sites far from everyone).
     */
    public Site record(BlockPos pos, long time, long day, int[] colours) {
        Site site = new Site(nextId++, pos.toImmutable(), time, day, colours.clone(), false);
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

    public int siteCount() {
        return sites.size();
    }

    public @Nullable Site byId(int id) {
        for (Site s : sites) if (s.id == id) return s;
        return null;
    }

    /** Forgets a site, and what the players knew of it. */
    public boolean remove(int id) {
        if (!sites.removeIf(s -> s.id == id)) return false;
        for (Knowledge k : players.values()) {
            k.found.remove(id);
            k.visited.remove(id);
        }
        markDirty();
        return true;
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

    // ---------------------------------------------------------------- what each player knows

    public boolean hasFound(UUID player, int id) {
        Knowledge k = players.get(player);
        return k != null && k.found.contains(id);
    }

    public boolean hasVisited(UUID player, int id) {
        Knowledge k = players.get(player);
        return k != null && k.visited.contains(id);
    }

    /** The player found this site at a Telescope: his guide star (false if he already knew it). */
    public boolean markFound(UUID player, int id) {
        if (byId(id) == null || hasVisited(player, id)) return false;
        if (!players.computeIfAbsent(player, u -> new Knowledge()).found.add(id)) return false;
        markDirty();
        return true;
    }

    /** The player has been there: no guide star any more, and his Telescopes no longer show that night. */
    public boolean markVisited(UUID player, int id) {
        if (byId(id) == null) return false;
        Knowledge k = players.computeIfAbsent(player, u -> new Knowledge());
        boolean changed = k.visited.add(id);
        changed |= k.found.remove(id);
        if (changed) markDirty();
        return changed;
    }

    /**
     * The sites a player can still find from a place: within {@code range} blocks (horizontally), neither found nor
     * visited by him, the latest first, at most {@code max}. Only this list in memory is read: no chunk is touched.
     */
    public List<Site> unfoundNear(UUID player, BlockPos from, double range, int max) {
        Knowledge k = players.get(player);
        List<Site> out = new ArrayList<>();
        for (Site s : sites) {
            if (k != null && (k.found.contains(s.id) || k.visited.contains(s.id))) continue;
            double dx = s.pos.getX() - from.getX(), dz = s.pos.getZ() - from.getZ();
            if (dx * dx + dz * dz <= range * range) out.add(s);
        }
        out.sort(Comparator.comparingLong((Site s) -> s.time).thenComparingInt(s -> s.id).reversed());
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** The sites a player found and has not been to yet: his guide stars. */
    public List<Site> guides(UUID player) {
        Knowledge k = players.get(player);
        if (k == null || k.found.isEmpty()) return List.of();
        List<Site> out = new ArrayList<>();
        for (Site s : sites) if (k.found.contains(s.id)) out.add(s);
        return out;
    }

    /**
     * A player standing at {@code at}: every site within {@code reach} blocks (horizontally) is visited by him.
     *
     * @return true if one of his guide stars went out
     */
    public boolean visitAround(UUID player, BlockPos at, double reach) {
        boolean guideLost = false;
        for (Site s : sites) {
            double dx = s.pos.getX() - at.getX(), dz = s.pos.getZ() - at.getZ();
            if (dx * dx + dz * dz > reach * reach || hasVisited(player, s.id)) continue;
            guideLost |= hasFound(player, s.id);
            markVisited(player, s.id);
        }
        return guideLost;
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
            c.putLong("Day", s.day);
            c.putIntArray("Colours", s.colours);
            c.putBoolean("Spawned", s.spawned);
            list.add(c);
        }
        nbt.put("Sites", list);
        nbt.putInt("NextId", nextId);
        NbtList known = new NbtList();
        for (Map.Entry<UUID, Knowledge> e : players.entrySet()) {
            Knowledge k = e.getValue();
            if (k.found.isEmpty() && k.visited.isEmpty()) continue;
            NbtCompound c = new NbtCompound();
            c.putUuid("Player", e.getKey());
            c.putIntArray("Found", k.found.stream().mapToInt(Integer::intValue).toArray());
            c.putIntArray("Visited", k.visited.stream().mapToInt(Integer::intValue).toArray());
            known.add(c);
        }
        nbt.put("Players", known);
        return nbt;
    }

    public static MulaSpawnSites fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        MulaSpawnSites state = new MulaSpawnSites();
        NbtList list = nbt.getList("Sites", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            int[] p = c.getIntArray("Pos");
            if (p.length != 3) continue;
            long time = c.getLong("Time");
            state.sites.add(new Site(c.getInt("Id"), new BlockPos(p[0], p[1], p[2]), time,
                    c.contains("Day") ? c.getLong("Day") : time / 24000L, c.getIntArray("Colours"), c.getBoolean("Spawned")));
        }
        state.nextId = Math.max(nbt.getInt("NextId"), state.sites.size() + 1);
        NbtList known = nbt.getList("Players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < known.size(); i++) {
            NbtCompound c = known.getCompound(i);
            if (!c.containsUuid("Player")) continue;
            Knowledge k = new Knowledge();
            for (int id : c.getIntArray("Found")) k.found.add(id);
            for (int id : c.getIntArray("Visited")) k.visited.add(id);
            state.players.put(c.getUuid("Player"), k);
        }
        return state;
    }
}
