package fr.lordfinn.steveparty.minigame.zone;

import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.Steveparty;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.tick.ChunkTickScheduler;
import net.minecraft.world.tick.OrderedTick;
import net.minecraft.world.tick.TickPriority;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The bubble of one mini-game session, as {@link ZoneBubbles#begin} hands it out: a zone played in place, in the
 * real world, that is given back as it was.
 * <ul>
 *   <li><b>the zone</b>: the block every changed position held is journaled at its first change ({@link ZoneJournal});
 *   the block entities and the entities (players aside) are remembered whole when the session begins. {@link #end()}
 *   puts all of it back: blocks first (no drops, neighbours told nothing), each with its block entity, then the
 *   block entities that changed on their own, then the entities, with their UUIDs;</li>
 *   <li><b>the players</b>: participants and spectators leave what they own at the door ({@link ZonePlayerStash}) and
 *   get it back when they leave the session, whatever the way;</li>
 *   <li><b>the border</b>: nothing crosses it while the session lasts ({@link ZoneBorder}, {@link ZonePlayerRules}).</li>
 * </ul>
 * A bubble that could not begin ({@link #refusal()}) does nothing at all, whatever is asked of it.
 * <p>
 * Why the block entities are read when the session begins, and not at their first change like the blocks: nothing
 * tells before a block entity changes. Stacks are grown and shrunk in place (a furnace burning, a shift-click),
 * machines change on their own every tick, and {@code markDirty} comes after the change. Reading them all at the
 * start is the only way that misses nothing; it is bounded by the zone ({@code miniGameBubbleMaxBlockEntities}) and
 * costs microseconds per block entity. The same goes for entities.
 */
public final class ZoneBubble {
    /** How a bubble is doing. */
    public enum State {
        /** The session is going on. */
        ACTIVE,
        /** The session is over, the zone is being put back (over several ticks if much changed): nobody gets in. */
        RESTORING,
        /** The zone is as it was: the bubble is gone. */
        ENDED,
        /** The session could not begin: see {@link #refusal()}. */
        REFUSED
    }

    /** Why a bubble did not begin. */
    public enum Refusal {
        NONE, DISABLED, NO_WORLD, TOO_BIG, OVERLAP, TOO_MANY_BLOCK_ENTITIES, TOO_MANY_ENTITIES,
        /** The zone holds a block the server does not allow in a zone ({@link ZoneForbidden}). */
        FORBIDDEN_BLOCK,
        /** The zone holds an entity the server does not allow in a zone. */
        FORBIDDEN_ENTITY
    }

    /** @param adventure participants play in adventure mode (their own mode is given back with their inventory) */
    public record Options(boolean adventure) {
        public static final Options DEFAULT = new Options(false);
    }

    /** Someone of the session. */
    static final class Member {
        boolean participant;
        /** Out of the zone with the mod's leave (brought there by it, or not in yet): hands tied until back in. */
        boolean away;
        boolean hasLast;
        double lastX, lastY, lastZ;
        float lastYaw, lastPitch;

        Member(boolean participant) {
            this.participant = participant;
        }
    }

    /**
     * A zone's chunks stay loaded and ticking for its session, like forced chunks, without being ones. One ticket per
     * session: two zones side by side share chunks, and the end of one must not let go of the other's.
     */
    private static final ChunkTicketType<UUID> TICKET = ChunkTicketType.create("steveparty_zone_bubble", UUID::compareTo);
    private static final int TICKET_RADIUS = 2;
    /** Blocks are set as they were: clients told, no neighbour update, no shape update, no drops. */
    private static final int RESTORE_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
    /** Putting a block back costs this much of the per-tick budget, looking at one that is already right costs 1. */
    private static final int FIX_COST = 8;
    /** Looking at a block entity (it is read whole to tell whether it changed) costs as much as 8 blocks. */
    private static final int BLOCK_ENTITY_COST = 64;
    private static final int MAX_PASSES = 6;
    /** Players this close to a zone have what they opened closed when its session begins. */
    private static final double SCREEN_REACH = 10;
    /** How often the members are told again that the journal is full. */
    private static final int JOURNAL_FULL_WARN_INTERVAL_TICKS = 200;
    /** How close to the far faces of the zone an entity is stopped: the block it stands in is still of the zone. */
    private static final double EDGE = 1.0E-4;
    /** How long a zone put back after a crash waits for the entities of its chunks to be read from disk. */
    private static final int MAX_ENTITY_WAIT_TICKS = 1200;

    private final UUID sessionId;
    private final MiniGameZone zone;
    private final Options options;
    private final Refusal refusal;
    /** What exactly kept the bubble from beginning (the forbidden block or entity and where it is), null when nothing more is known. */
    private @Nullable Text refusalDetail;
    final @Nullable ServerWorld world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    private State state;
    private ZoneJournal journal = new ZoneJournal(null, 0);
    private Long2ObjectOpenHashMap<NbtCompound> blockEntities = new Long2ObjectOpenHashMap<>();
    private List<NbtCompound> entities = new ArrayList<>();
    /**
     * The chunks of the zone whose entities were not read from disk yet when the session began: what they hold is
     * remembered once they are; until then the session touched none of it, so it is never wiped (not even after a crash).
     */
    private final LongOpenHashSet unseen = new LongOpenHashSet();
    /** The entities the session spawned in the chunks of {@link #unseen}: not remembered with what is read from disk there. */
    private final Set<UUID> spawned = new HashSet<>();
    /** The block and fluid ticks the zone was waiting for when the session began: they wait again once it is put back. */
    private NbtList ticks = new NbtList();
    /** Read back from its files after a crash: what the zone holds now is the session's, not what it was. */
    private boolean recoveredFromFiles;
    private int entityWait;
    private @Nullable Path directory;
    private long lastFullWarning = -1;

    /** The steps of a restoration, in order. */
    private enum Phase {
        BLOCKS, BLOCK_ENTITIES, VERIFY, ENTITIES
    }

    // where the restoration is, when it takes several ticks
    private Phase phase = Phase.BLOCKS;
    private int pass;
    private long[] order = new long[0];
    private int cursor;
    private int fixed;
    /** The block entities made again along with their block. */
    private final LongOpenHashSet remade = new LongOpenHashSet();

    private ZoneBubble(UUID sessionId, MiniGameZone zone, Options options, @Nullable ServerWorld world, Refusal refusal) {
        this.sessionId = sessionId;
        this.zone = zone;
        this.options = options;
        this.world = world;
        this.refusal = refusal;
        this.state = refusal == Refusal.NONE ? State.ACTIVE : State.REFUSED;
        BlockBox box = zone.box();
        minX = box.getMinX();
        minY = box.getMinY();
        minZ = box.getMinZ();
        maxX = box.getMaxX();
        maxY = box.getMaxY();
        maxZ = box.getMaxZ();
    }

    static ZoneBubble refused(UUID sessionId, MiniGameZone zone, Refusal refusal) {
        return new ZoneBubble(sessionId, zone, Options.DEFAULT, null, refusal);
    }

    // ------------------------------------------------------------------ what a session asks

    public UUID sessionId() {
        return sessionId;
    }

    public MiniGameZone zone() {
        return zone;
    }

    public State state() {
        return state;
    }

    /** @return true while the session is going on (false once ended, and for a bubble that never began) */
    public boolean isActive() {
        return state == State.ACTIVE;
    }

    /** @return true while the zone is put back: the session is over, nobody may come in yet */
    public boolean isRestoring() {
        return state == State.RESTORING;
    }

    /** @return why the bubble did not begin, {@link Refusal#NONE} if it did */
    public Refusal refusal() {
        return refusal;
    }

    /** Why the bubble did not begin, for its players: null when it did, or when the feature is off. */
    public @Nullable Text refusalText() {
        return refusalDetail != null ? refusalDetail : ZoneBubbles.refusalText(refusal);
    }

    public boolean isMember(UUID player) {
        return members.containsKey(player);
    }

    public boolean isParticipant(UUID player) {
        Member member = members.get(player);
        return member != null && member.participant;
    }

    public boolean isSpectator(UUID player) {
        Member member = members.get(player);
        return member != null && !member.participant;
    }

    public Set<UUID> members() {
        return Set.copyOf(members.keySet());
    }

    /** @return the number of positions whose block the session changed so far */
    public int journalSize() {
        return journal.size();
    }

    /** @return true once the journal could take no more: the blocks not changed yet can't be changed any more */
    public boolean isJournalFull() {
        return journal.isFull();
    }

    /** A player joins the session as a participant: it leaves what it owns at the door. */
    public void addParticipant(ServerPlayerEntity player) {
        add(player, true);
    }

    /** A player leaves the session: its session inventory is destroyed, what it owns given back. */
    public void removePlayer(ServerPlayerEntity player) {
        if (state != State.ACTIVE || members.remove(player.getUuid()) == null) return;
        ZoneBubbles.forget(player.getUuid(), this);
        ZoneBorder.bypass++;
        try {
            ZoneBubbles.giveBack(player);
        } finally {
            ZoneBorder.bypass--;
        }
    }

    /**
     * The session is over: every player gets back what it owns, then the zone is put back as it was. A zone where
     * little changed is whole again when this returns; one where much did ({@code miniGameBubbleRestorePerTick}) is
     * {@link #isRestoring() restored} over the next ticks, and nobody may enter it meanwhile.
     */
    public void end() {
        end(false);
    }

    /** Like {@link #end()}, but the zone is whole again when this returns, however much changed. */
    public void endNow() {
        end(true);
    }

    // ------------------------------------------------------------------ beginning

    static ZoneBubble start(MinecraftServer server, UUID sessionId, MiniGameZone zone, ServerWorld world,
                            Collection<ServerPlayerEntity> participants, Collection<ServerPlayerEntity> spectators, Options options) {
        ServerConfig config = ServerConfig.get();
        ZoneBubble bubble = new ZoneBubble(sessionId, zone, options, world, Refusal.NONE);
        bubble.holdChunks();
        bubble.findUnseenChunks();
        List<BlockEntity> found = bubble.findBlockEntities();
        List<Entity> inZone = bubble.findEntities();
        Refusal refusal = found.size() > config.miniGameBubbleMaxBlockEntities ? Refusal.TOO_MANY_BLOCK_ENTITIES
                : inZone.size() > config.miniGameBubbleMaxEntities ? Refusal.TOO_MANY_ENTITIES : Refusal.NONE;
        Text detail = null;
        if (refusal == Refusal.NONE) {
            // Nothing the server forbids in a zone: its sections are asked for the blocks, the entities are those just found
            ZoneForbidden.FoundBlock block = ZoneForbidden.findBlock(world, zone, true);
            Entity entity = block != null ? null : ZoneForbidden.findEntity(inZone);
            if (block != null) {
                refusal = Refusal.FORBIDDEN_BLOCK;
                detail = ZoneForbidden.blockText(block);
            } else if (entity != null) {
                refusal = Refusal.FORBIDDEN_ENTITY;
                detail = ZoneForbidden.entityText(entity.getType(), entity.getBlockPos());
            }
        }
        if (refusal != Refusal.NONE) {
            bubble.releaseChunks();
            ZoneBubble refused = refused(sessionId, zone, refusal);
            refused.refusalDetail = detail;
            return refused;
        }
        ZoneBorder.bypass++;
        try {
            // nobody keeps a hand in the zone: a chest of the zone left open would be emptied behind the session's back
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (zone.bounds().expand(SCREEN_REACH).contains(player.getPos())) player.closeHandledScreen();
            }
            for (ServerPlayerEntity player : participants) bubble.add(player, true);
            for (ServerPlayerEntity player : spectators) bubble.add(player, false);
            // after the players: what their full inventories dropped lies in the zone like any item
            for (BlockEntity entity : found) {
                if (!entity.isRemoved()) bubble.blockEntities.put(entity.getPos().asLong(), entity.createNbtWithIdentifyingData(world.getRegistryManager()));
            }
            bubble.entities = bubble.snapshotEntities();
            bubble.rememberScheduledTicks();
        } finally {
            ZoneBorder.bypass--;
        }
        bubble.directory = ZoneStorage.sessionDirectory(server, sessionId);
        ZoneBubbles.keep(bubble.directory);
        ZoneStorage.delete(bubble.directory);
        ZoneStorage.write(bubble.directory.resolve(ZoneStorage.SESSION_FILE), bubble.toNbt());
        bubble.journal = new ZoneJournal(bubble.directory.resolve(ZoneStorage.JOURNAL_FILE), config.miniGameBubbleMaxJournal);
        return bubble;
    }

    /** A bubble read back from its files after a crash: nobody plays it, it only has its zone to put back. */
    static @Nullable ZoneBubble recovered(MinecraftServer server, Path directory) {
        NbtCompound nbt = ZoneStorage.read(directory.resolve(ZoneStorage.SESSION_FILE));
        if (nbt == null) return null;
        MiniGameZone zone = MiniGameZone.fromNbt(nbt.getCompound("Zone"));
        if (zone == null || !nbt.containsUuid("Session")) return null;
        ServerWorld world = server.getWorld(zone.dimension());
        // its dimension is not there now (a datapack, a mod missing): the zone waits for it, its files stay
        if (world == null) return refused(nbt.getUuid("Session"), zone, Refusal.NO_WORLD);
        ZoneBubble bubble = new ZoneBubble(nbt.getUuid("Session"), zone, Options.DEFAULT, world, Refusal.NONE);
        bubble.directory = directory;
        bubble.recoveredFromFiles = true;
        for (long chunk : nbt.getLongArray("UnseenChunks")) bubble.unseen.add(chunk);
        bubble.ticks = nbt.getList("Ticks", NbtElement.COMPOUND_TYPE);
        for (NbtElement element : nbt.getList("BlockEntities", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entity = (NbtCompound) element;
            bubble.blockEntities.put(BlockEntity.posFromNbt(entity).asLong(), entity);
        }
        List<NbtCompound> entities = new ArrayList<>();
        for (NbtElement element : nbt.getList("Entities", NbtElement.COMPOUND_TYPE)) entities.add((NbtCompound) element);
        bubble.entities = entities;
        byte[] journal = ZoneStorage.readBytes(directory.resolve(ZoneStorage.JOURNAL_FILE));
        if (journal != null) bubble.journal = ZoneJournal.read(journal, world.createCommandRegistryWrapper(RegistryKeys.BLOCK));
        bubble.holdChunks();
        return bubble;
    }

    private NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Session", sessionId);
        nbt.put("Zone", zone.toNbt());
        NbtList blocks = new NbtList();
        blocks.addAll(blockEntities.values());
        nbt.put("BlockEntities", blocks);
        NbtList list = new NbtList();
        list.addAll(entities);
        nbt.put("Entities", list);
        nbt.putLongArray("UnseenChunks", unseen.toLongArray());
        nbt.put("Ticks", ticks);
        return nbt;
    }

    private void holdChunks() {
        for (int x = zone.minChunkX(); x <= zone.maxChunkX(); x++) {
            for (int z = zone.minChunkZ(); z <= zone.maxChunkZ(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                world.getChunkManager().addTicket(TICKET, chunk, TICKET_RADIUS, sessionId);
                // loaded now: the zone is read and journaled from this tick on
                world.getChunk(x, z);
            }
        }
    }

    private void releaseChunks() {
        for (int x = zone.minChunkX(); x <= zone.maxChunkX(); x++) {
            for (int z = zone.minChunkZ(); z <= zone.maxChunkZ(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                world.getChunkManager().removeTicket(TICKET, chunk, TICKET_RADIUS, sessionId);
            }
        }
    }

    /** The chunks of the zone whose entities are not read from disk yet (they are, a few ticks after the chunk). */
    private void findUnseenChunks() {
        for (int x = zone.minChunkX(); x <= zone.maxChunkX(); x++) {
            for (int z = zone.minChunkZ(); z <= zone.maxChunkZ(); z++) {
                long chunk = ChunkPos.toLong(x, z);
                if (!world.isChunkLoaded(chunk)) unseen.add(chunk);
            }
        }
    }

    /** @return true once the entities of every chunk of the zone are read from disk */
    private boolean entitiesLoaded() {
        for (int x = zone.minChunkX(); x <= zone.maxChunkX(); x++) {
            for (int z = zone.minChunkZ(); z <= zone.maxChunkZ(); z++) {
                if (!world.isChunkLoaded(ChunkPos.toLong(x, z))) return false;
            }
        }
        return true;
    }

    private boolean inUnseenChunk(Entity entity) {
        return !unseen.isEmpty() && unseen.contains(ChunkPos.toLong(entity.getBlockX() >> 4, entity.getBlockZ() >> 4));
    }

    /**
     * The entities of the chunks read from disk since the session began are remembered as they come in (at the end
     * of the tick they come in, before anyone can touch them): else they would be wiped with the session's at the end.
     * Those the session spawned there meanwhile are not of them ({@link #spawned}).
     */
    private void seeLateChunks() {
        if (unseen.isEmpty() || recoveredFromFiles) return;
        Set<UUID> known = null;
        boolean changed = false;
        for (Entity entity : findEntities()) {
            if (!inUnseenChunk(entity)) continue;
            Entity root = entity.getRootVehicle();
            if (root instanceof PlayerEntity || spawned.contains(root.getUuid())) continue;
            if (known == null) {
                known = new HashSet<>();
                for (NbtCompound saved : entities) if (saved.containsUuid("UUID")) known.add(saved.getUuid("UUID"));
            }
            if (!known.add(root.getUuid())) continue;
            NbtCompound nbt = new NbtCompound();
            if (root.saveNbt(nbt)) {
                entities.add(nbt);
                changed = true;
            }
        }
        // read whole: nothing more comes from disk there
        for (LongIterator it = unseen.iterator(); it.hasNext(); ) {
            if (world.isChunkLoaded(it.nextLong())) {
                it.remove();
                changed = true;
            }
        }
        if (unseen.isEmpty()) spawned.clear();
        if (changed && directory != null) ZoneStorage.write(directory.resolve(ZoneStorage.SESSION_FILE), toNbt());
    }

    /** An entity is spawned in the zone (not read from disk): if its chunk is not read yet, it is the session's. */
    void onSpawn(Entity entity) {
        if (!unseen.isEmpty() && state == State.ACTIVE && inUnseenChunk(entity)) spawned.add(entity.getUuid());
    }

    /** The block and fluid ticks the zone is waiting for (a button to pop out, a clock to tick): asked again once it is put back. */
    @SuppressWarnings("unchecked")
    private void rememberScheduledTicks() {
        long now = world.getTime();
        for (int x = zone.minChunkX(); x <= zone.maxChunkX(); x++) {
            for (int z = zone.minChunkZ(); z <= zone.maxChunkZ(); z++) {
                WorldChunk chunk = world.getChunk(x, z);
                // read through the only way in that leaves them be: nothing is removed
                if (chunk.getBlockTickScheduler() instanceof ChunkTickScheduler<?> blocks) {
                    ((ChunkTickScheduler<Object>) blocks).removeTicksIf(tick -> {
                        rememberTick(tick, false, now);
                        return false;
                    });
                }
                if (chunk.getFluidTickScheduler() instanceof ChunkTickScheduler<?> fluids) {
                    ((ChunkTickScheduler<Object>) fluids).removeTicksIf(tick -> {
                        rememberTick(tick, true, now);
                        return false;
                    });
                }
            }
        }
    }

    private void rememberTick(OrderedTick<Object> tick, boolean fluid, long now) {
        if (!zone.contains(tick.pos())) return;
        Identifier id = fluid ? Registries.FLUID.getId((Fluid) tick.type()) : Registries.BLOCK.getId((Block) tick.type());
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("Fluid", fluid);
        nbt.putString("Id", id.toString());
        nbt.putLong("Pos", tick.pos().asLong());
        nbt.putInt("Delay", (int) MathHelper.clamp(tick.triggerTick() - now, 0, Integer.MAX_VALUE));
        nbt.putInt("Priority", tick.priority().getIndex());
        ticks.add(nbt);
    }

    /** The zone is whole again: the ticks it waited for when the session began are asked again, as far off as they were. */
    private void scheduleRememberedTicks() {
        for (NbtElement element : ticks) {
            NbtCompound nbt = (NbtCompound) element;
            Identifier id = Identifier.tryParse(nbt.getString("Id"));
            if (id == null) continue;
            BlockPos pos = BlockPos.fromLong(nbt.getLong("Pos"));
            int delay = nbt.getInt("Delay");
            TickPriority priority = TickPriority.byIndex(nbt.getInt("Priority"));
            if (nbt.getBoolean("Fluid")) {
                Registries.FLUID.getOrEmpty(id).ifPresent(fluid -> world.scheduleFluidTick(pos, fluid, delay, priority));
            } else {
                Registries.BLOCK.getOrEmpty(id).ifPresent(block -> world.scheduleBlockTick(pos, block, delay, priority));
            }
        }
        ticks = new NbtList();
    }

    /** The block entities of the zone, found in the tables of its chunks (never by walking its blocks). */
    private List<BlockEntity> findBlockEntities() {
        List<BlockEntity> found = new ArrayList<>();
        for (int x = zone.minChunkX(); x <= zone.maxChunkX(); x++) {
            for (int z = zone.minChunkZ(); z <= zone.maxChunkZ(); z++) {
                for (BlockEntity entity : world.getChunk(x, z).getBlockEntities().values()) {
                    if (zone.contains(entity.getPos())) found.add(entity);
                }
            }
        }
        return found;
    }

    /** The entities of the zone, players and those kept live aside (found in the entity sections the zone covers). */
    private List<Entity> findEntities() {
        return world.getOtherEntities(null, zone.bounds(), entity -> !(entity instanceof PlayerEntity) && zone.contains(entity.getBlockPos())
                && !ZoneBubbles.isKeptLive(entity));
    }

    /** Each entity of the zone as NBT, a mount with all its riders (a rider is never saved apart). */
    private List<NbtCompound> snapshotEntities() {
        Set<Entity> roots = new LinkedHashSet<>();
        for (Entity entity : findEntities()) roots.add(entity.getRootVehicle());
        List<NbtCompound> saved = new ArrayList<>();
        for (Entity root : roots) {
            NbtCompound nbt = new NbtCompound();
            if (!(root instanceof PlayerEntity) && root.saveNbt(nbt)) saved.add(nbt);
        }
        return saved;
    }

    // ------------------------------------------------------------------ players

    private void add(ServerPlayerEntity player, boolean participant) {
        if (state != State.ACTIVE) return;
        UUID id = player.getUuid();
        Member member = members.get(id);
        if (member == null) {
            ZoneBubble other = ZoneBubbles.ofPlayer(id);
            if (other != null) other.removePlayer(player);
            ZoneBorder.bypass++;
            try {
                ZoneBubbles.stash(player, sessionId);
            } finally {
                ZoneBorder.bypass--;
            }
            member = new Member(participant);
            members.put(id, member);
            ZoneBubbles.remember(id, this);
        }
        member.participant = participant;
        // not in the zone yet: it may come in (the mod brings it), and has its hands tied until then
        member.away = !contains(player);
        if (participant && options.adventure() && player.interactionManager.getGameMode() != GameMode.ADVENTURE) {
            player.changeGameMode(GameMode.ADVENTURE);
        }
    }

    @Nullable Member member(UUID player) {
        return members.get(player);
    }

    /** The player is gone (it left the server): it is no longer of the session. */
    void drop(UUID player) {
        members.remove(player);
    }

    /** @return true if the player plays here and stands where it may: it acts in the zone */
    boolean plays(ServerPlayerEntity player) {
        Member member = members.get(player.getUuid());
        return state == State.ACTIVE && member != null && member.participant && !member.away;
    }

    /** The participants found out of the zone right after one of the mod's teleports are there with its leave (but the {@code strays}, out before it). */
    void markAway(MinecraftServer server, Set<UUID> strays) {
        for (Map.Entry<UUID, Member> entry : members.entrySet()) {
            Member member = entry.getValue();
            if (!member.participant || member.away || strays.contains(entry.getKey())) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null && !contains(player)) member.away = true;
        }
    }

    /** Adds the participants out of the zone without leave: they are sent back at the end of the tick. */
    void findStrays(Set<UUID> strays) {
        MinecraftServer server = world.getServer();
        for (Map.Entry<UUID, Member> entry : members.entrySet()) {
            Member member = entry.getValue();
            if (!member.participant || member.away) continue;
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null && !contains(player)) strays.add(entry.getKey());
        }
    }

    void tell(String key, Object... args) {
        MinecraftServer server = world.getServer();
        for (UUID id : members.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) player.sendMessage(Text.translatable("message.steveparty.zone_bubble." + key, args).formatted(Formatting.RED), false);
        }
    }

    // ------------------------------------------------------------------ the zone, as the border sees it

    boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    boolean contains(Entity entity) {
        BlockPos pos = entity.getBlockPos();
        return entity.getWorld() == world && contains(pos.getX(), pos.getY(), pos.getZ());
    }

    double clampX(double x) {
        return MathHelper.clamp(x, minX, maxX + 1 - EDGE);
    }

    double clampY(double y) {
        return MathHelper.clamp(y, minY, maxY + 1 - EDGE);
    }

    double clampZ(double z) {
        return MathHelper.clamp(z, minZ, maxZ + 1 - EDGE);
    }

    double keepOutX(int oldBlock, double x) {
        return oldBlock < minX ? Math.min(x, minX - EDGE) : oldBlock > maxX ? Math.max(x, maxX + 1) : x;
    }

    double keepOutY(int oldBlock, double y) {
        return oldBlock < minY ? Math.min(y, minY - EDGE) : oldBlock > maxY ? Math.max(y, maxY + 1) : y;
    }

    double keepOutZ(int oldBlock, double z) {
        return oldBlock < minZ ? Math.min(z, minZ - EDGE) : oldBlock > maxZ ? Math.max(z, maxZ + 1) : z;
    }

    /**
     * A position of the zone, holding {@code old}, is about to change.
     *
     * @return false if the change must not happen: the journal is full
     */
    boolean journal(long pos, BlockState old, boolean own) {
        boolean session = state == State.ACTIVE;
        // written while the zone is put back too: a crash then still finds every position to put back
        if (journal.record(pos, old, own || !session, state != State.ENDED)) return true;
        long now = world.getTime();
        if (lastFullWarning < 0 || now - lastFullWarning >= JOURNAL_FULL_WARN_INTERVAL_TICKS) {
            lastFullWarning = now;
            tell("journal_full");
        }
        return false;
    }

    // ------------------------------------------------------------------ every tick

    /** @param restoreShare the blocks it may put back this tick, if it is being restored: its share of the budget */
    void tick(int restoreShare) {
        if (state == State.ACTIVE || state == State.RESTORING) seeLateChunks();
        if (state == State.RESTORING) restore(restoreShare);
        if (state != State.ENDED) journal.flush();
    }

    /** What the session journaled so far goes to its file (the world is about to be saved). */
    void flush() {
        journal.flush();
    }

    // ------------------------------------------------------------------ ending

    private void end(boolean now) {
        if (state == State.RESTORING && now) restore(Integer.MAX_VALUE);
        if (state != State.ACTIVE) return;
        journal.flush();
        state = State.RESTORING;
        MinecraftServer server = world.getServer();
        ZoneBorder.bypass++;
        try {
            for (UUID id : new ArrayList<>(members.keySet())) {
                ZoneBubbles.forget(id, this);
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) continue;
                ZoneBubbles.giveBack(player);
                // Nobody of the session stays in the zone with what it owns: a zone put back at once would be theirs,
                // as built (its chests, an arena the mod's pipes alone lead into), until they are brought back
                if (!player.isDead() && contains(player)) ZoneBubbles.putOut(player, this);
            }
            members.clear();
            wipeEntities();
            clearScheduledTicks();
        } finally {
            ZoneBorder.bypass--;
        }
        restore(now ? Integer.MAX_VALUE : ServerConfig.get().miniGameBubbleRestorePerTick);
    }

    /** A bubble read back after a crash: its zone is put back at once. */
    void restoreRecovered() {
        state = State.RESTORING;
        ZoneBorder.bypass++;
        try {
            wipeEntities();
        } finally {
            ZoneBorder.bypass--;
        }
        restore(Integer.MAX_VALUE);
    }

    /** The session vanishes without putting anything back, as if the server had crashed (its files stay). */
    void abandon() {
        journal.flush();
        members.clear();
        state = State.ENDED;
        releaseChunks();
    }

    /**
     * Goes on putting the zone back, {@code blocks} blocks at most.
     * <p>
     * The blocks go in passes over the journal, until one finds nothing left to fix: putting a block back can change
     * another (lava beside water, a sponge), which is then journaled like any change and fixed by the next pass.
     * The first pass only clears what the session added, so that what comes back never meets it. Then the block
     * entities whose block did not change, then the blocks once more (what moved meanwhile, when this took several
     * ticks), and at last the entities, which only come back to a zone that is whole.
     */
    private void restore(int blocks) {
        long budget = (long) blocks * FIX_COST;
        ZoneBorder.bypass++;
        ZoneBorder.RESTORING = true;
        try {
            BlockPos.Mutable pos = new BlockPos.Mutable();
            while (phase != Phase.ENTITIES) {
                if (cursor >= order.length) {
                    // a pass is over (or none began yet)
                    boolean settled = pass >= (phase == Phase.BLOCKS ? 2 : 1) && fixed == 0;
                    if (phase == Phase.BLOCK_ENTITIES || settled || pass >= MAX_PASSES) {
                        phase = Phase.values()[phase.ordinal() + 1];
                        pass = 0;
                        order = phase == Phase.BLOCK_ENTITIES ? blockEntities.keySet().toLongArray() : new long[0];
                        cursor = 0;
                        continue;
                    }
                    pass++;
                    order = journal.positions();
                    cursor = 0;
                    fixed = 0;
                }
                while (cursor < order.length) {
                    if (budget <= 0) return;
                    long at = order[cursor++];
                    pos.set(at);
                    if (phase == Phase.BLOCK_ENTITIES) {
                        // those whose block was put back are already made again
                        if (remade.contains(at)) continue;
                        restoreBlockEntity(pos, true);
                        budget -= BLOCK_ENTITY_COST;
                    } else {
                        budget -= restoreBlock(pos, journal.original(at), phase == Phase.BLOCKS && pass == 1) ? FIX_COST : 1;
                    }
                }
            }
            seeLateChunks();
            // after a crash, what the session left in the zone is only there once read from disk: waited for
            if (recoveredFromFiles && !entitiesLoaded() && entityWait++ < MAX_ENTITY_WAIT_TICKS) return;
            scheduleRememberedTicks();
            // bounded by what the zone held when the session began: done in one go
            wipeEntities();
            restoreEntities();
        } finally {
            ZoneBorder.RESTORING = false;
            ZoneBorder.bypass--;
        }
        finish();
    }

    /** @return true if the block had to be put back */
    private boolean restoreBlock(BlockPos pos, BlockState original, boolean removalsOnly) {
        if (removalsOnly && !original.isAir()) return false;
        BlockState current = world.getBlockState(pos);
        if (current == original) return false;
        // a position of its own from here on: a block entity, a scheduled tick keep the one they are given
        BlockPos at = pos.toImmutable();
        // what the session put in it goes with it: nothing is scattered
        if (current.hasBlockEntity()) world.removeBlockEntity(at);
        world.setBlockState(at, original, RESTORE_FLAGS);
        // its block entity right after it, as it was
        if (blockEntities.containsKey(at.asLong())) {
            restoreBlockEntity(at, false);
            remade.add(at.asLong());
        }
        fixed++;
        return true;
    }

    /**
     * The block entity at {@code pos} is made again from what it was.
     *
     * @param ifChanged only if it is not the same any more (a chest emptied, a sign rewritten)
     */
    private void restoreBlockEntity(BlockPos pos, boolean ifChanged) {
        NbtCompound saved = blockEntities.get(pos.asLong());
        BlockState state = world.getBlockState(pos);
        if (saved == null || !state.hasBlockEntity()) return;
        if (ifChanged) {
            BlockEntity current = world.getBlockEntity(pos);
            // holds what goes on beyond the zone, and its block is still there: left as it is
            if (current != null && ZoneBubbles.isKeptLive(current.getType())) return;
            if (current != null && saved.equals(current.createNbtWithIdentifyingData(world.getRegistryManager()))) return;
        }
        BlockPos at = pos.toImmutable();
        BlockEntity fresh = BlockEntity.createFromNbt(at, state, saved.copy(), world.getRegistryManager());
        if (fresh == null) return;
        world.removeBlockEntity(at);
        world.addBlockEntity(fresh);
        fresh.markDirty();
        world.getChunkManager().markForUpdate(at);
    }

    /**
     * A double chest with one half in the zone and the other out of it would be one inventory across the border:
     * its half in the zone is a single chest while the session lasts (a change like any other: journaled, put back
     * at the end), and so is the other half then.
     */
    void splitStraddlingChests() {
        ZoneBorder.bypass++;
        try {
            for (long at : blockEntities.keySet().toLongArray()) {
                BlockPos pos = BlockPos.fromLong(at);
                BlockState state = world.getBlockState(pos);
                if (!(state.getBlock() instanceof ChestBlock) || state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) continue;
                BlockPos other = pos.offset(ChestBlock.getFacing(state));
                if (contains(other.getX(), other.getY(), other.getZ())) continue;
                world.setBlockState(pos, state.with(ChestBlock.CHEST_TYPE, ChestType.SINGLE), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
        } finally {
            ZoneBorder.bypass--;
        }
    }

    /** Every entity of the zone is removed, players aside (they step down from what they ride), dropping nothing. */
    private void wipeEntities() {
        // twice: what removing an entity leaves behind is removed too
        for (int round = 0; round < 2; round++) {
            for (Entity entity : findEntities()) {
                // the session never saw it: what it is now is what it was
                if (inUnseenChunk(entity)) continue;
                entity.removeAllPassengers();
                if (entity instanceof Inventory inventory) inventory.clear();
                entity.discard();
            }
        }
    }

    /** The entities the zone held when the session began are made again, as they were. */
    private void restoreEntities() {
        for (NbtCompound saved : entities) {
            Entity entity = EntityType.loadEntityWithPassengers(saved.copy(), world, loaded -> loaded);
            if (entity == null) continue;
            if (world.spawnNewEntityAndPassengers(entity)) continue;
            // one of them got away with its UUID: the one of the session goes, the one remembered comes back
            entity.streamSelfAndPassengers().forEach(self -> {
                Entity twin = world.getEntity(self.getUuid());
                if (twin != null && !(twin instanceof PlayerEntity)) twin.discard();
            });
            if (!world.spawnNewEntityAndPassengers(entity)) {
                Steveparty.LOGGER.warn("An entity of a mini-game zone could not be put back: {}", saved.getString("id"));
            }
        }
    }

    /**
     * The block and fluid ticks the session left on the positions it changed (and right beside them) are dropped:
     * they were asked for blocks that are about to be gone.
     */
    @SuppressWarnings("unchecked")
    private void clearScheduledTicks() {
        if (journal.size() == 0) return;
        for (int x = zone.minChunkX(); x <= zone.maxChunkX(); x++) {
            for (int z = zone.minChunkZ(); z <= zone.maxChunkZ(); z++) {
                WorldChunk chunk = world.getChunk(x, z);
                if (chunk.getBlockTickScheduler() instanceof ChunkTickScheduler<?> blocks) {
                    ((ChunkTickScheduler<Object>) blocks).removeTicksIf(tick -> touched(tick.pos()));
                }
                if (chunk.getFluidTickScheduler() instanceof ChunkTickScheduler<?> fluids) {
                    ((ChunkTickScheduler<Object>) fluids).removeTicksIf(tick -> touched(tick.pos()));
                }
            }
        }
    }

    private boolean touched(BlockPos pos) {
        long at = pos.asLong();
        return journal.has(at) || journal.has(BlockPos.add(at, 1, 0, 0)) || journal.has(BlockPos.add(at, -1, 0, 0))
                || journal.has(BlockPos.add(at, 0, 1, 0)) || journal.has(BlockPos.add(at, 0, -1, 0))
                || journal.has(BlockPos.add(at, 0, 0, 1)) || journal.has(BlockPos.add(at, 0, 0, -1));
    }

    private void finish() {
        state = State.ENDED;
        releaseChunks();
        ZoneBubbles.ended(this, directory);
    }
}
