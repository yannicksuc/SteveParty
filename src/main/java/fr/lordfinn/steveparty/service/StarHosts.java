package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The host of the star: a Mula (a hologram board actor, see {@link BoardActors}) waiting with the party's star on the
 * space that has it ({@link PartyStars}), the whole party long. The purchase itself stays {@link PartyStars}'s: the
 * host only shows it.
 * <ul>
 *     <li>it stands on the space's Spawn Bubble if its Star Cartridge links one ({@link BoardMobSpots}), else floating
 *     beside the space, on the side away from the path; it holds the item the party's star is (whatever it is: a dirt
 *     block is held as a little block), the star no longer drawn over the space (its beam is);</li>
 *     <li>while it waits, it plays with it: tossing it up, spinning it on a hand, juggling it, polishing it, hiding it
 *     behind its back... at random, never twice in a row, turning to the players nearby;</li>
 *     <li>a token reaching the star: it presents it to the token while the player chooses;</li>
 *     <li>bought: a happy dance, then the item flies from its hands to the buyer (or to the token, the buyer being
 *     far) in a trail of sparkles, it shows the way to the star's new space (where a new host pops in with the star)
 *     and vanishes;</li>
 *     <li>refused, or too few coins: it droops, shakes its head, sighs, and goes back to waiting with the star.</li>
 * </ul>
 * A Spawn Bubble « always visible » ({@link MarkerResidents}) has a Mula asleep on it the whole party on every star
 * space without the star: the star comes, it wakes up with it (the host is that Mula); the star goes, it falls asleep
 * again, empty-handed.
 * <p>
 * Its show never holds the party: the token goes on as soon as the choice is made. The hosts are never saved: made
 * again (a second at most) after a reload or when their chunk loads; gone when the party ends. One per star space
 * with the star. Server thread only.
 */
public final class StarHosts {
    /** Without a Spawn Bubble, the host floats this far beside the space, this high above its surface. */
    public static final double SIDE = 1.6, LIFT = 0.7;
    /** The host is drawn this much bigger than a wild Mula (it is seen from the whole board). */
    public static final double SCALE = 1.6;
    /** Beyond this distance from the host, its gift flies to the token rather than to the buyer. */
    private static final double GIFT_REACH = 16;
    /** Waiting, it turns to the nearest player this close (blocks). */
    private static final double WATCH_REACH = 10;
    /** How fast it turns (degrees per tick). */
    private static final float TURN_SPEED = 14;
    /** How often the hosts are checked against the stars (ticks). */
    private static final int CHECK_INTERVAL = 20;

