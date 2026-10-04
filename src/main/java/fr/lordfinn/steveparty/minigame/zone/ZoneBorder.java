package fr.lordfinn.steveparty.minigame.zone;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The border of the zones with a session going on, as the game's own code meets it: every check here is called by a
 * mixin, on the event itself, and only once {@link #ACTIVE} was read true (no session: one boolean read, nothing else).
 * <p>
 * Two rules close most of the ways across:
 * <ul>
 *   <li><b>the wall</b> ({@link #wall}): an entity never moves, is pushed, pulled or teleported from one side to the
 *   other: the move stops at the border;</li>
 *   <li><b>the origin</b> ({@link #enter}/{@link #exit}): whatever starts on one side (a block change and all it
 *   sets off, the tick of a block, of a fluid or of an entity, the action of a player) changes no block and spawns
 *   no entity on the other. The side is the one of the place it starts from, or, for a player, the one it plays on.</li>
 * </ul>
 * The rest are transfers neither rule sees (hoppers, droppers, pistons, explosions, pickups): one check each.
 * <p>
 * Server thread only: a call from another thread (client world, world generation) is left alone.
 */
public final class ZoneBorder {
    /** True while a zone is in session or being restored: read first by every hook. */
    public static boolean ACTIVE;
    /** True while blocks are put back: the neighbours of a restored block are told nothing. */
    public static boolean RESTORING;
    static Thread thread;
    /** The engine's own moves and changes (restoring, sending a player back) pass every guard. */
    static int bypass;
    /** The mod's own teleports of players pass the wall. */
    static int allow;
    /** What a player drops is destroyed: its session inventory is being taken from it. */
    static int discardDrops;

    private static int depth;
    private static World originWorld;
    private static int originX, originY, originZ;
    private static @Nullable ServerPlayerEntity originPlayer;
    /** The origin is something a player does with its hands (not its own tick). */
    private static boolean originAction;

    private ZoneBorder() {
    }

    // ------------------------------------------------------------------ the origin

    /**
     * The tick of a block, a fluid or an entity starts at {@code pos}: until {@link #exit}, it only acts on the side
     * of {@code pos}.
     * <p>
     * Ticks start from the game loop, never from one another: whatever origin is still there was left behind (a
     * mixin of another mod returned from the middle of a tick without passing by its exit) and is replaced. The one
     * nesting the game has, a block that runs its random tick from its scheduled one, keeps its origin.
     */
    public static void enter(World world, BlockPos pos) {
        if (world.isClient || Thread.currentThread() != thread) return;
        if (depth > 0 && originPlayer == null && originWorld == world
                && originX == pos.getX() && originY == pos.getY() && originZ == pos.getZ()) {
            depth++;
            return;
        }
        depth = 1;
        setOrigin(world, pos);
    }

    /**
     * A player acts (uses, breaks, ticks): until {@link #exit}, it only acts on the side it plays on (its zone, or
     * the rest of the world). Like a tick, the action of a player starts from the game loop.
     */
    public static void enterPlayer(ServerPlayerEntity player) {
        enterPlayer(player, false);
    }

    /**
     * Like {@link #enterPlayer(ServerPlayerEntity)}; {@code action}: it uses or breaks something. A member of a session
     * whose hands are tied (a spectator, a participant out of its zone) changes no block and spawns nothing by its
     * actions, on either side: the blocks it may still use (the session's own controls) do what they do, the item in
     * its hand does nothing (a block of the session placed out of the zone, a spawn egg...).
     */
    public static void enterPlayer(ServerPlayerEntity player, boolean action) {
        if (Thread.currentThread() != thread) return;
        if (depth > 0 && originPlayer == player) {
            depth++;
            return;
        }
        depth = 1;
        originWorld = player.getWorld();
        originPlayer = player;
        originAction = action;
    }

    /** A block changes: until {@link #exitChange}, all it sets off stays on its side (or on the side of what made it change). */
    public static void enterChange(World world, BlockPos pos) {
        if (world.isClient || Thread.currentThread() != thread) return;
        if (depth++ == 0) setOrigin(world, pos);
    }

    public static void exit(World world) {
        if (world.isClient || Thread.currentThread() != thread) return;
        if (depth > 0) depth--;
    }

    public static void exitChange(World world) {
        exit(world);
    }

    private static void setOrigin(World world, BlockPos pos) {
        originWorld = world;
        originX = pos.getX();
        originY = pos.getY();
        originZ = pos.getZ();
        originPlayer = null;
    }

    /** @return true if nothing is going on: every origin entered was left */
    public static boolean isIdle() {
        return depth == 0;
    }

    /**
     * Whatever is going on has no origin any more. Between two ticks: nothing is going on, an action cut short by
     * an error leaves no origin behind. When a session begins or ends: what begins or ends it (a player's click, a
     * block's tick) goes on as it would without the rule, and is not held to a side that just appeared or vanished.
     */
    static void resetOrigin() {
        depth = 0;
        originPlayer = null;
        originWorld = null;
    }

    /** The side what is going on started on: looked up every time, sessions and their players come and go. */
    private static @Nullable ZoneBubble originSide() {
        return originPlayer != null ? ZoneBubbles.sideOf(originPlayer) : ZoneBubbles.at(originWorld, originX, originY, originZ);
    }

    /** @return true if what is going on started on the other side of a border from {@code x y z}, or by hands that are tied */
    private static boolean fromOtherSide(World world, int x, int y, int z) {
        if (depth == 0 || bypass > 0 || world != originWorld) return false;
        return fromOtherSide(ZoneBubbles.at(world, x, y, z));
    }

    /** Like {@link #fromOtherSide(World, int, int, int)}, the side of the place already looked up ({@code here}). */
    private static boolean fromOtherSide(World world, @Nullable ZoneBubble here) {
        if (depth == 0 || bypass > 0 || world != originWorld) return false;
        return fromOtherSide(here);
    }

    private static boolean fromOtherSide(@Nullable ZoneBubble here) {
        if (originPlayer != null && originAction && ZoneBubbles.handsTied(originPlayer)) return true;
        return here != originSide();
    }

    // ------------------------------------------------------------------ blocks

    /**
     * A block of a loaded chunk is about to change: remembered if it is the first change of a position of a zone.
     *
     * @return true if the change must not happen (it comes from the other side, or the journal is full)
     */
    public static boolean onBlockChange(WorldChunk chunk, BlockPos pos, BlockState state) {
        World world = chunk.getWorld();
        if (world.isClient || Thread.currentThread() != thread) return false;
        ZoneBubble bubble = ZoneBubbles.at(world, pos.getX(), pos.getY(), pos.getZ());
        if (fromOtherSide(world, bubble)) return true;
        if (bubble == null) return false;
        BlockState old = chunk.getBlockState(pos);
        if (old == state) return false;
        // What the server forbids in a zone does not appear in one in session, whatever would put it there
        if (bypass == 0 && bubble.isActive() && old.getBlock() != state.getBlock() && ZoneForbidden.isForbidden(state)) {
            if (originPlayer != null && depth > 0) ZoneBubbles.warn(originPlayer, "forbidden_here");
            return true;
        }
        return !bubble.journal(pos.asLong(), old, bypass > 0);
    }

    /** @return true if the two positions are not on the same side of a border */
    public static boolean across(World world, BlockPos a, BlockPos b) {
        if (world.isClient || bypass > 0 || Thread.currentThread() != thread) return false;
        return ZoneBubbles.at(world, a.getX(), a.getY(), a.getZ()) != ZoneBubbles.at(world, b.getX(), b.getY(), b.getZ());
    }

    /** @return true if a piston at {@code piston} may not move these blocks: one of them would cross a border */
    public static boolean blocksPush(World world, BlockPos piston, BlockPos head, List<BlockPos> moved, List<BlockPos> broken, Direction motion) {
        if (world.isClient || bypass > 0 || Thread.currentThread() != thread) return false;
        ZoneBubble side = ZoneBubbles.at(world, piston.getX(), piston.getY(), piston.getZ());
        if (ZoneBubbles.at(world, head.getX(), head.getY(), head.getZ()) != side) return true;
        for (BlockPos pos : moved) {
            if (ZoneBubbles.at(world, pos.getX(), pos.getY(), pos.getZ()) != side) return true;
            if (ZoneBubbles.at(world, pos.getX() + motion.getOffsetX(), pos.getY() + motion.getOffsetY(), pos.getZ() + motion.getOffsetZ()) != side) return true;
        }
        for (BlockPos pos : broken) {
            if (ZoneBubbles.at(world, pos.getX(), pos.getY(), pos.getZ()) != side) return true;
        }
        return false;
    }

    /** The blocks an explosion at {@code origin} destroys: only those on its side of the border. */
    public static List<BlockPos> explosionBlocks(ServerWorld world, Vec3d origin, List<BlockPos> blocks) {
        if (bypass > 0 || Thread.currentThread() != thread) return blocks;
        ZoneBubble side = ZoneBubbles.at(world, MathHelper.floor(origin.x), MathHelper.floor(origin.y), MathHelper.floor(origin.z));
        List<BlockPos> kept = null;
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i);
            boolean same = ZoneBubbles.at(world, pos.getX(), pos.getY(), pos.getZ()) == side;
            if (!same && kept == null) kept = new ArrayList<>(blocks.subList(0, i));
            else if (same && kept != null) kept.add(pos);
        }
        return kept == null ? blocks : kept;
    }

    // ------------------------------------------------------------------ entities

    /**
     * An entity (players aside: they are sent back, see {@link ZoneBubbles}) is about to stand at {@code x y z}.
     *
     * @return where it stands instead if that would take it across a border, null if it may
     */
    public static @Nullable Vec3d wall(Entity entity, double x, double y, double z) {
        World world = entity.getWorld();
        if (world.isClient || bypass > 0 || entity instanceof PlayerEntity) return null;
        BlockPos old = entity.getBlockPos();
        int nx = MathHelper.floor(x), ny = MathHelper.floor(y), nz = MathHelper.floor(z);
        if (nx == old.getX() && ny == old.getY() && nz == old.getZ()) return null;
        if (Thread.currentThread() != thread) return null;
        ZoneBubble from = ZoneBubbles.at(world, old.getX(), old.getY(), old.getZ());
        ZoneBubble to = ZoneBubbles.at(world, nx, ny, nz);
        if (from == to) return null;
        // an entity being made (not in the world yet) is put where it starts
        if (((ServerWorld) world).getEntityById(entity.getId()) != entity) return null;
        double fx = x, fy = y, fz = z;
        if (from != null) {
            fx = from.clampX(x);
            fy = from.clampY(y);
            fz = from.clampZ(z);
        } else {
            // kept out along the axes it was out on
            fx = to.keepOutX(old.getX(), x);
            fy = to.keepOutY(old.getY(), y);
            fz = to.keepOutZ(old.getZ(), z);
        }
        Vec3d velocity = entity.getVelocity();
        entity.setVelocity(fx == x ? velocity.x : 0, fy == y ? velocity.y : 0, fz == z ? velocity.z : 0);
        return new Vec3d(fx, fy, fz);
    }

    /** @return true if an entity may not be added to the world: what makes it started on the other side, or it is forbidden in the zone it would be in */
    public static boolean blocksSpawn(ServerWorld world, Entity entity) {
        if (Thread.currentThread() != thread) return false;
        BlockPos pos = entity.getBlockPos();
        ZoneBubble bubble = ZoneBubbles.at(world, pos.getX(), pos.getY(), pos.getZ());
        if (bubble != null) bubble.onSpawn(entity);
        if (bypass > 0) return false;
        if (fromOtherSide(world, bubble)) return true;
        // What the server forbids in a zone does not spawn in one in session, whatever makes it (egg, dispenser, spawner, mod)
        return bubble != null && bubble.isActive() && ZoneForbidden.isForbidden(entity.getType());
    }

    /** @return true if the portal block at {@code pos} takes nobody anywhere: it is in a zone in session */
    public static boolean blocksPortal(Entity entity, BlockPos pos) {
        World world = entity.getWorld();
        if (world.isClient || bypass > 0 || Thread.currentThread() != thread) return false;
        return ZoneBubbles.at(world, pos.getX(), pos.getY(), pos.getZ()) != null;
    }

    /** @return true if a mob (the entity ticking) may not take this item: it lies on the other side */
    public static boolean blocksMobPickup(ItemEntity item) {
        if (depth == 0 || originPlayer != null || Thread.currentThread() != thread) return false;
        BlockPos pos = item.getBlockPos();
        return fromOtherSide(item.getWorld(), pos.getX(), pos.getY(), pos.getZ());
    }

    // ------------------------------------------------------------------ players

    /** @return true if a teleport of a player must not happen: out of the zone it plays in, or into one it doesn't */
    public static boolean blocksTeleport(ServerPlayerEntity player, TeleportTarget target) {
        if (bypass > 0 || allow > 0 || Thread.currentThread() != thread) return false;
        // a relative move is judged where it ends (the player is sent back if need be)
        if (!target.relatives().isEmpty()) return false;
        Vec3d pos = target.position();
        ZoneBubble to = ZoneBubbles.at(target.world(), MathHelper.floor(pos.x), MathHelper.floor(pos.y), MathHelper.floor(pos.z));
        if (ZoneBubbles.blocksTeleport(player, to)) {
            ZoneBubbles.warn(player, to == null || to.isMember(player.getUuid()) ? "cannot_leave" : "cannot_enter");
            return true;
        }
        return false;
    }

    /** @return true if what a player drops is destroyed instead: session items can't lie out of their zone */
    public static boolean blocksDrop(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity server) || Thread.currentThread() != thread) return false;
        // what a screen closed while the session inventory is taken back gives back (a crafting table's grid, an
        // anvil's input...) is of the session: it never lies anywhere, whether the player stands in its zone or not
        if (discardDrops > 0) return true;
        if (bypass > 0) return false;
        return ZoneBubbles.blocksDrop(server);
    }

    /** Runs what destroys a player's session inventory: whatever the player drops meanwhile is destroyed. */
    static void discardingDrops(Runnable action) {
        discardDrops++;
        try {
            action.run();
        } finally {
            discardDrops--;
        }
    }

    // ------------------------------------------------------------------ hurting across

    private static @Nullable World explosionWorld;
    private static @Nullable Vec3d explosionPos;

    /** An explosion hurts the entities around it (null, null: it is done). */
    public static void explosionAt(@Nullable World world, @Nullable Vec3d pos) {
        explosionWorld = world;
        explosionPos = pos;
    }

    /** The side of the border an entity is on: for a player, the zone it plays in (none if its hands are tied). */
    private static @Nullable ZoneBubble sideOf(Entity entity) {
        if (entity instanceof ServerPlayerEntity player) return ZoneBubbles.sideOf(player);
        BlockPos pos = entity.getBlockPos();
        return ZoneBubbles.at(entity.getWorld(), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * @return true if an entity is not hurt: what hurts it (who, or else what, or else where it happened: an
     * explosion, an arrow, a splash, a cloud) is on the other side of a border; or it is a piece of what goes on
     * beyond its zone (a token of a running party), which the session must not touch. {@code /kill} and the void pass.
     */
    public static boolean blocksDamage(LivingEntity victim, DamageSource source) {
        World world = victim.getWorld();
        if (world.isClient || bypass > 0 || Thread.currentThread() != thread) return false;
        if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) return false;
        ZoneBubble side = sideOf(victim);
        if (!(victim instanceof PlayerEntity) && side != null && ZoneBubbles.isKeptLive(victim)) return true;
        Entity cause = source.getAttacker() != null ? source.getAttacker() : source.getSource();
        if (cause != null) return cause.getWorld() == world && sideOf(cause) != side;
        Vec3d at = source.getPosition();
        if (at == null && explosionWorld == world && source.isIn(DamageTypeTags.IS_EXPLOSION)) at = explosionPos;
        return at != null && ZoneBubbles.at(world, MathHelper.floor(at.x), MathHelper.floor(at.y), MathHelper.floor(at.z)) != side;
    }

    /** @return true if an effect (a potion, a cloud, an arrow's) is not given: what gives it is on the other side of a border */
    public static boolean blocksEffect(LivingEntity target, Entity source) {
        if (target.getWorld().isClient || bypass > 0 || Thread.currentThread() != thread) return false;
        return source.getWorld() == target.getWorld() && sideOf(source) != sideOf(target);
    }

    /**
     * @return true if a projectile hits nothing there: the block or the entity it reaches is on the other side of a
     * border (the move that would take it there stops at the border anyway: {@link #wall})
     */
    public static boolean blocksHit(Entity projectile, HitResult hit) {
        World world = projectile.getWorld();
        if (world.isClient || bypass > 0 || Thread.currentThread() != thread) return false;
        BlockPos from = projectile.getBlockPos();
        ZoneBubble side = ZoneBubbles.at(world, from.getX(), from.getY(), from.getZ());
        if (hit instanceof EntityHitResult entityHit) return sideOf(entityHit.getEntity()) != side;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            return ZoneBubbles.at(world, pos.getX(), pos.getY(), pos.getZ()) != side;
        }
        return false;
    }

    /** @return true if a player may not pick this entity up (item, experience orb, arrow) */
    public static boolean blocksPickup(PlayerEntity player, Entity entity) {
        if (bypass > 0 || !(player instanceof ServerPlayerEntity server) || Thread.currentThread() != thread) return false;
        if (!ZoneBubbles.canTouch(server, entity.getWorld(), entity.getBlockPos())) return true;
        if (entity instanceof ItemEntity item && ZoneBubbles.blocksItem(server, item.getStack())) {
            ZoneBubbles.warn(server, "forbidden_item");
            return true;
        }
        return false;
    }

    /** @return true if a player may not take what this slot holds: an item forbidden to sessions, in a container, and the player is of one */
    public static boolean blocksSlot(PlayerEntity player, Slot slot) {
        if (bypass > 0 || !(player instanceof ServerPlayerEntity server) || slot.inventory == player.getInventory()) return false;
        if (!ZoneBubbles.blocksItem(server, slot.getStack())) return false;
        ZoneBubbles.warn(server, "forbidden_item");
        return true;
    }
}
