package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The host of the star spaces: a Mula (a hologram board actor, see {@link BoardActors}) that comes with the star when a
 * token reaches it ({@link PartyStars}), and goes once the star is bought or not. The purchase itself stays
 * {@link PartyStars}'s: the host only shows it.
 * <ul>
 *     <li>it pops in on the space's Spawn Bubble if its Star Cartridge is linked to one ({@link BoardMobSpots}), else
 *     floating beside the space on the token's right; it turns to the token, holding the item the party's star is
 *     (whatever it is: a dirt block is held as a little block);</li>
 *     <li>while the player chooses, it plays with it: tossing it up, spinning it on a hand, juggling it, polishing it,
 *     hiding it behind its back... at random, never twice in a row;</li>
 *     <li>bought: a happy dance, then the item flies from its hands to the buyer (or to the token, the buyer being
 *     far) in a trail of sparkles, it claps and waves (or shows the way to the star's new space), and vanishes;</li>
 *     <li>refused, or too few coins: it droops, shakes its head, sighs a grey puff, and drifts away, fading out.</li>
 * </ul>
 * Its show never holds the party: the token goes on as soon as the choice is made, the host's goodbye plays meanwhile
 * (a few seconds). One host per token; its party stopped, it goes at once. Server thread only.
 */
public final class StarHosts {
    /** Without a Spawn Bubble, the host floats this far on the token's right, this high above the space's surface. */
    public static final double SIDE = 1.6, LIFT = 0.7;
    /** The host is drawn this much bigger than a wild Mula (it is seen from the whole board). */
    public static final double SCALE = 1.6;
    /** Beyond this distance from the host, its gift flies to the token rather than to the buyer. */
    private static final double GIFT_REACH = 16;
    /** How fast it turns (degrees per tick), and drifts away when it leaves (blocks per tick). */
    private static final float TURN_SPEED = 14;
    private static final double LEAVE_SPEED = 0.06;

    /**
     * Length of each host animation in ticks, as triggered (its json length, mula_animations.py, plus the blend in,
     * {@link MulaEntity#MAIN_TRANSITION_TICKS}).
     */
    static final int APPEAR_TICKS = ticks(1.0), DANCE_TICKS = ticks(1.6), GIVE_TICKS = ticks(1.7), POINT_TICKS = ticks(1.9),
            VANISH_TICKS = ticks(0.6), SAD_TICKS = ticks(2.0), LEAVE_TICKS = ticks(1.2);
    /** Its games with the item while the player chooses, with their lengths. */
    private static final List<String> GAMES = List.of("host_toss", "host_spin", "host_juggle", "host_polish", "host_peek");
    private static final int[] GAME_TICKS = {ticks(1.6), ticks(1.8), ticks(2.0), ticks(1.8), ticks(2.2)};
    /** Between two games, it presents the item for this long (plus up to as much at random). */
    private static final int REST_TICKS = 20;
    /** When it turns to the star's new space while it shows the way (ticks into host_point). */
    private static final int POINT_TURN_TICK = 22;
    private static final int[] POINT_SPARK_TICKS = {30, 40};

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20) + MulaEntity.MAIN_TRANSITION_TICKS;
    }

    /** Where the host is in its show. */
    public enum Phase {
        /** Popping in, the item shown. */
        APPEAR,
        /** Presenting the item and playing with it while the player chooses. */
        PRESENT,
        /** Bought: its happy dance. */
        DANCE,
        /** The item flies to the buyer; it claps and waves, or shows the way to the star's new space. */
        GIVE,
        /** Gone in a twinkle. */
        VANISH,
        /** Refused (or too few coins): disappointed. */
        SAD,
        /** Drifting away, fading out. */
        LEAVE
    }

    private static final BoardSequences<Host> RUNNING = new BoardSequences<>();

    private StarHosts() {
    }

    public static void initialize() {
        RUNNING.stopWithServer(Host::finish);
    }

    // ---------------------------------------------------------------- queries (and the GameTests)

    /** The host of {@code token}'s visit to the star, null if none. */
    public static @Nullable MulaEntity hostOf(UUID token) {
        Host host = RUNNING.get(token);
        return host == null ? null : host.mula;
    }

    /** Where the host of {@code token}'s visit is in its show, null if none. */
    public static @Nullable Phase phase(UUID token) {
        Host host = RUNNING.get(token);
        return host == null ? null : host.phase;
    }

    /**
     * Where the host of the star space at {@code space} appears for {@code token}: on its Spawn Bubble, else
     * {@link #SIDE} blocks on the token's right, {@link #LIFT} high, facing it.
     */
    public static BoardMobSpots.Spot spot(ServerWorld world, BlockPos space, MobEntity token) {
        Vec3d stand = BoardSpaces.standPos(world, space);
        Vec3d at = stand.add(Vec3d.fromPolar(0, token.getYaw() + 90).multiply(SIDE)).add(0, LIFT, 0);
        return BoardMobSpots.spot(world, space, at, BoardSequences.yawToward(at, stand));
    }

    // ---------------------------------------------------------------- from PartyStars

    /**
     * {@code token} reached the star of {@code party} on {@code space}: its host comes with the star. {@code offered}:
     * the player chooses now (the host waits for {@link #decided}); false: too few coins (it is disappointed at once).
     */
    public static void arrive(ServerWorld world, MobEntity token, BlockPos space, PartyControllerEntity party, boolean offered) {
        arrive(world, token, space, party.getCurrency(PartyCurrency.STAR), offered);
    }

    /** {@code token} reached the star on {@code space}, the item {@code star}: its host comes holding it. */
    public static void arrive(ServerWorld world, MobEntity token, BlockPos space, ItemStack star, boolean offered) {
        Host previous = RUNNING.get(token);
        if (previous != null) previous.finish();
        Host host = new Host(world, token, space.toImmutable(), offered);
        if (!host.spawn(star)) return;
        RUNNING.run(host, host::tick);
    }

    /**
     * The player of {@code token} chose: {@code bought} the star (which then went to {@code newSpace}, null if it is
     * hidden now), given to {@code buyer}; or not. The host shows it, and goes.
     */
    public static void decided(MobEntity token, boolean bought, @Nullable BlockPos newSpace, @Nullable ServerPlayerEntity buyer) {
        Host host = RUNNING.get(token);
        if (host == null || host.outcome != null) return;
        host.outcome = bought ? Outcome.BOUGHT : Outcome.REFUSED;
        host.newSpace = newSpace;
        host.buyer = buyer;
    }

    /** {@code token}'s visit to the star is called off (its party stopped): its host goes at once. */
    public static void cancel(UUID token) {
        Host host = RUNNING.get(token);
        if (host != null) host.finish();
    }

    private enum Outcome { BOUGHT, REFUSED }

    /** One visit to the star: its host. */
    private static final class Host extends BoardSequences.Sequence {
        final BlockPos space;
        /** False: too few coins, nothing to choose. */
        final boolean offered;
        MulaEntity mula;
        Phase phase = Phase.APPEAR;
        int phaseTick;
        /** Ticks before its next game with the item (PRESENT), and the last one played. */
        int nextGame;
        int lastGame = -1;
        int gameEnds;
        @Nullable Outcome outcome;
        @Nullable BlockPos newSpace;
        @Nullable ServerPlayerEntity buyer;
        /** Whether it shows the way to the star's new space (host_point) rather than waving. */
        boolean pointing;
        /** The way it leaves (LEAVE). */
        Vec3d away = Vec3d.ZERO;

        Host(ServerWorld world, MobEntity token, BlockPos space, boolean offered) {
            super(world, token, () -> {
            });
            this.space = space;
            this.offered = offered;
        }

        /** It pops in on its spot, holding {@code star}, facing the token. */
        boolean spawn(ItemStack star) {
            MulaEntity one = ModEntities.MULA_ENTITY.create(world);
            if (one == null) return false;
            cast(one);
            EntityAttributeInstance scale = one.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
            if (scale != null) scale.setBaseValue(SCALE);
            one.setHeldItem(star);
            one.setHostSpace(space);
            BoardMobSpots.Spot spot = spot(world, space, token);
            BoardMobSpots.showStarts(world, spot, task);
            BoardMobSpots.hold(one, spot);
            Vec3d at = spot.pos();
            float yaw = spot.onMarker() ? spot.yaw() : BoardSequences.yawToward(at, token.getPos());
            one.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
            one.setHeadYaw(yaw);
            one.setBodyYaw(yaw);
            world.spawnEntity(one);
            mula = one;
            one.playHost("host_appear");
            world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 12, 0.3, 0.4, 0.3, 0.03);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.NEUTRAL, 0.8F, 1.5F);
            return true;
        }

        void tick() {
            if (mula == null || mula.isRemoved()) {
                finish();
                return;
            }
            phaseTick++;
            switch (phase) {
                case APPEAR -> {
                    face(token.getPos());
                    if (phaseTick >= APPEAR_TICKS || (outcome != null && phaseTick >= APPEAR_TICKS / 2)) {
                        if (!offered) sad();
                        else enter(Phase.PRESENT);
                    }
                }
                case PRESENT -> present();
                case DANCE -> {
                    face(token.getPos());
                    if (phaseTick >= DANCE_TICKS) give();
                }
                case GIVE -> {
                    if (pointing && phaseTick >= POINT_TURN_TICK && newSpace != null) {
                        Vec3d to = BoardSpaces.standPos(world, newSpace);
                        face(to);
                        for (int at : POINT_SPARK_TICKS) if (phaseTick == at) sparkTowards(to);
                    }
                    if (phaseTick >= (pointing ? POINT_TICKS : GIVE_TICKS)) {
                        mula.setHeldItem(ItemStack.EMPTY);
                        mula.setHostSpace(null);
                        mula.playHost("host_vanish");
                        enter(Phase.VANISH);
                    }
                }
                case VANISH -> {
                    if (phaseTick >= VANISH_TICKS) finish();
                }
                case SAD -> {
                    face(token.getPos());
                    if (phaseTick >= SAD_TICKS) leave();
                }
                case LEAVE -> {
                    // its back turned, it drifts away and up a little as it fades out
                    face(mula.getPos().add(away));
                    mula.setPosition(mula.getPos().add(away.multiply(LEAVE_SPEED)).add(0, 0.015, 0));
                    if (phaseTick >= LEAVE_TICKS) finish();
                }
            }
        }

        void enter(Phase next) {
            phase = next;
            phaseTick = 0;
            if (next == Phase.PRESENT) {
                nextGame = REST_TICKS / 2 + world.getRandom().nextInt(REST_TICKS);
                gameEnds = 0;
            }
        }

        /** While the player chooses: it faces the token and plays with the item now and then. */
        void present() {
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
            if (phaseTick < gameEnds + nextGame) return;
            int game = world.getRandom().nextInt(GAMES.size() - 1);
            if (game >= lastGame && lastGame >= 0) game++; // never twice in a row
            lastGame = game;
            mula.playHost(GAMES.get(game));
            gameEnds = phaseTick + GAME_TICKS[game];
            nextGame = REST_TICKS + world.getRandom().nextInt(REST_TICKS);
        }

        /** Bought: the item flies to the buyer (or the token, the buyer far away); it shows the way if the star moved. */
        void give() {
            Entity target = buyer != null && !buyer.isRemoved() && buyer.getWorld() == world
                    && buyer.squaredDistanceTo(mula) <= GIFT_REACH * GIFT_REACH ? buyer : token;
            mula.setGiftTarget(target.isRemoved() ? null : target);
            pointing = newSpace != null && !newSpace.equals(space);
            mula.playHost(pointing ? "host_point" : "host_give");
            enter(Phase.GIVE);
        }

        void sad() {
            mula.playHost("host_sad");
            enter(Phase.SAD);
        }

        /** It turns its back and drifts away, empty-handed: the star is back over its space. */
        void leave() {
            Vec3d from = token.getPos();
            Vec3d d = mula.getPos().subtract(from.x, mula.getY(), from.z);
            away = d.lengthSquared() < 1.0E-4 ? Vec3d.fromPolar(0, mula.getYaw() + 180) : d.normalize();
            mula.setHeldItem(ItemStack.EMPTY);
            mula.setHostSpace(null);
            mula.playHost("host_leave");
            world.spawnParticles(ParticleTypes.CLOUD, mula.getX(), mula.getY() + mula.getHeight() * 0.6, mula.getZ(), 4,
                    0.15, 0.1, 0.15, 0.005);
            enter(Phase.LEAVE);
        }

        /** It turns towards {@code to}, {@link #TURN_SPEED} degrees a tick at most. */
        void face(Vec3d to) {
            float want = BoardSequences.yawToward(mula.getPos(), to);
            float yaw = mula.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(want - mula.getYaw()), -TURN_SPEED, TURN_SPEED);
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

        /** Over (or stopped): the host is removed, wherever it was in its show. */
        void finish() {
            if (!close()) return;
            dismissActors();
            onDone.run();
        }
    }
}