    /**
     * Length of each host animation in ticks, as triggered (its json length, mula_animations.py, plus the blend in,
     * {@link MulaEntity#MAIN_TRANSITION_TICKS}).
     */
    static final int APPEAR_TICKS = ticks(1.0), DANCE_TICKS = ticks(1.6), GIVE_TICKS = ticks(1.7), POINT_TICKS = ticks(1.9),
            VANISH_TICKS = ticks(0.6), SAD_TICKS = ticks(2.0);
    /** Its games with the item, with their lengths. */
    private static final List<String> GAMES = List.of("host_toss", "host_spin", "host_juggle", "host_polish", "host_peek");
    private static final int[] GAME_TICKS = {ticks(1.6), ticks(1.8), ticks(2.0), ticks(1.8), ticks(2.2)};
    /** Between two games, it presents the item for this long (plus up to as much at random): a token there, waiting. */
    private static final int PRESENT_REST_TICKS = 20, WAIT_REST_TICKS = 50;
    /** When it turns to the star's new space while it shows the way (ticks into host_point). */
    private static final int POINT_TURN_TICK = 22;
    private static final int[] POINT_SPARK_TICKS = {30, 40};
    /** The sides it may float on without a Spawn Bubble, the first preferred among equals. */
    private static final Direction[] SIDES = {Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.NORTH};

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20) + MulaEntity.MAIN_TRANSITION_TICKS;
    }

    /** Where the host is in its show. */
    public enum Phase {
        /** Popping in (or presenting the item to a token that just came). */
        APPEAR,
        /** Waiting with the item, playing with it. */
        WAIT,
        /** Presenting the item to a token and playing with it while the player chooses. */
        PRESENT,
        /** Bought: its happy dance. */
        DANCE,
        /** The item flies to the buyer; it waves, or shows the way to the star's new space. */
        GIVE,
        /** Gone in a twinkle. */
        VANISH,
        /** Refused (or too few coins): disappointed, then back to waiting. */
        SAD
    }

    /** The host of each star space with the star. */
    private static final Map<GlobalPos, Host> HOSTS = ServerMemory.forgetOnStop(new HashMap<>());
    /** Every host alive (those leaving too), ticked every tick. */
    private static final List<Host> ALL = ServerMemory.forgetOnStop(new ArrayList<>());
    /** The host each token visiting the star is with. */
    private static final Map<UUID, Host> VISITS = ServerMemory.forgetOnStop(new HashMap<>());
    private static int age;
    /** A host went: the stars looked at again at the end of the tick (a new host where one is missing). */
    private static boolean dirty;

    private StarHosts() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(StarHosts::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Host host : new ArrayList<>(ALL)) host.finish();
        });
    }

    private static void tick(MinecraftServer server) {
        for (int i = ALL.size() - 1; i >= 0; i--) {
            if (i < ALL.size()) ALL.get(i).tick();
        }
        if (++age < CHECK_INTERVAL && !dirty) return;
        age = 0;
        dirty = false;
        for (ServerWorld world : server.getWorlds()) update(world);
    }

    // ---------------------------------------------------------------- queries (and the GameTests)

    /** The host of {@code token}'s visit to the star, null if none. */
    public static @Nullable MulaEntity hostOf(UUID token) {
        Host host = VISITS.get(token);
        return host == null ? null : host.mula;
    }

    /** Where the host of {@code token}'s visit is in its show, null if none. */
    public static @Nullable Phase phase(UUID token) {
        Host host = VISITS.get(token);
        return host == null ? null : host.phase;
    }

    /** The host of the star space at {@code space}, null if none. */
    public static @Nullable MulaEntity hostAt(ServerWorld world, BlockPos space) {
        Host host = HOSTS.get(GlobalPos.create(world.getRegistryKey(), space));
        return host == null || host.mula == null || host.mula.isRemoved() ? null : host.mula;
    }

    /** Where the host of the star space at {@code space} is in its show, null if none. */
    public static @Nullable Phase phaseAt(ServerWorld world, BlockPos space) {
        Host host = HOSTS.get(GlobalPos.create(world.getRegistryKey(), space));
        return host == null ? null : host.phase;
    }

    /**
     * Where the host of the star space at {@code space} stands: on its Spawn Bubble, else {@link #SIDE} blocks beside
     * the space ({@link #LIFT} high), on the side with the fewest board spaces and nothing solid, facing the space.
     */
    public static BoardMobSpots.Spot spot(ServerWorld world, BlockPos space) {
        BoardMobSpots.Spot marker = BoardMobSpots.marker(world, space);
        if (marker != null) return marker;
        Vec3d stand = BoardSpaces.standPos(world, space);
        Direction best = SIDES[0];
        int bestScore = Integer.MAX_VALUE;
        BlockPos.Mutable at = new BlockPos.Mutable();
        for (Direction side : SIDES) {
            int score = 0;
            for (int i = 1; i <= 2; i++) {
                at.set(stand.x + side.getOffsetX() * i, space.getY(), stand.z + side.getOffsetZ() * i);
                if (world.getBlockState(at).getBlock() instanceof ABoardSpaceBlock) score += 2;
            }
            at.set(stand.x + side.getOffsetX() * SIDE, stand.y + LIFT + 0.5, stand.z + side.getOffsetZ() * SIDE);
            if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty()) score += 3;
            if (score < bestScore) {
                bestScore = score;
                best = side;
            }
        }
        Vec3d pos = stand.add(best.getOffsetX() * SIDE, LIFT, best.getOffsetZ() * SIDE);
        return new BoardMobSpots.Spot(null, pos, yawToward(pos, stand), 0);
    }

    // ---------------------------------------------------------------- the stars

    /**
     * The hosts of {@code world} as its stars are: one on each star space with the star (its chunk loaded), holding
     * the party's star; the others go (once their visit is over). Every {@link #CHECK_INTERVAL} ticks, and whenever the
     * stars move ({@link PartyStars#sync}).
     */
    public static void update(ServerWorld world) {
        if (HOSTS.isEmpty() && PartyControllerEntity.getActivePartyControllers().isEmpty()) return;
        List<GlobalPos> wanted = new ArrayList<>(2);
        for (PartyControllerEntity party : PartyControllerEntity.getActivePartyControllers()) {
            BlockPos star = party.getStarSpace();
            if (party.isRemoved() || party.getWorld() != world || !party.getPartyData().isStarted() || star == null) continue;
            GlobalPos key = GlobalPos.create(world.getRegistryKey(), star);
            if (wanted.contains(key)) continue;
            wanted.add(key);
            ItemStack item = party.getCurrency(PartyCurrency.STAR);
            Host host = HOSTS.get(key);
            if (host == null) {
                if (loaded(world, star)) spawn(world, star, item);
            } else {
                host.upkeep(item);
            }
        }
        if (HOSTS.isEmpty()) return;
        for (Host host : new ArrayList<>(HOSTS.values())) {
            if (host.world == world && !wanted.contains(host.key) && host.token == null && host.phase != Phase.VANISH) host.goAway();
        }
    }

    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos): nothing is loaded for this
    private static boolean loaded(ServerWorld world, BlockPos space) {
        return world.isChunkLoaded(space) && world.shouldTickEntity(space);
    }

    private static @Nullable Host spawn(ServerWorld world, BlockPos space, ItemStack star) {
        Host host = new Host(world, space.toImmutable());
        if (!host.spawn(star)) return null;
        HOSTS.put(host.key, host);
        ALL.add(host);
        return host;
    }

    // ---------------------------------------------------------------- from PartyStars

    /** {@code token} reached the star of {@code party} on {@code space}: its host presents it (see the other arrive). */
    public static void arrive(ServerWorld world, MobEntity token, BlockPos space, PartyControllerEntity party, boolean offered) {
        arrive(world, token, space, party.getCurrency(PartyCurrency.STAR), offered);
    }

    /**
     * {@code token} reached the star on {@code space}, the item {@code star}: its host (made now if it is not there yet)
     * presents it to the token. {@code offered}: the player chooses now (the host waits for {@link #decided}); false:
     * too few coins (it is disappointed at once).
     */
    public static void arrive(ServerWorld world, MobEntity token, BlockPos space, ItemStack star, boolean offered) {
        Host previous = VISITS.remove(token.getUuid());
        if (previous != null) previous.endVisit();
        Host host = HOSTS.get(GlobalPos.create(world.getRegistryKey(), space));
        if (host != null && (host.mula == null || host.mula.isRemoved() || host.phase == Phase.VANISH)) {
            host.finish();
            host = null;
        }
        if (host == null) {
            host = spawn(world, space, star);
            if (host == null) return;
        } else {
            if (host.token != null) {
                VISITS.remove(host.token.getUuid(), host);
                host.endVisit();
            }
            host.mula.setHeldItem(star);
            host.mula.playHost("host_appear");
            host.enter(Phase.APPEAR);
        }
        host.token = token;
        host.offered = offered;
        VISITS.put(token.getUuid(), host);
    }

    /**
     * The player of {@code token} chose: {@code bought} the star (which then went to {@code newSpace}, null if it is
     * hidden now), given to {@code buyer}; or not. The host shows it.
     */
    public static void decided(MobEntity token, boolean bought, @Nullable BlockPos newSpace, @Nullable ServerPlayerEntity buyer) {
        Host host = VISITS.get(token.getUuid());
        if (host == null || host.outcome != null) return;
        host.outcome = bought ? Outcome.BOUGHT : Outcome.REFUSED;
        host.newSpace = newSpace;
        host.buyer = buyer;
    }

    /** {@code token}'s visit is over without a word (the token gone): its host goes back to waiting. */
    public static void forget(UUID token) {
        Host host = VISITS.remove(token);
        if (host != null) {
            host.endVisit();
            if (host.phase == Phase.APPEAR || host.phase == Phase.PRESENT) host.enter(Phase.WAIT);
        }
    }

    /** {@code token}'s visit to the star is called off (its party stopped): its host goes at once. */
    public static void cancel(UUID token) {
        Host host = VISITS.get(token);
        if (host != null) host.finish();
    }

    private enum Outcome { BOUGHT, REFUSED }

    /** The yaw of a mob at {@code from} facing {@code to}. */
    private static float yawToward(Vec3d from, Vec3d to) {
        return BoardSequences.yawToward(from, to);
    }

    /** The host of one star space. */
    private static final class Host {
        final ServerWorld world;
        final BlockPos space;
        final GlobalPos key;
        /** Its BoardActors sequence (the one of the Mula asleep on its bubble, when it was that one). */
        UUID task = UUID.randomUUID();
        MulaEntity mula;
        BoardMobSpots.Spot spot;
        Phase phase = Phase.APPEAR;
        int phaseTick;
        /** Ticks before its next game with the item, the last one played, and when the current one ends. */
        int nextGame;
        int lastGame = -1;
        int gameEnds;
        boolean done;
        /** The token visiting the star, null for none; its visit. */
        @Nullable MobEntity token;
        boolean offered;
        @Nullable Outcome outcome;
        @Nullable BlockPos newSpace;
        @Nullable ServerPlayerEntity buyer;
        /** Whether it shows the way to the star's new space (host_point) rather than waving. */
        boolean pointing;

        Host(ServerWorld world, BlockPos space) {
            this.world = world;
            this.space = space;
            this.key = GlobalPos.create(world.getRegistryKey(), space);
        }

        /** It pops in on its spot holding {@code star} (or the Mula asleep on its bubble wakes up with it). */
        boolean spawn(ItemStack star) {
            spot = spot(world, space);
            Vec3d at = spot.pos();
            MarkerResidents.Resident resident = spot.marker() == null ? null : MarkerResidents.take(world, spot.marker());
            if (resident != null && resident.mob() instanceof MulaEntity sleeper) {
                mula = sleeper;
                task = resident.sequence();
                mula.setHostAsleep(false);
                place(at, spot.yaw());
            } else {
                if (resident != null) BoardActors.end(resident.sequence());
                MulaEntity one = ModEntities.MULA_ENTITY.create(world);
                if (one == null) return false;
                one.makeBoardActor();
                BoardActors.join(task, one);
                one.setNoGravity(true);
                mula = one;
                place(at, spot.yaw());
                world.spawnEntity(one);
            }
            BoardMobSpots.showStarts(world, spot, task);
            EntityAttributeInstance scale = mula.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
            if (scale != null) scale.setBaseValue(SCALE);
            mula.setHeldItem(star);
            mula.setHostSpace(space);
            mula.playHost("host_appear");
            enter(Phase.APPEAR);
            world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 12, 0.3, 0.4, 0.3, 0.03);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.NEUTRAL, 0.8F, 1.5F);
            return true;
        }

        void place(Vec3d at, float yaw) {
            mula.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
            mula.setHeadYaw(yaw);
            mula.setBodyYaw(yaw);
            mula.setVelocity(Vec3d.ZERO);
        }

        /** Every second while its space has the star: kept on its spot, holding the party's star, its bubble its own. */
        void upkeep(ItemStack star) {
            if (mula == null || mula.isRemoved()) return;
            if (phase != Phase.WAIT) return;
            BoardMobSpots.Spot now = spot(world, space);
            if (now.pos().squaredDistanceTo(spot.pos()) > 0.01) {
                spot = now;
                place(now.pos(), now.yaw());
            }
            spot = now;
            BoardMobSpots.showStarts(world, spot, task);
            if (!ItemStack.areItemsAndComponentsEqual(mula.getHeldItem(), star)) mula.setHeldItem(star);
        }

        void tick() {
            if (done) return;
            if (mula == null || mula.isRemoved()) {
                finish();
                return;
            }
            phaseTick++;
            switch (phase) {
                case APPEAR -> {
                    if (token != null) face(token.getPos());
                    if (phaseTick >= APPEAR_TICKS || (outcome != null && phaseTick >= APPEAR_TICKS / 2)) {
                        if (token == null) enter(Phase.WAIT);
                        else if (!offered) sad();
                        else enter(Phase.PRESENT);
                    }
                }
                case WAIT -> {
                    faceRest();
                    play(WAIT_REST_TICKS);
                }
                case PRESENT -> present();
                case DANCE -> {
                    if (token != null) face(token.getPos());
                    if (phaseTick >= DANCE_TICKS) give();
                }
                case GIVE -> {
                    if (pointing && phaseTick >= POINT_TURN_TICK && newSpace != null) {
                        Vec3d to = BoardSpaces.standPos(world, newSpace);
                        face(to);
                        for (int at : POINT_SPARK_TICKS) if (phaseTick == at) sparkTowards(to);
                    }
                    if (phaseTick >= (pointing ? POINT_TICKS : GIVE_TICKS)) goAway();
                }
                case VANISH -> {
                    if (phaseTick >= VANISH_TICKS) finish();
                }
                case SAD -> {
                    if (token != null) face(token.getPos());
                    if (phaseTick >= SAD_TICKS) {
                        if (token != null) VISITS.remove(token.getUuid(), this);
                        endVisit();
                        enter(Phase.WAIT);
                    }
                }
            }
        }

        void enter(Phase next) {
            phase = next;
            phaseTick = 0;
            if (next == Phase.WAIT || next == Phase.PRESENT) {
                int rest = next == Phase.WAIT ? WAIT_REST_TICKS : PRESENT_REST_TICKS;
                nextGame = rest / 2 + world.getRandom().nextInt(rest);
                gameEnds = 0;
            }
        }

        /** While the player chooses: it faces the token and plays with the item now and then. */
        void present() {
            if (token == null) {
                enter(Phase.WAIT);
                return;
            }
            face(token.getPos());
            if (outcome == Outcome.BOUGHT) {
                mula.playHost("host_dance");
                enter(Phase.DANCE);
                return;
            }
            // Refused, or the choice is over without a word (the token or its player gone)
            if (outcome == Outcome.REFUSED || !PartyStars.isDeciding(token.getUuid())) {
                sad();
                return;
            }
            play(PRESENT_REST_TICKS);
        }

        /** One of its games with the item now and then, never twice the same in a row. */
        void play(int rest) {
            if (phaseTick < gameEnds + nextGame) return;
            int game = world.getRandom().nextInt(GAMES.size() - 1);
            if (game >= lastGame && lastGame >= 0) game++;
            lastGame = game;
            mula.playHost(GAMES.get(game));
            gameEnds = phaseTick + GAME_TICKS[game];
            nextGame = rest + world.getRandom().nextInt(rest);
        }

        /** Waiting: it faces its bubble's way, else the nearest player around, else the space. */
        void faceRest() {
            if (spot.onMarker()) {
                turnTo(spot.yaw());
                return;
            }
            PlayerEntity player = world.getClosestPlayer(mula.getX(), mula.getY(), mula.getZ(), WATCH_REACH,
                    p -> !p.isSpectator());
            face(player != null ? player.getPos() : BoardSpaces.standPos(world, space));
        }

        /** Bought: the item flies to the buyer (or the token, the buyer far away); it shows the way if the star moved. */
        void give() {
            Entity target = buyer != null && !buyer.isRemoved() && buyer.getWorld() == world
                    && buyer.squaredDistanceTo(mula) <= GIFT_REACH * GIFT_REACH ? buyer : token;
            mula.setGiftTarget(target == null || target.isRemoved() ? null : target);
            pointing = newSpace != null && !newSpace.equals(space);
            mula.playHost(pointing ? "host_point" : "host_give");
            enter(Phase.GIVE);
        }

        void sad() {
            mula.playHost("host_sad");
            enter(Phase.SAD);
        }

        /** Its token's visit is over (the host stays). */
        void endVisit() {
            token = null;
            outcome = null;
            newSpace = null;
            buyer = null;
            pointing = false;
        }

        /**
         * The star is no longer here (or was given): empty-handed, it falls asleep on its bubble if the bubble is
         * « always visible », else it vanishes in a twinkle.
         */
        void goAway() {
            mula.setHeldItem(ItemStack.EMPTY);
            mula.setHostSpace(null);
            mula.setGiftTarget(null);
            if (spot.marker() != null) {
                mula.setHostAsleep(true);
                if (MarkerResidents.giveBack(world, spot.marker(), task, mula)) {
                    forgotten();
                    return;
                }
                mula.setHostAsleep(false);
            }
            mula.playHost("host_vanish");
            enter(Phase.VANISH);
        }

        /** Over (or stopped): the host is removed, wherever it was in its show. */
        void finish() {
            if (done) return;
            forgotten();
            BoardActors.end(task);
        }

        /** No longer a host (its Mula removed, or asleep on its bubble). */
        void forgotten() {
            done = true;
            ALL.remove(this);
            HOSTS.remove(key, this);
            if (token != null) VISITS.remove(token.getUuid(), this);
            endVisit();
            dirty = true;
        }

        /** It turns towards {@code to}, {@link #TURN_SPEED} degrees a tick at most. */
        void face(Vec3d to) {
            turnTo(yawToward(mula.getPos(), to));
        }

        void turnTo(float want) {
            float delta = MathHelper.wrapDegrees(want - mula.getYaw());
            if (Math.abs(delta) < 0.5F) return;
            float yaw = mula.getYaw() + MathHelper.clamp(delta, -TURN_SPEED, TURN_SPEED);
            mula.setYaw(yaw);
            mula.setHeadYaw(yaw);
            mula.setBodyYaw(yaw);
        }

        /** A little arrow of sparkles shooting from it towards the star's new space. */
        void sparkTowards(Vec3d to) {
            Vec3d from = mula.getPos().add(0, mula.getHeight() * 0.6, 0);
            Vec3d d = to.subtract(from);
            if (d.lengthSquared() < 1.0E-4) return;
            Vec3d step = d.normalize();
            for (int i = 1; i <= 6; i++) {
                Vec3d p = from.add(step.multiply(0.35 * i));
                world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            world.spawnParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 6, 0.05, 0.05, 0.05, 0);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 0.6F, 1.8F);
        }
    }
}
