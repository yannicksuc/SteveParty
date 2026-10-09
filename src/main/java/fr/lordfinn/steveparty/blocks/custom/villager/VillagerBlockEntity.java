package fr.lordfinn.steveparty.blocks.custom.villager;

import fr.lordfinn.steveparty.blocks.SyncedBlockEntity;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerReaction.P;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.BlockPositionSource;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
import net.minecraft.world.event.listener.GameEventListener;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The villager block's brain (server) and animation state (clients).
 * <p>
 * <b>Server</b>: decides the reactions from what the players around do. Three kinds of senses:
 * <ul>
 *   <li>the block's own methods (right-click, stepped on, landed on, projectile) and {@link VillagerBlockEvents}
 *   (punches, chat) call {@link #onRightClick}, {@link #onPunched}, {@link #react};</li>
 *   <li>a game event listener (like a sculk sensor, {@link #getEventListener()}): note blocks, jukeboxes, bells,
 *   goat horns, lightning, explosions and fireworks, players hurt, teleporting, shooting, building around it,
 *   putting armour on, monsters dying;</li>
 *   <li>a look at the players around every 2 ticks (coming, leaving, staring, sneaking, jumping, eating,
 *   holding something, dying) and at the surroundings every second (mobs, thrown items, time, weather, idle), only
 *   when a player is within {@value #WATCH_RANGE} blocks (nobody to see it otherwise).</li>
 * </ul>
 * {@link #react} applies the priority and cooldown rules (see {@link VillagerReaction}) and sends the reaction to
 * the clients with a block event (the vanilla way, like note blocks and chests: one tiny packet to the players
 * around). The lasting {@link VillagerMode} (asleep, dancing) is synced with the block entity data.
 * <p>
 * <b>Client</b>: plays the reaction's sounds and particles tick by tick ({@link VillagerReactionEffects}), follows
 * the nearest player (or the reaction's target) with its face, blinks; the client renderer turns that into a pose.
 */
public class VillagerBlockEntity extends SyncedBlockEntity implements GameEventListener.Holder<VillagerBlockEntity.Listener> {
    public static final int WATCH_RANGE = 16;
    public static final double GREET_RANGE = 4.5;
    public static final double LEAVE_RANGE = 8;
    /** A player coming back within this many ticks after leaving gets a "you again?" instead of a greeting. */
    public static final int RETURN_WINDOW = 300;
    /** Leaving within this many ticks after being greeted, without interacting: it sulks. */
    public static final int SULK_WINDOW = 60;
    public static final int STARE_SHY_TICKS = 60;
    public static final int STARE_CONTEST_TICKS = 180;
    public static final int SNEAK_SUSPICIOUS_TICKS = 30;
    /** Right-clicks: annoyed at 5 in 3 s, dizzy at 10 in 5 s. */
    public static final int ANNOYED_CLICKS = 5, ANNOYED_WINDOW = 60, DIZZY_CLICKS = 10, DIZZY_WINDOW = 100;
    /** Punches: grumpy at 3 in 4 s, faint at 6 in 6 s; at most one punch counted every 5 ticks. */
    public static final int GRUMPY_PUNCHES = 3, GRUMPY_WINDOW = 80, FAINT_PUNCHES = 6, FAINT_WINDOW = 120, PUNCH_RATE = 5;
    /** The easter egg every stack of right-clicks. */
    public static final int POKES_PER_STACK = 64;
    /** Quiet time after a reaction before an idle bit, and between idle bits (plus up to {@link #IDLE_JITTER}). */
    public static final int IDLE_QUIET = 100, IDLE_GAP = 200, IDLE_JITTER = 300;
    /** Ticks without being walked on after which a step onto it squishes it again. */
    public static final int STEP_GAP = 40;

    private final Listener listener;

    // --- Shared ---
    private VillagerMode mode = VillagerMode.NONE;
    @Nullable
    private VillagerReaction reaction;
    private int targetId = -1;

    // --- Server ---
    private long reactionStart;
    private long lastReactionEnd;
    private long nextIdleAt;
    private final long[] nextAllowed = new long[VillagerReaction.count()];
    /** How many times each reaction started (for the tests and debugging; not saved). */
    private final int[] started = new int[VillagerReaction.count()];
    private final long[] clicks = new long[16];
    private int clickIndex;
    private final long[] punches = new long[8];
    private int punchIndex;
    private long lastPunch = Long.MIN_VALUE / 2;
    private int totalPokes;
    private long lastStep = Long.MIN_VALUE / 2;
    private final Map<UUID, Watcher> watchers = new HashMap<>(4);
    private double nearestPlayerSq = Double.MAX_VALUE;
    @Nullable
    private BlockPos jukeboxPos;
    private boolean cousinSeen;
    /** The squashed villager's data (see {@link VillagerSoul}), null for a villager block made otherwise. */
    @Nullable
    private NbtCompound soul;
    private boolean carryChecked;

    // --- Client ---
    /**
     * The client's breaking progress of a block (0..9, -1 when nobody breaks it), set by the client (the world
     * renderer knows every player's breaking progress); nothing on a server.
     */
    public static ToIntFunction<BlockPos> miningStageProbe = pos -> -1;
    private int miningStage = -1;
    private int clientAge;
    private int modeAge;
    private float yaw, prevYaw, tilt, prevTilt;
    private int blinkTimer = 60, blinkTicks;

    public VillagerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VILLAGER_BLOCK_ENTITY, pos, state);
        this.listener = new Listener(this);
        // clicks/punches "long ago"
        Arrays.fill(clicks, Long.MIN_VALUE / 2);
        Arrays.fill(punches, Long.MIN_VALUE / 2);
    }

    // =============================================================================================================
    // Reactions (server)
    // =============================================================================================================

    /**
     * Starts {@code reaction} if its cooldown is over and nothing more important is playing (see
     * {@link VillagerReaction}). Asleep, it only wakes up for alarms, and for interactions (which then startle it
     * awake instead); the rest is ignored.
     *
     * @param target who/what caused it (the face turns to it; a player gets the reaction's action bar line)
     * @return whether it started
     */
    public boolean react(VillagerReaction reaction, @Nullable Entity target) {
        if (world == null || world.isClient) return false;
        long now = world.getTime();
        boolean wakes = false;
        if (mode == VillagerMode.SLEEP && reaction != VillagerReaction.SLEEP_TALK) {
            if (reaction.priority < P.INTERACT) return false;
            if (reaction.priority < P.ALARM) reaction = VillagerReaction.WAKE_STARTLED;
            wakes = true;
        }
        if (now < nextAllowed[reaction.ordinal()]) return false;
        if (isReacting()) {
            VillagerReaction current = this.reaction;
            if (reaction.priority < current.priority) return false;
            if (reaction.priority == current.priority && !(reaction == current && reaction.restartable)) return false;
        }
        if (wakes) setMode(VillagerMode.NONE);
        this.reaction = reaction;
        this.reactionStart = now;
        this.targetId = target != null ? target.getId() : -1;
        nextAllowed[reaction.ordinal()] = now + reaction.cooldown;
        started[reaction.ordinal()]++;
        world.addSyncedBlockEvent(pos, getCachedState().getBlock(), reaction.ordinal() + 1, targetId);
        if (reaction.hasMessage() && target instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.translatable(reaction.messageKey()), true);
        }
        return true;
    }

    /** Server, operator command: plays {@code reaction} now, whatever its cooldown and what is playing. */
    public void forceReact(VillagerReaction reaction, @Nullable Entity target) {
        if (world == null || world.isClient) return;
        this.reaction = null;
        nextAllowed[reaction.ordinal()] = 0;
        if (reaction != VillagerReaction.SLEEP_TALK && mode == VillagerMode.SLEEP) setMode(VillagerMode.NONE);
        react(reaction, target);
    }

    /** The reaction playing (server: until its duration is over; client: the one being animated). */
    @Nullable
    public VillagerReaction getReaction() {
        return isReacting() ? reaction : null;
    }

    /** Server: whether {@code reaction} is still in its cooldown. */
    public boolean isCoolingDown(VillagerReaction reaction) {
        return world != null && world.getTime() < nextAllowed[reaction.ordinal()];
    }

    /** Server: how many times {@code reaction} started since this block entity was created. */
    public int timesStarted(VillagerReaction reaction) {
        return started[reaction.ordinal()];
    }

    public boolean isReacting() {
        if (reaction == null || world == null) return false;
        return world.isClient ? clientAge < reaction.duration : world.getTime() - reactionStart < reaction.duration;
    }

    public VillagerMode getMode() {
        return mode;
    }

    /** Server: sets the lasting mode, synced to the clients with the block entity data. */
    public void setMode(VillagerMode mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        if (!mode.isDancing()) jukeboxPos = null;
        markDirty();
        syncToClients();
    }

    public int getTotalPokes() {
        return totalPokes;
    }

    /** Server: a right-click (any hand, any item) that made {@code reaction}; counts the clicks for the spam. */
    public void onRightClick(PlayerEntity player, VillagerReaction reaction) {
        if (world == null || world.isClient) return;
        long now = world.getTime();
        clicks[clickIndex] = now;
        clickIndex = (clickIndex + 1) % clicks.length;
        totalPokes++;
        markDirty();
        Watcher watcher = watchers.get(player.getUuid());
        if (watcher != null) watcher.interacted = true;
        if (totalPokes % POKES_PER_STACK == 0 && react(VillagerReaction.STACK_OF_POKES, player)) return;
        if (countSince(clicks, now - DIZZY_WINDOW) >= DIZZY_CLICKS) {
            if (react(VillagerReaction.DIZZY, player)) Arrays.fill(clicks, Long.MIN_VALUE / 2);
            return;
        }
        if (countSince(clicks, now - ANNOYED_WINDOW) >= ANNOYED_CLICKS && react(VillagerReaction.ANNOYED, player)) return;
        react(reaction, player);
    }

    /** Server: punched where it can't be broken. */
    public void onPunched(PlayerEntity player) {
        if (world == null || world.isClient) return;
        long now = world.getTime();
        // holding the attack button hits every tick: count one punch every few ticks
        if (now - lastPunch < PUNCH_RATE) return;
        lastPunch = now;
        punches[punchIndex] = now;
        punchIndex = (punchIndex + 1) % punches.length;
        Watcher watcher = watchers.get(player.getUuid());
        if (watcher != null) watcher.interacted = true;
        if (countSince(punches, now - FAINT_WINDOW) >= FAINT_PUNCHES) {
            if (react(VillagerReaction.FAINT, player)) Arrays.fill(punches, Long.MIN_VALUE / 2);
            return;
        }
        if (countSince(punches, now - GRUMPY_WINDOW) >= GRUMPY_PUNCHES && react(VillagerReaction.GRUMPY, player)) return;
        react(VillagerReaction.OUCH, player);
    }

    /**
     * Server: a player walking on it (called on each of their moves while on it): squished when they step onto it,
     * not all along the walk.
     */
    public void onSteppedOn(PlayerEntity player) {
        if (world == null || world.isClient) return;
        long now = world.getTime();
        boolean steppedOnto = now - lastStep > STEP_GAP;
        lastStep = now;
        if (steppedOnto) react(VillagerReaction.SQUISHED, player);
    }

    private static int countSince(long[] times, long since) {
        int n = 0;
        for (long t : times) if (t >= since) n++;
        return n;
    }

    // =============================================================================================================
    // Senses (server)
    // =============================================================================================================

    public void serverTick(ServerWorld world) {
        if (!carryChecked) {
            // moved by a piston: its data comes along (pistons move blocks without their block entity)
            carryChecked = true;
            NbtCompound carried = VillagerSoul.take(world, pos);
            if (carried != null) readKeptData(carried);
        }
        long now = world.getTime();
        if (reaction != null && now - reactionStart >= reaction.duration) {
            reaction = null;
            lastReactionEnd = now;
        }
        int phase = phase();
        if (((now + phase) & 1) != 0) return;
        boolean anyone = watchPlayers(world, now);
        if ((now + phase) % 20 == 0) watchSurroundings(world, now, anyone);
    }

    private int phase() {
        return (int) (pos.asLong() * 31 & 0xFF);
    }

    private double cx() {
        return pos.getX() + 0.5;
    }

    private double cy() {
        return pos.getY() + 0.5;
    }

    private double cz() {
        return pos.getZ() + 0.5;
    }

    /** @return whether a player is within {@link #WATCH_RANGE} */
    private boolean watchPlayers(ServerWorld world, long now) {
        boolean anyone = false;
        double nearest = Double.MAX_VALUE;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            double d2 = player.squaredDistanceTo(cx(), cy(), cz());
            Watcher watcher = watchers.get(player.getUuid());
            if (d2 > WATCH_RANGE * WATCH_RANGE) {
                if (watcher != null && watcher.near) leave(watcher, player, now);
                continue;
            }
            anyone = true;
            nearest = Math.min(nearest, d2);
            if (watcher == null) {
                watcher = new Watcher(player);
                watchers.put(player.getUuid(), watcher);
            }
            watcher.lastSeen = now;
            observe(player, watcher, Math.sqrt(d2), now);
        }
        nearestPlayerSq = nearest;
        return anyone;
    }

    private void observe(ServerPlayerEntity player, Watcher w, double d, long now) {
        boolean dead = player.isDead();
        if (dead && !w.wasDead && d < 12) react(VillagerReaction.MOURN, player);
        w.wasDead = dead;
        if (dead) return;

        // Coming and going
        if (!w.near && d < GREET_RANGE) {
            w.near = true;
            w.nearSince = now;
            w.interacted = false;
            w.greetedAt = -1;
            if (mode != VillagerMode.SLEEP) {
                if (now - w.lastLeft < RETURN_WINDOW) react(VillagerReaction.RETURN, player);
                else if (react(VillagerReaction.GREET, player)) w.greetedAt = now;
            }
        } else if (w.near && d > LEAVE_RANGE) {
            leave(w, player, now);
        }
        if (mode == VillagerMode.SLEEP) {
            // only a close approach wakes it up
            if (d < 2.5) react(VillagerReaction.WAKE_STARTLED, player);
            if (mode == VillagerMode.SLEEP) {
                w.resetFrom(player);
                return;
            }
        }

        // Jumping in front of it: hops along, a jump party after 3 jumps
        boolean onGround = player.isOnGround();
        if (w.wasOnGround && !onGround && player.getY() > w.lastY + 0.05 && d < 5
                && !player.getAbilities().flying && !player.isTouchingWater() && !player.hasVehicle()) {
            w.jumps[w.jumpIndex] = now;
            w.jumpIndex = (w.jumpIndex + 1) % w.jumps.length;
            if (countSince(w.jumps, now - 60) >= 3) {
                if (react(VillagerReaction.JUMP_PARTY, player)) Arrays.fill(w.jumps, Long.MIN_VALUE / 2);
            } else {
                react(VillagerReaction.JUMP_ALONG, player);
            }
        }
        w.wasOnGround = onGround;
        w.lastY = player.getY();

        // Crouch-spam: bows back; sneaking close: suspicious
        boolean sneaking = player.isSneaking();
        if (sneaking && !w.wasSneaking && d < 5) {
            w.sneaks[w.sneakIndex] = now;
            w.sneakIndex = (w.sneakIndex + 1) % w.sneaks.length;
            if (countSince(w.sneaks, now - 40) >= 4 && react(VillagerReaction.BOW, player)) {
                Arrays.fill(w.sneaks, Long.MIN_VALUE / 2);
            }
        }
        w.wasSneaking = sneaking;
        if (sneaking && d < 3.5) {
            w.sneakTicks += 2;
            if (w.sneakTicks >= SNEAK_SUSPICIOUS_TICKS && !w.suspiciousDone) {
                w.suspiciousDone = true;
                react(VillagerReaction.SUSPICIOUS, player);
            }
        } else {
            w.sneakTicks = 0;
            w.suspiciousDone = false;
        }

        // Staring at it
        if (d < 6 && isLookingAt(player, 0.97)) {
            w.stareTicks += 2;
            if (w.stareTicks >= STARE_SHY_TICKS && !w.shyDone) {
                w.shyDone = true;
                react(VillagerReaction.STARE_SHY, player);
            }
            if (w.stareTicks >= STARE_CONTEST_TICKS && !w.contestDone) {
                w.contestDone = true;
                react(VillagerReaction.STARE_CONTEST, player);
            }
        } else {
            w.stareTicks = 0;
            w.shyDone = false;
            w.contestDone = false;
        }

        // Showing it something
        if (d < 5 && isLookingAt(player, 0.9)) {
            Item held = player.getMainHandStack().getItem();
            if (held != w.lastShown) {
                w.lastShown = held;
                VillagerReaction shown = heldReaction(player.getMainHandStack());
                if (shown != null) react(shown, player);
            }
        } else {
            w.lastShown = null;
        }

        // Eating, drinking
        if (player.isUsingItem() && d < 6) {
            if (!w.wasUsing) {
                UseAction action = player.getActiveItem().getUseAction();
                if (action == UseAction.EAT) react(VillagerReaction.HUNGRY, player);
                else if (action == UseAction.DRINK) react(VillagerReaction.CURIOUS_POTION, player);
            }
            w.wasUsing = true;
        } else {
            w.wasUsing = false;
        }

        // "hmm" in the chat
        int chats = VillagerBlockEvents.chatCount(player);
        if (chats != w.chatCount) {
            w.chatCount = chats;
            if (d < 8) react(VillagerReaction.CHAT_HMM, player);
        }
    }

    private void leave(Watcher w, PlayerEntity player, long now) {
        w.near = false;
        w.lastLeft = now;
        if (mode == VillagerMode.SLEEP) return;
        long stayed = now - w.nearSince;
        if (w.greetedAt >= 0 && stayed < SULK_WINDOW && !w.interacted) react(VillagerReaction.SULK, player);
        else if (stayed >= SULK_WINDOW) react(VillagerReaction.BYE, player);
    }

    /** Whether the player's view points at the block (cosine of the angle to its centre above {@code threshold}). */
    private boolean isLookingAt(PlayerEntity player, double threshold) {
        float yawRad = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        float pitchRad = player.getPitch() * MathHelper.RADIANS_PER_DEGREE;
        double cosPitch = MathHelper.cos(pitchRad);
        double lx = -MathHelper.sin(yawRad) * cosPitch, ly = -MathHelper.sin(pitchRad), lz = MathHelper.cos(yawRad) * cosPitch;
        double dx = cx() - player.getX(), dy = cy() - player.getEyeY(), dz = cz() - player.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return len > 1.0E-4 && (lx * dx + ly * dy + lz * dz) / len > threshold;
    }

    /** What it does when shown (held in front of it) {@code stack}, or null. */
    @Nullable
    public static VillagerReaction heldReaction(ItemStack stack) {
        if (VillagerBlockUse.isWeapon(stack)) return VillagerReaction.WEAPON_SCARED;
        if (VillagerBlockUse.isTreasure(stack)) return VillagerReaction.GREEDY;
        if (stack.isOf(ModBlocks.VILLAGER_BLOCK.asItem())) return VillagerReaction.MIRROR;
        return null;
    }

    private void watchSurroundings(ServerWorld world, long now, boolean anyone) {
        if (!watchers.isEmpty()) {
            // players gone (disconnected, other dimension)
            watchers.values().removeIf(w -> now - w.lastSeen > 40);
        }

        // Night and day
        boolean sleepTime = isSleepTime(world);
        if (mode == VillagerMode.SLEEP && !sleepTime) {
            setMode(VillagerMode.NONE);
            react(VillagerReaction.MORNING_STRETCH, null);
        } else if (mode == VillagerMode.NONE && sleepTime && nearestPlayerSq > 5 * 5 && !isReacting()) {
            react(VillagerReaction.YAWN, null);
            setMode(VillagerMode.SLEEP);
        }
        // Dancing while the jukebox plays
        if (mode.isDancing() && !jukeboxPlaying(world)) setMode(VillagerMode.NONE);

        if (!anyone) return;
        var random = world.getRandom();
        if (mode == VillagerMode.SLEEP) {
            if (random.nextInt(12) == 0) react(VillagerReaction.SLEEP_TALK, null);
            return;
        }
        if (world.isRaining() && world.hasRain(pos.up()) && random.nextInt(8) == 0) {
            react(VillagerReaction.RAIN_GRUMBLE, null);
        }
        watchNeighbours(world, now);

        if (mode == VillagerMode.NONE && !isReacting() && now >= nextIdleAt && now - lastReactionEnd >= IDLE_QUIET) {
            react(pickIdle(random.nextInt(1000), sleepTime), null);
            nextIdleAt = now + IDLE_GAP + random.nextInt(IDLE_JITTER);
        }
    }

    /** The idle bit for a roll in [0, 1000): common ones, a rare spin (3%), rarer breakdance and disguise (1% each). */
    public static VillagerReaction pickIdle(int roll, boolean night) {
        if (roll < 10) return VillagerReaction.IDLE_BREAKDANCE;
        if (roll < 20) return VillagerReaction.IDLE_DISGUISE;
        if (roll < 50) return VillagerReaction.IDLE_SPIN;
        if (night && roll < 250) return VillagerReaction.YAWN;
        VillagerReaction[] common = {VillagerReaction.IDLE_LOOK_AROUND, VillagerReaction.IDLE_WHISTLE,
                VillagerReaction.IDLE_NOSE_WIGGLE, VillagerReaction.IDLE_SNEEZE, VillagerReaction.IDLE_HUM,
                VillagerReaction.IDLE_HICCUP};
        return common[roll % common.length];
    }

    /** Night, in a dimension with a day cycle. */
    public static boolean isSleepTime(World world) {
        return !world.getDimension().hasFixedTime() && isSleepTime(world.getTimeOfDay());
    }

    public static boolean isSleepTime(long timeOfDay) {
        long t = Math.floorMod(timeOfDay, 24000L);
        return t >= 12600 && t < 23400;
    }

    private boolean jukeboxPlaying(World world) {
        return jukeboxPos != null && world.isChunkLoaded(jukeboxPos)
                && world.getBlockEntity(jukeboxPos) instanceof JukeboxBlockEntity jukebox && jukebox.getManager().isPlaying();
    }

    /** Mobs and thrown items around: panics, hearts, gossip... one reaction, the most important. */
    private void watchNeighbours(ServerWorld world, long now) {
        List<Entity> around = world.getOtherEntities(null, new Box(pos).expand(8),
                e -> (e instanceof MobEntity || e instanceof ItemEntity) && e.isAlive() && !isToken(e));
        Entity threat = null, cousin = null, item = null, cat = null, golem = null, villager = null, trader = null;
        for (Entity e : around) {
            double d2 = e.squaredDistanceTo(cx(), cy(), cz());
            if (e instanceof ZombieVillagerEntity && d2 < 36 && !cousinSeen) cousin = e;
            else if ((e instanceof ZombieEntity || e instanceof IllagerEntity || e instanceof RavagerEntity
                    || e instanceof VexEntity) && d2 < 64) threat = e;
            else if (e instanceof ItemEntity drop && d2 < 16 && drop.getItemAge() < 40
                    && drop.getOwner() instanceof PlayerEntity) item = e;
            else if (e instanceof CatEntity && d2 < 25) cat = e;
            else if (e instanceof IronGolemEntity && d2 < 64) golem = e;
            else if ((e instanceof VillagerEntity || e instanceof BoxedTraderEntity) && d2 < 25) villager = e;
            else if (e instanceof WanderingTraderEntity && d2 < 36) trader = e;
        }
        if (cousin != null && world.getRandom().nextBoolean()) {
            cousinSeen = react(VillagerReaction.COUSIN, cousin);
            if (cousinSeen) return;
        }
        if (cousin != null && threat == null) threat = cousin;
        if (threat != null && react(VillagerReaction.ZOMBIE_PANIC, threat)) return;
        if (item != null) {
            ItemStack stack = ((ItemEntity) item).getStack();
            if (react(VillagerBlockUse.isTreasure(stack) ? VillagerReaction.GREEDY : VillagerReaction.CATCH_ITEM, item)) return;
        }
        if (cat != null && react(VillagerReaction.CAT_LOVE, cat)) return;
        if (golem != null && react(VillagerReaction.GOLEM_HERO, golem)) return;
        if (villager != null && react(VillagerReaction.GOSSIP, villager)) return;
        if (trader != null) react(VillagerReaction.TRADER_JEALOUS, trader);
    }

    private static boolean isToken(Entity entity) {
        return entity instanceof TokenizedEntityInterface token && token.steveparty$isTokenized();
    }

    /** A game event heard by {@link #listener}. */
    public boolean onGameEvent(ServerWorld world, RegistryEntry<GameEvent> event, @Nullable Entity source, Vec3d at) {
        double d2 = at.squaredDistanceTo(cx(), cy(), cz());
        BlockPos from = BlockPos.ofFloored(at);
        PlayerEntity player = source instanceof PlayerEntity p && !p.isSpectator() ? p : null;
        if (event.matches(GameEvent.NOTE_BLOCK_PLAY)) {
            return react(VillagerReaction.NOTE_BOB, null);
        } else if (event.matches(GameEvent.JUKEBOX_PLAY)) {
            if (mode == VillagerMode.SLEEP) return false;
            boolean funky = world.getBlockEntity(from) instanceof JukeboxBlockEntity jukebox
                    && jukebox.getStack().isOf(Items.MUSIC_DISC_PIGSTEP);
            setMode(funky ? VillagerMode.DANCE_FUNKY : VillagerMode.DANCE);
            jukeboxPos = from.toImmutable();
            return true;
        } else if (event.matches(GameEvent.JUKEBOX_STOP_PLAY)) {
            if (mode.isDancing() && from.equals(jukeboxPos)) setMode(VillagerMode.NONE);
            return true;
        } else if (event.matches(GameEvent.BLOCK_CHANGE)) {
            return world.getBlockState(from).isOf(Blocks.BELL) && react(VillagerReaction.BELL_ALARM, source);
        } else if (event.matches(GameEvent.INSTRUMENT_PLAY)) {
            return react(VillagerReaction.BELL_ALARM, source);
        } else if (event.matches(GameEvent.LIGHTNING_STRIKE)) {
            return react(VillagerReaction.THUNDER_PANIC, null);
        } else if (event.matches(GameEvent.EXPLODE)) {
            boolean firework = !world.getEntitiesByClass(FireworkRocketEntity.class, new Box(from).expand(2), e -> true).isEmpty();
            return react(firework ? VillagerReaction.FIREWORK_WONDER : VillagerReaction.EXPLOSION_PANIC, null);
        } else if (event.matches(GameEvent.ENTITY_DAMAGE)) {
            return player != null && d2 < 64 && react(VillagerReaction.WINCE, player);
        } else if (event.matches(GameEvent.ENTITY_DIE)) {
            return source instanceof Monster && !isToken(source) && d2 < 100 && react(VillagerReaction.CHEER, source);
        } else if (event.matches(GameEvent.TELEPORT)) {
            return player != null && d2 < 64 && react(VillagerReaction.WHOA, player);
        } else if (event.matches(GameEvent.PROJECTILE_SHOOT)) {
            return player != null && d2 < 64 && react(VillagerReaction.FLINCH, player);
        } else if (event.matches(GameEvent.BLOCK_PLACE)) {
            if (player == null) return false;
            Watcher watcher = watchers.get(player.getUuid());
            if (watcher != null) watcher.interacted = true;
            if (from.getManhattanDistance(pos) == 1) return react(VillagerReaction.PERSONAL_SPACE, player);
            return d2 < 25 && react(VillagerReaction.ADMIRE_BUILD, player);
        } else if (event.matches(GameEvent.BLOCK_DESTROY)) {
            return player != null && from.getManhattanDistance(pos) == 1 && react(VillagerReaction.FLINCH, player);
        } else if (event.matches(GameEvent.EQUIP)) {
            return player != null && d2 < 36 && react(VillagerReaction.FASHION, player);
        }
        return false;
    }

    // =============================================================================================================
    // Client
    // =============================================================================================================

    @Override
    public boolean onSyncedBlockEvent(int type, int data) {
        if (world != null && world.isClient) {
            VillagerReaction started = VillagerReaction.byId(type - 1);
            if (started != null) {
                reaction = started;
                clientAge = 0;
                targetId = data;
            }
        }
        return true;
    }

    public void clientTick() {
        if (world == null) return;
        int stage = miningStageProbe.applyAsInt(pos);
        if (stage != miningStage) {
            VillagerReactionEffects.miningStageChanged(world, pos, miningStage, stage, world.random);
            miningStage = stage;
        }
        if (stage >= 0) VillagerReactionEffects.miningTick(world, pos, stage, modeAge, world.random);
        if (reaction != null && clientAge < reaction.duration) {
            VillagerReactionEffects.tick(world, pos, reaction, clientAge, world.random);
            clientAge++;
        }
        if (mode != VillagerMode.NONE) VillagerReactionEffects.modeTick(world, pos, mode, world.getTime(), world.random);
        modeAge++;
        updateLook();
        if (blinkTicks > 0) {
            blinkTicks--;
        } else if (--blinkTimer <= 0) {
            blinkTicks = 3;
            blinkTimer = 40 + world.random.nextInt(100);
        }
    }

    /** Turns its face toward the reaction's target, or the nearest player, or back to rest. */
    private void updateLook() {
        prevYaw = yaw;
        prevTilt = tilt;
        Entity target = null;
        if (isReacting() && targetId >= 0) target = world.getEntityById(targetId);
        if (target == null && mode != VillagerMode.SLEEP) {
            target = world.getClosestPlayer(cx(), cy(), cz(), 8, false);
        }
        float wantedYaw, wantedTilt;
        if (target != null) {
            double dx = target.getX() - cx(), dz = target.getZ() - cz();
            double dist = Math.sqrt(dx * dx + dz * dz);
            // rotating the block by yaw turns its south side (the face's chin) toward the target
            wantedYaw = (float) (MathHelper.atan2(dx, dz) * MathHelper.DEGREES_PER_RADIAN);
            wantedTilt = (float) MathHelper.clamp(24 - dist * 2.5, 4, 20);
        } else {
            wantedYaw = Math.round(yaw / 360f) * 360f;
            wantedTilt = 0;
        }
        float delta = MathHelper.wrapDegrees(wantedYaw - yaw);
        yaw += MathHelper.clamp(delta * 0.25f, -18f, 18f);
        tilt += (wantedTilt - tilt) * 0.2f;
    }

    public int getClientAge() {
        return clientAge;
    }

    public int getModeAge() {
        return modeAge;
    }

    public float getYaw(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevYaw, yaw);
    }

    public float getTilt(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevTilt, tilt);
    }

    /** Client: how far a player is in breaking it (0..9), -1 when nobody is. */
    public int getMiningStage() {
        return miningStage;
    }

    public boolean isBlinking() {
        return blinkTicks > 0;
    }

    // =============================================================================================================
    // Data
    // =============================================================================================================

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("Mode", mode.ordinal());
        nbt.putInt("Pokes", totalPokes);
        if (soul != null) nbt.put(VillagerSoul.KEY, soul.copy());
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        VillagerMode read = VillagerMode.byId(nbt.getInt("Mode"));
        if (read != mode) modeAge = 0;
        mode = read;
        totalPokes = nbt.getInt("Pokes");
        if (nbt.contains(VillagerSoul.KEY, NbtElement.COMPOUND_TYPE)) soul = nbt.getCompound(VillagerSoul.KEY).copy();
    }

    /** The squashed villager's data, or null. */
    @Nullable
    public NbtCompound getSoul() {
        return soul;
    }

    public void setSoul(@Nullable NbtCompound soul) {
        this.soul = soul;
        markDirty();
    }

    /**
     * What follows the block when it moves (falls, pushed by a piston) or is dropped as an item: the villager inside
     * and its poke count. Not its mood nor its reactions.
     */
    public NbtCompound keptData() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Pokes", totalPokes);
        if (soul != null) nbt.put(VillagerSoul.KEY, soul.copy());
        return nbt;
    }

    public void readKeptData(NbtCompound nbt) {
        if (nbt.contains("Pokes")) totalPokes = nbt.getInt("Pokes");
        if (nbt.contains(VillagerSoul.KEY, NbtElement.COMPOUND_TYPE)) soul = nbt.getCompound(VillagerSoul.KEY).copy();
        markDirty();
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        // the clients only need its mode (not the villager's trades)
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Mode", mode.ordinal());
        return nbt;
    }

    @Override
    public Listener getEventListener() {
        return listener;
    }

    /** Hears game events within 16 blocks, like a sculk sensor (but it only gasps). */
    public static class Listener implements GameEventListener {
        private final VillagerBlockEntity villager;
        private final PositionSource source;

        Listener(VillagerBlockEntity villager) {
            this.villager = villager;
            this.source = new BlockPositionSource(villager.pos);
        }

        @Override
        public PositionSource getPositionSource() {
            return source;
        }

        @Override
        public int getRange() {
            return WATCH_RANGE;
        }

        @Override
        public boolean listen(ServerWorld world, RegistryEntry<GameEvent> event, GameEvent.Emitter emitter, Vec3d emitterPos) {
            if (villager.isRemoved()) return false;
            return villager.onGameEvent(world, event, emitter.sourceEntity(), emitterPos);
        }
    }

    /** What it remembers of a player around. */
    private static final class Watcher {
        boolean near;
        long nearSince;
        long lastLeft = Long.MIN_VALUE / 2;
        long greetedAt = -1;
        boolean interacted;
        long lastSeen;
        int stareTicks;
        boolean shyDone, contestDone;
        boolean wasSneaking;
        final long[] sneaks = new long[6];
        int sneakIndex;
        int sneakTicks;
        boolean suspiciousDone;
        boolean wasOnGround;
        double lastY;
        final long[] jumps = new long[4];
        int jumpIndex;
        boolean wasDead;
        boolean wasUsing;
        @Nullable
        Item lastShown;
        int chatCount;

        Watcher(PlayerEntity player) {
            Arrays.fill(sneaks, Long.MIN_VALUE / 2);
            Arrays.fill(jumps, Long.MIN_VALUE / 2);
            resetFrom(player);
            chatCount = VillagerBlockEvents.chatCount(player);
        }

        /** Takes the player's current state as the reference (no reaction to what happened while it slept). */
        void resetFrom(PlayerEntity player) {
            wasOnGround = player.isOnGround();
            lastY = player.getY();
            wasSneaking = player.isSneaking();
            wasUsing = player.isUsingItem();
            chatCount = VillagerBlockEvents.chatCount(player);
            stareTicks = 0;
            sneakTicks = 0;
        }
    }
}
