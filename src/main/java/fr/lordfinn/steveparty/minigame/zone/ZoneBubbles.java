package fr.lordfinn.steveparty.minigame.zone;

import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The mini-game bubbles of the server: a mini-game played in place, in the real world, inside a box zone that is
 * given back as it was, by players who take nothing in and nothing out. Safe in survival: nothing is duplicated,
 * the arena repairs itself.
 * <p>
 * A session calls {@link #begin} and keeps the {@link ZoneBubble} it gets, {@link ZoneBubble#end() ends} it when it
 * is over, and wraps the teleports it makes itself in {@link #allowTeleports} (whatever else it does across the
 * border, in {@link #unguarded}). Everything else goes on its own.
 * <p>
 * What it costs: nothing while no session runs (every hook reads {@link ZoneBorder#ACTIVE} and leaves). During a
 * session, a box test per event (a block changing, an entity stepping into another block, a player acting: the box
 * of all zones first, so that an event far from them costs the same whatever the number of sessions), a hash
 * map insert for the first change of a position, and one pass over the online players per tick. The zone is never
 * walked: not at the start, not during the session, not at the end.
 */
public final class ZoneBubbles {
    private static final int WARN_INTERVAL_TICKS = 40;
    /** The fewest blocks a restoration puts back a tick, however many share the budget. */
    private static final int MIN_RESTORE_SHARE = 16;
    /** How far out of a zone a player sent out of it lands. */
    private static final double EVICT_MARGIN = 0.7;

    /**
     * The bubbles in session or being restored: the zones the border guards. Never changed in place: a new array is
     * built whole, then published ({@link #setLive}), so that a reader never meets it half made.
     */
    private static ZoneBubble[] live = new ZoneBubble[0];
    /**
     * The box holding every live zone, all worlds together: almost every event happens far from every zone and
     * stops at it, after six comparisons, however many sessions run ({@link #at}). Empty (min above max) without one.
     */
    private static int spanMinX = Integer.MAX_VALUE, spanMinY = Integer.MAX_VALUE, spanMinZ = Integer.MAX_VALUE;
    private static int spanMaxX = Integer.MIN_VALUE, spanMaxY = Integer.MIN_VALUE, spanMaxZ = Integer.MIN_VALUE;
    /** The bubble each player of a session is in. */
    private static final Map<UUID, ZoneBubble> BY_PLAYER = new HashMap<>();
    /** What the players holding a session inventory own (also on disk), by player. */
    private static final Map<UUID, NbtCompound> STASHES = new HashMap<>();
    /** Files of what was given back, kept until the world is saved without needing them. */
    private static final List<Path> RETIRED = new ArrayList<>();
    private static final Map<UUID, Long> LAST_WARNING = new HashMap<>();
    /** Blocks anyone of a session may use wherever it stands (the session's own controls). */
    private static final Set<Block> USABLE = new HashSet<>();
    /** Block entity types left alone by a restoration while their block did not change. */
    private static final Set<BlockEntityType<?>> KEPT_LIVE = new HashSet<>();
    /** Entities left alone by sessions. */
    private static final List<Predicate<Entity>> KEPT_ENTITIES = new ArrayList<>();

    private ZoneBubbles() {
    }

    public static void initialize() {
        ServerConfig.load();
        ZonePlayerRules.initialize();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // the settings as the file says now, and what they forbid among what this server has
            ServerConfig.load();
            ZoneForbidden.resolve();
            recover(server);
        });
        // before the players are sent away and the world saved: every zone whole, every inventory back
        ServerLifecycleEvents.SERVER_STOPPING.register(ZoneBubbles::endAll);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
            for (ZoneBubble bubble : live) bubble.flush();
            ZoneStorage.flush();
        });
        ServerLifecycleEvents.AFTER_SAVE.register((server, flush, force) -> {
            // saved without them: the zones as they were, the players with what they own
            for (Path path : RETIRED) ZoneStorage.delete(path);
            RETIRED.clear();
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (ZoneBorder.ACTIVE) ZoneBorder.resetOrigin();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ZoneBorder.ACTIVE) tick(server);
        });
        // A player coming is settled by the one join hook of the mini-games (MiniGameReturns), which tells it so
        // given back what it owns on its death screen: it keeps it when it respawns, whatever the keepInventory rule
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) ZonePlayerStash.afterRespawn(oldPlayer, newPlayer);
        });
        // nothing hurts across a border: neither an arrow, an explosion, a splash nor a mob of the other side
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !ZoneBorder.ACTIVE || !ZoneBorder.blocksDamage(entity, source));
        // a member dying where it may not drop anything drops no experience either: it is of the session
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (ZoneBorder.ACTIVE && entity instanceof ServerPlayerEntity player && ZoneBorder.blocksDrop(player)) ZonePlayerStash.dropNoExperience(player);
            return true;
        });
        // before the player is saved: it leaves with what it owns
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ZoneBubble bubble = BY_PLAYER.get(handler.getPlayer().getUuid());
            if (bubble != null) bubble.removePlayer(handler.getPlayer());
        });
    }

    // ------------------------------------------------------------------ what a session asks

    /**
     * A session begins in a zone: from now on, and until the bubble is {@link ZoneBubble#end() ended}, the zone is
     * journaled, its players hold a session inventory and nothing crosses its border.
     * <p>
     * The players need not be in the zone yet: one of the session found out of it may come in, and touches nothing
     * until it has. Anyone else found in the zone is sent just out of it.
     *
     * @return the bubble; one that {@link ZoneBubble#isActive() is not active} could not begin (feature turned off,
     * zone too big or over another one...: {@link ZoneBubble#refusal()}, {@link #refusalText}) and does nothing,
     * whatever is asked of it. Nobody is told: the session says what it does about it.
     */
    public static ZoneBubble begin(MinecraftServer server, UUID sessionId, MiniGameZone zone,
                                   Collection<ServerPlayerEntity> participants, Collection<ServerPlayerEntity> spectators,
                                   ZoneBubble.Options options) {
        // what the zone holds (too much, something forbidden) is looked at once its chunks are held: ZoneBubble.start
        ZoneBubble.Refusal refusal = checkPlace(server, zone);
        if (refusal == ZoneBubble.Refusal.NONE && get(sessionId) != null) refusal = ZoneBubble.Refusal.OVERLAP;
        ZoneBubble bubble = refusal != ZoneBubble.Refusal.NONE ? ZoneBubble.refused(sessionId, zone, refusal)
                : ZoneBubble.start(server, sessionId, zone, server.getWorld(zone.dimension()), participants, spectators, options);
        if (!bubble.isActive()) return bubble;
        ZoneBorder.thread = server.getThread();
        add(bubble);
        ZoneBorder.ACTIVE = true;
        ZoneBorder.resetOrigin();
        bubble.splitStraddlingChests();
        return bubble;
    }

    /**
     * Whether a session could begin in the zone now, as far as can be told without loading it (what it holds is
     * only counted when it begins).
     */
    public static ZoneBubble.Refusal check(MinecraftServer server, MiniGameZone zone) {
        ZoneBubble.Refusal place = checkPlace(server, zone);
        if (place != ZoneBubble.Refusal.NONE) return place;
        return forbiddenBlock(server, zone) != null ? ZoneBubble.Refusal.FORBIDDEN_BLOCK : ZoneBubble.Refusal.NONE;
    }

    /** Whether the zone itself can take a session: the feature, its dimension, its size, the zones in session. Costs nothing. */
    public static ZoneBubble.Refusal checkPlace(MinecraftServer server, MiniGameZone zone) {
        return checkPlace(server, zone, null);
    }

    /** Like {@link #checkPlace(MinecraftServer, MiniGameZone)}, the zone of {@code ignored} not counted as in session. */
    public static ZoneBubble.Refusal checkPlace(MinecraftServer server, MiniGameZone zone, @Nullable ZoneBubble ignored) {
        ServerConfig config = ServerConfig.get();
        if (!config.miniGameBubble) return ZoneBubble.Refusal.DISABLED;
        if (server.getWorld(zone.dimension()) == null) return ZoneBubble.Refusal.NO_WORLD;
        int max = config.miniGameBubbleMaxSize;
        if (zone.sizeX() > max || zone.sizeY() > max || zone.sizeZ() > max) return ZoneBubble.Refusal.TOO_BIG;
        for (ZoneBubble other : live) if (other != ignored && other.zone().intersects(zone)) return ZoneBubble.Refusal.OVERLAP;
        return ZoneBubble.Refusal.NONE;
    }

    /**
     * A block of the zone the server does not allow in a zone, null if none is seen: only the chunks that are
     * loaded are looked at (the others when a session begins), by the palettes of their sections.
     */
    public static ZoneForbidden.@Nullable FoundBlock forbiddenBlock(MinecraftServer server, MiniGameZone zone) {
        ServerWorld world = server.getWorld(zone.dimension());
        return world == null ? null : ZoneForbidden.findBlock(world, zone, false);
    }

    /** Why a zone can't take a session now, as {@link #check} sees it: with the forbidden block and where it is. */
    public static @Nullable Text refusalText(MinecraftServer server, MiniGameZone zone) {
        ZoneBubble.Refusal refusal = check(server, zone);
        ZoneForbidden.FoundBlock block = refusal == ZoneBubble.Refusal.FORBIDDEN_BLOCK ? forbiddenBlock(server, zone) : null;
        return block != null ? ZoneForbidden.blockText(block) : refusalText(refusal);
    }

    /** @return true if all that holds the zone up is being put back: it is free in a moment */
    public static boolean isBeingRestored(MiniGameZone zone) {
        boolean restoring = false;
        for (ZoneBubble other : live) {
            if (!other.zone().intersects(zone)) continue;
            if (other.isActive()) return false;
            restoring = true;
        }
        return restoring;
    }

    /**
     * Why a session could not begin, for its players; null when there is nothing to say (it began, or the feature
     * is off). {@link ZoneBubble#refusalText()} says more: which block or entity is forbidden, and where.
     */
    public static @Nullable Text refusalText(ZoneBubble.Refusal refusal) {
        return switch (refusal) {
            case NONE, DISABLED -> null;
            case TOO_BIG -> Text.translatable("message.steveparty.zone_bubble.too_big", ServerConfig.get().miniGameBubbleMaxSize);
            case OVERLAP -> Text.translatable("message.steveparty.zone_bubble.overlap");
            case TOO_MANY_BLOCK_ENTITIES, TOO_MANY_ENTITIES -> Text.translatable("message.steveparty.zone_bubble.too_full");
            case NO_WORLD -> Text.translatable("message.steveparty.zone_bubble.unavailable");
            case FORBIDDEN_BLOCK, FORBIDDEN_ENTITY -> Text.translatable("message.steveparty.zone_bubble.forbidden");
        };
    }

    /** @return the bubble (in session or being restored) whose zone holds this block, null if none does */
    public static @Nullable ZoneBubble of(World world, BlockPos pos) {
        // a client's world is in no zone (and its thread does not read the server's lists)
        return ZoneBorder.ACTIVE && !world.isClient ? at(world, pos.getX(), pos.getY(), pos.getZ()) : null;
    }

    /** @return the bubble this player is in the session of (participant or spectator), null if none */
    public static @Nullable ZoneBubble ofPlayer(ServerPlayerEntity player) {
        return ofPlayer(player.getUuid());
    }

    public static @Nullable ZoneBubble ofPlayer(UUID player) {
        return BY_PLAYER.get(player);
    }

    /** @return the bubble of that session, null if it is over (or never began) */
    public static @Nullable ZoneBubble get(UUID sessionId) {
        for (ZoneBubble bubble : live) if (bubble.sessionId().equals(sessionId)) return bubble;
        return null;
    }

    /** The bubbles in session or being restored. */
    public static List<ZoneBubble> all() {
        return List.of(live);
    }

    /**
     * Runs teleports the mod makes itself (a pipe, the way back at the end of a game): they pass the border. A
     * participant they take out of its zone stays of its session, with its session inventory, and has its hands
     * tied (it breaks, places, uses, picks up and drops nothing) until it is back in.
     */
    public static void allowTeleports(Runnable teleports) {
        // who stepped out of its zone on its own (the border puts it back at the end of the tick) is not excused by a
        // teleport of the mod meanwhile, made for someone else
        Set<UUID> strays = new HashSet<>();
        for (ZoneBubble bubble : live) if (bubble.isActive()) bubble.findStrays(strays);
        ZoneBorder.allow++;
        try {
            teleports.run();
        } finally {
            ZoneBorder.allow--;
            for (ZoneBubble bubble : live) if (bubble.isActive()) bubble.markAway(bubble.world.getServer(), strays);
        }
    }

    /**
     * Runs what the mod itself does to a zone in session or from it (a gate it opens, a prop it moves in or out, an
     * entity it brings): nothing of it is stopped at the border, teleports included. What it changes in the zone is
     * journaled and put back like the rest.
     */
    public static void unguarded(Runnable action) {
        ZoneBorder.bypass++;
        try {
            allowTeleports(action);
        } finally {
            ZoneBorder.bypass--;
        }
    }

    /** Anyone of a session may use this block wherever it stands: for the controls of the session itself. */
    public static void allowUse(Block block) {
        USABLE.add(block);
    }

    /**
     * Block entities of this type are left as they are when a zone is put back, unless their block had to be put
     * back too: for those that hold what goes on beyond the zone (a party, whose controller stands in it).
     */
    public static void keepLive(BlockEntityType<?> type) {
        KEPT_LIVE.add(type);
    }

    static boolean isKeptLive(BlockEntityType<?> type) {
        return KEPT_LIVE.contains(type);
    }

    /**
     * Entities this says yes to are no part of a zone: neither remembered when a session begins nor removed and
     * made again when it ends (they still don't cross its border). For those of something that goes on beyond the
     * zone: the tokens of a running party.
     */
    public static void keepLive(Predicate<Entity> entities) {
        KEPT_ENTITIES.add(entities);
    }

    static boolean isKeptLive(Entity entity) {
        for (Predicate<Entity> kept : KEPT_ENTITIES) if (kept.test(entity)) return true;
        return false;
    }

    /**
     * @return true if a traveller the mod carries (a pipe) may come out at {@code pos}: in a zone in session, only a
     * player of that session; out of every zone, anyone but a participant playing in its zone; and what is not a
     * player stays on its side
     */
    public static boolean mayArrive(Entity traveller, World world, BlockPos pos) {
        if (!ZoneBorder.ACTIVE) return true;
        ZoneBubble there = at(world, pos.getX(), pos.getY(), pos.getZ());
        if (traveller instanceof ServerPlayerEntity player) {
            ZoneBubble mine = BY_PLAYER.get(player.getUuid());
            if (there != null) return there == mine && there.isActive();
            return mine == null || !mine.plays(player);
        }
        BlockPos from = traveller.getBlockPos();
        return traveller.getWorld() == world && at(world, from.getX(), from.getY(), from.getZ()) == there;
    }

    /** Every session ends now and every zone is whole again (the server stops). */
    public static void endAll(MinecraftServer server) {
        if (live.length > 0) Steveparty.LOGGER.info("Ending {} mini-game session(s): their zones are put back", live.length);
        for (ZoneBubble bubble : live) bubble.endNow();
        ZoneStorage.flush();
    }

    // ------------------------------------------------------------------ lookups of the border

    private static void add(ZoneBubble bubble) {
        ZoneBubble[] more = Arrays.copyOf(live, live.length + 1);
        more[live.length] = bubble;
        setLive(more);
    }

    private static void setLive(ZoneBubble[] bubbles) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ZoneBubble bubble : bubbles) {
            BlockBox box = bubble.zone().box();
            minX = Math.min(minX, box.getMinX());
            minY = Math.min(minY, box.getMinY());
            minZ = Math.min(minZ, box.getMinZ());
            maxX = Math.max(maxX, box.getMaxX());
            maxY = Math.max(maxY, box.getMaxY());
            maxZ = Math.max(maxZ, box.getMaxZ());
        }
        spanMinX = minX;
        spanMinY = minY;
        spanMinZ = minZ;
        spanMaxX = maxX;
        spanMaxY = maxY;
        spanMaxZ = maxZ;
        live = bubbles;
    }

    /**
     * The hottest lookup of the mod while a session runs (every block change, every entity stepping into another
     * block): out of the box of all zones, it is over at once; within it, the few zones are tried in turn (ten
     * pages played at once: ten box tests, cheaper than any index).
     */
    static @Nullable ZoneBubble at(World world, int x, int y, int z) {
        if (x < spanMinX || x > spanMaxX || z < spanMinZ || z > spanMaxZ || y < spanMinY || y > spanMaxY) return null;
        for (ZoneBubble bubble : live) {
            if (bubble.world == world && bubble.contains(x, y, z)) return bubble;
        }
        return null;
    }

    /** @return the zone the player acts in (it plays there and stands where it may), null for the rest of the world */
    static @Nullable ZoneBubble sideOf(ServerPlayerEntity player) {
        ZoneBubble bubble = BY_PLAYER.get(player.getUuid());
        return bubble != null && bubble.plays(player) ? bubble : null;
    }

    /**
     * @return true if the player may touch what is at {@code pos} (break, place, use, hit, pick up): in a zone, only
     * who plays there; out of every zone, only who is of no session
     */
    static boolean canTouch(ServerPlayerEntity player, World world, BlockPos pos) {
        ZoneBubble there = at(world, pos.getX(), pos.getY(), pos.getZ());
        ZoneBubble mine = BY_PLAYER.get(player.getUuid());
        if (there == null) return mine == null;
        return there == mine && there.plays(player);
    }

    static boolean isUsable(Block block) {
        return USABLE.contains(block);
    }

    /** @return true if the player is of a session but does not play in its zone now (a spectator, a participant the mod took out): it touches nothing */
    static boolean handsTied(ServerPlayerEntity player) {
        ZoneBubble bubble = BY_PLAYER.get(player.getUuid());
        return bubble != null && !bubble.plays(player);
    }

    /**
     * @return true if two places are not on the same side of the border of a zone in session or being restored:
     * what the mod links from afar (a merchant and its stock, a party and its bank) is not reached across it, else
     * what is taken from a zone would come back with it, and what is put in one would go
     */
    public static boolean separated(World world, BlockPos a, BlockPos b) {
        return ZoneBorder.ACTIVE && !world.isClient && at(world, a.getX(), a.getY(), a.getZ()) != at(world, b.getX(), b.getY(), b.getZ());
    }

    /** @return true if the place is in a zone in session or being restored: what lies there will be put back as it was */
    public static boolean isInZone(World world, BlockPos pos) {
        return ZoneBorder.ACTIVE && !world.isClient && at(world, pos.getX(), pos.getY(), pos.getZ()) != null;
    }

    /** @return true if the player may not teleport to {@code to} (null: out of every zone) */
    static boolean blocksTeleport(ServerPlayerEntity player, @Nullable ZoneBubble to) {
        ZoneBubble side = sideOf(player);
        if (side != null) return to != side;
        return to != null && !player.isSpectator() && !to.isMember(player.getUuid());
    }

    /** @return true if the player is of a session and may not have this item: the server forbids it to sessions */
    static boolean blocksItem(ServerPlayerEntity player, ItemStack stack) {
        return BY_PLAYER.containsKey(player.getUuid()) && ZoneForbidden.isForbidden(stack);
    }

    /** @return true if what the player drops is destroyed: a spectator, or a participant out of its zone */
    static boolean blocksDrop(ServerPlayerEntity player) {
        ZoneBubble bubble = BY_PLAYER.get(player.getUuid());
        return bubble != null && !(bubble.isParticipant(player.getUuid()) && bubble.contains(player));
    }

    /** A short word above the hotbar, not said again for two seconds. */
    static void warn(ServerPlayerEntity player, String key) {
        long now = player.getServer().getTicks();
        Long last = LAST_WARNING.get(player.getUuid());
        if (last != null && now - last < WARN_INTERVAL_TICKS && now >= last) return;
        LAST_WARNING.put(player.getUuid(), now);
        player.sendMessage(Text.translatable("message.steveparty.zone_bubble." + key).formatted(Formatting.RED), true);
    }

    // ------------------------------------------------------------------ the players' inventories

    static void remember(UUID player, ZoneBubble bubble) {
        BY_PLAYER.put(player, bubble);
    }

    static void forget(UUID player, ZoneBubble bubble) {
        BY_PLAYER.remove(player, bubble);
    }

    /** The player leaves what it owns at the door: on disk first, then out of its hands. */
    static void stash(ServerPlayerEntity player, UUID session) {
        UUID id = player.getUuid();
        Path file = ZoneStorage.stashFile(player.getServer(), id);
        // still holding the session inventory of an earlier session: what it owns is what was stashed then
        if (STASHES.containsKey(id)) ZonePlayerStash.restore(player, STASHES.remove(id));
        RETIRED.remove(file);
        NbtCompound stash = ZonePlayerStash.capture(player, session);
        STASHES.put(id, stash);
        ZoneStorage.write(file, stash);
        ZonePlayerStash.empty(player);
    }

    /** The player gets back what it owns. Its file stays until the world is saved: until then the saved player holds the session inventory. */
    static void giveBack(ServerPlayerEntity player) {
        NbtCompound stash = STASHES.remove(player.getUuid());
        if (stash == null) return;
        ZonePlayerStash.restore(player, stash);
        RETIRED.add(ZoneStorage.stashFile(player.getServer(), player.getUuid()));
    }

    /**
     * A player comes (back) on the server: whatever session it was in is over for it. If it was saved holding a
     * session inventory (the server crashed, or it left without a word), it gets back what it owns.
     *
     * @return true if it got back what it owns (the caller tells it)
     */
    public static boolean settle(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        ZoneBubble bubble = BY_PLAYER.remove(id);
        if (bubble != null) bubble.drop(id);
        NbtCompound stash = STASHES.remove(id);
        boolean tagged = player.getCommandTags().contains(ZonePlayerStash.TAG);
        if (stash == null) {
            if (tagged) {
                player.removeCommandTag(ZonePlayerStash.TAG);
                Steveparty.LOGGER.warn("{} holds a mini-game session inventory but what it owns was not found", player.getName().getString());
            }
            return false;
        }
        Path file = ZoneStorage.stashFile(player.getServer(), id);
        if (!tagged) {
            // saved before its session began: what it holds is what it owns, the stash is from a time never saved
            ZoneStorage.delete(file);
            return false;
        }
        ZoneBorder.bypass++;
        try {
            ZonePlayerStash.restore(player, stash);
        } finally {
            ZoneBorder.bypass--;
        }
        RETIRED.add(file);
        return true;
    }

    // ------------------------------------------------------------------ every tick

    private static void tick(MinecraftServer server) {
        // the restorations share one budget a tick: ten zones put back at once cost the server what one does
        int restoring = 0;
        for (ZoneBubble bubble : live) if (bubble.isRestoring()) restoring++;
        int share = restoring == 0 ? 0 : Math.max(MIN_RESTORE_SHARE, ServerConfig.get().miniGameBubbleRestorePerTick / restoring);
        for (ZoneBubble bubble : live) bubble.tick(share);
        if (live.length == 0) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isDead()) continue;
            ZoneBubble mine = BY_PLAYER.get(player.getUuid());
            ZoneBubble.Member member = mine == null ? null : mine.member(player.getUuid());
            ZoneBubble here = at(player.getWorld(), player.getBlockX(), player.getBlockY(), player.getBlockZ());
            if (member != null && member.participant && mine.isActive()) {
                if (here == mine) {
                    member.away = false;
                    member.hasLast = true;
                    member.lastX = player.getX();
                    member.lastY = player.getY();
                    member.lastZ = player.getZ();
                    member.lastYaw = player.getYaw();
                    member.lastPitch = player.getPitch();
                    continue;
                }
                if (!member.away) {
                    sendBack(player, mine, member);
                    continue;
                }
            }
            // anyone else walks out of a zone it is not of (someone only passing through as a game-mode spectator aside)
            if (here != null && (here != mine || here.isRestoring()) && !player.isSpectator()) sendOut(player, here);
        }
    }

    /** A participant out of its zone without leave is put back where it last stood in it. */
    private static void sendBack(ServerPlayerEntity player, ZoneBubble bubble, ZoneBubble.Member member) {
        ServerWorld world = bubble.world;
        Vec3d to;
        if (member.hasLast) to = new Vec3d(member.lastX, member.lastY, member.lastZ);
        else {
            Vec3d center = bubble.zone().bounds().getCenter();
            to = new Vec3d(center.x, bubble.zone().box().getMinY(), center.z);
        }
        teleport(player, world, to, member.hasLast ? member.lastYaw : player.getYaw(), member.hasLast ? member.lastPitch : player.getPitch());
        warn(player, "cannot_leave");
    }

    /** Someone who is not of the session is put just out of the zone, through its nearest side. */
    private static void sendOut(ServerPlayerEntity player, ZoneBubble bubble) {
        putOut(player, bubble);
        warn(player, bubble.isRestoring() ? "restoring" : "cannot_enter");
    }

    /** The player is put just out of the zone, through its nearest side, without a word. */
    static void putOut(ServerPlayerEntity player, ZoneBubble bubble) {
        ServerWorld world = bubble.world;
        Box zone = bubble.zone().bounds();
        double x = player.getX(), z = player.getZ();
        double west = x - zone.minX, east = zone.maxX - x, north = z - zone.minZ, south = zone.maxZ - z;
        double nearest = Math.min(Math.min(west, east), Math.min(north, south));
        if (nearest == west) x = zone.minX - EVICT_MARGIN;
        else if (nearest == east) x = zone.maxX + EVICT_MARGIN;
        else if (nearest == north) z = zone.minZ - EVICT_MARGIN;
        else z = zone.maxZ + EVICT_MARGIN;
        double y = player.getY();
        Box body = player.getBoundingBox().offset(x - player.getX(), 0, z - player.getZ());
        int up = 0;
        while (up < 4 && !world.isSpaceEmpty(player, body.offset(0, up, 0))) up++;
        // no room at its height: on top of whatever stands there
        if (up < 4) y += up;
        else y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        if (player.hasVehicle()) player.stopRiding();
        teleport(player, world, new Vec3d(x, y, z), player.getYaw(), player.getPitch());
    }

    private static void teleport(ServerPlayerEntity player, ServerWorld world, Vec3d to, float yaw, float pitch) {
        ZoneBorder.bypass++;
        try {
            player.teleport(world, to.x, to.y, to.z, Set.of(), yaw, pitch, false);
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0;
        } finally {
            ZoneBorder.bypass--;
        }
    }

    // ------------------------------------------------------------------ ends, crashes and restarts

    /** A session begins again under an id whose files were about to go: they stay. */
    static void keep(Path path) {
        RETIRED.remove(path);
    }

    /** A bubble is gone, its zone whole again: its files go once the world is saved as it is now. */
    static void ended(ZoneBubble bubble, @Nullable Path directory) {
        List<ZoneBubble> left = new ArrayList<>(List.of(live));
        left.remove(bubble);
        setLive(left.toArray(new ZoneBubble[0]));
        if (directory != null) RETIRED.add(directory);
        ZoneBorder.resetOrigin();
        if (live.length == 0) {
            ZoneBorder.ACTIVE = false;
            LAST_WARNING.clear();
        }
    }

    /**
     * The server starts (or picks up after {@link #simulateCrash}): the zones of the sessions it left unfinished are
     * put back as they were, and the players it left holding a session inventory get back what they own (now if they
     * are there, else when they come).
     */
    public static void recover(MinecraftServer server) {
        ZoneBorder.thread = server.getThread();
        for (Path file : ZoneStorage.list(ZoneStorage.playersDirectory(server))) {
            String name = file.getFileName().toString();
            if (!name.endsWith(".dat")) continue;
            NbtCompound stash = ZoneStorage.read(file);
            try {
                if (stash != null) STASHES.put(UUID.fromString(name.substring(0, name.length() - 4)), stash);
            } catch (IllegalArgumentException e) {
                Steveparty.LOGGER.warn("Not a player's mini-game stash: {}", file);
            }
        }
        for (Path directory : ZoneStorage.list(ZoneStorage.root(server).resolve("sessions"))) {
            ZoneBubble bubble = ZoneBubble.recovered(server, directory);
            if (bubble == null) {
                // nothing of it was written whole: nothing of it was saved either
                ZoneStorage.delete(directory);
                continue;
            }
            Steveparty.LOGGER.info("Putting back the mini-game zone of the unfinished session {}", bubble.sessionId());
            add(bubble);
            ZoneBorder.ACTIVE = true;
            bubble.restoreRecovered();
        }
        for (ServerPlayerEntity player : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
            if (settle(player)) player.sendMessage(Text.translatable("message.steveparty.zone_bubble.inventory_back"), false);
        }
    }

    /**
     * Forgets every session without putting anything back, as a server that crashed would: the zones stay changed,
     * the players keep their session inventories, the files stay. For tests of {@link #recover}.
     */
    public static void simulateCrash() {
        for (ZoneBubble bubble : live) bubble.abandon();
        ZoneStorage.flush();
        reset();
    }

    private static void reset() {
        setLive(new ZoneBubble[0]);
        BY_PLAYER.clear();
        STASHES.clear();
        RETIRED.clear();
        LAST_WARNING.clear();
        ZoneBorder.ACTIVE = false;
        ZoneBorder.resetOrigin();
        // The stopped server's thread is not kept alive by the hooks
        ZoneBorder.thread = null;
    }
}
