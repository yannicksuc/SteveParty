package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * The show of a Move Forward / Back space (see AdvanceBackTileBehavior): a Boomcart stages the extra move
 * {@link AdvanceBackMoves} decided, which stays exactly what it was (same steps, same way, same landing).
 * <ul>
 *     <li><b>forward</b>: the Boomcart rolls in behind the token, its fuse hissing and sparking, and blows up: a big
 *     cartoon blast behind the token, which is launched forward, hopping over the spaces to the one it lands on;</li>
 *     <li><b>back</b>: the Boomcart rolls in ahead of the token on the path, roars, charges and crashes into it with a
 *     comic bonk: the token is knocked back, hopping backward along the way it came, still facing the crash.</li>
 * </ul>
 * The blast or the crash consumes the Boomcart. It is a board actor (a hologram: invulnerable, never saved, removed
 * at the end of the show whatever ends it) and the blast is only particles and sounds: no block nor mob is touched.
 * It appears on the space's Spawn Bubble when its cartridge links one ({@link BoardMobSpots}), else beside the path.
 * <p>
 * Server thread only, not saved (a restart in the middle: no Boomcart, the token walks its move as a plain one).
 */
public final class BoomcartLaunches {
    /** From the move decided to the blast (or the crash): the token flies then (the walk's delay). */
    public static final int LAUNCH_TICKS = 44;
    /** The Boomcart rolls to its place in this many ticks. */
    public static final int ARRIVE_TICKS = 16;
    /** Back: it roars this long before it charges. */
    private static final int ROAR_TICKS = 8;
    /** Forward: how far behind the token it stops; without a Spawn Bubble, how far further it rolls in from. */
    private static final double BEHIND = 1.35, ROLL_IN = 3.0;
    /** Back: how far ahead of the token it charges from; where it hits it. */
    private static final double CHARGE_FROM = 4.0, HIT_AT = 0.85;

    /** The running shows, by the token that landed. */
    private static final BoardSequences<Show> RUNNING = new BoardSequences<>();
    /** For the GameTests: each show's Boomcart as it appears, with the token it comes for. */
    public static final List<BiConsumer<MobEntity, BoomcartEntity>> SPAWN_LISTENERS = new CopyOnWriteArrayList<>();

    private BoomcartLaunches() {
    }

    public static void initialize() {
        RUNNING.stopWithServer(Show::finish);
        // « always visible » Spawn Bubble: a Boomcart waits on it all the party long
        MarkerResidents.register(AdvanceBackCartridgeItem.class, world -> ModEntities.BOOMCART.create(world));
    }

    public static boolean isRunning(MobEntity token) {
        return RUNNING.isRunning(token);
    }

    /**
     * The extra move of {@code steps} spaces just {@link AdvanceBackMoves#launch launched} for {@code token}, on the
     * space at {@code tile}, is staged by a Boomcart: the token will fly. False (the move walks as a plain one) if no
     * Boomcart could appear.
     */
    public static boolean play(ServerWorld world, BlockPos tile, MobEntity token, int steps) {
        if (steps == 0 || RUNNING.isRunning(token)) return false;
        Vec3d at = BoardSpaces.standPos(world, tile);
        boolean back = steps < 0;
        // The way the token goes: to the first space of its move (the first link forward, the way back)
        BlockPos first = back ? AdvanceBackMoves.peekRouted(token) : firstAhead(world, tile);
        Vec3d way = first != null ? direction(at, BoardSpaces.standPos(world, first)) : Vec3d.fromPolar(0, token.getYaw());
        if (back) way = way.multiply(-1); // back: the Boomcart comes from the front, the opposite way

        Show show = new Show(world, token, back, at, way);
        // Forward: it stops behind the token; back: it stops ahead, to charge from there
        show.mark = back ? at.add(way.multiply(CHARGE_FROM)) : at.subtract(way.multiply(BEHIND));
        show.from = back ? show.mark.add(way.multiply(ROLL_IN)) : show.mark.subtract(way.multiply(ROLL_IN));
        float yaw = yawOf(back ? way.multiply(-1) : way);
        // On the space's Spawn Bubble, facing its way: it rolls from there
        BoardMobSpots.Spot spot = BoardMobSpots.marker(world, tile);
        BoardMobSpots.showStarts(world, spot, show.task);
        if (spot != null) {
            show.from = spot.pos();
            yaw = spot.yaw();
        }
        BoomcartEntity cart = ModEntities.BOOMCART.create(world);
        if (cart == null) return false;
        show.cast(cart);
        cart.refreshPositionAndAngles(show.from.x, show.from.y, show.from.z, yaw, 0);
        cart.bodyYaw = yaw;
        cart.headYaw = yaw;
        world.spawnEntity(cart);
        show.cart = cart;
        SPAWN_LISTENERS.forEach(listener -> listener.accept(token, cart));
        world.spawnParticles(ParticleTypes.POOF, show.from.x, show.from.y + 0.4, show.from.z, 8, 0.3, 0.3, 0.3, 0.02);
        // Knocked back, the token keeps facing the Boomcart that hit it
        AdvanceBackMoves.flyWhenLaunched(token, back ? yawOf(way) : null);
        RUNNING.run(show, show::tick);
        return true;
    }

    /** The first board space the space at {@code tile} leads to, null for none. */
    private static @Nullable BlockPos firstAhead(ServerWorld world, BlockPos tile) {
        List<BlockPos> route = GlandouillePushes.route(world, tile, 1);
        return route.isEmpty() ? null : route.getFirst();
    }

    private static Vec3d direction(Vec3d from, Vec3d to) {
        Vec3d d = new Vec3d(to.x - from.x, 0, to.z - from.z);
        return d.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : d.normalize();
    }

    private static float yawOf(Vec3d dir) {
        return (float) (MathHelper.atan2(dir.z, dir.x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
    }

    /** One show: its Boomcart, where it rolls, and the blast (or the crash) that launches the token. */
    private static final class Show extends BoardSequences.Sequence {
        final boolean back;
        /** Where the token stands, and the way the Boomcart looks toward it from its mark (unit, level). */
        final Vec3d at, way;
        /** Where the Boomcart appears, and where it rolls to (forward: behind the token; back: ahead, to charge). */
        Vec3d from = Vec3d.ZERO, mark = Vec3d.ZERO;
        @Nullable BoomcartEntity cart;
        int tick;
        /** The party the token played in when it landed (null: none): its show stops if that party is over. */
        final @Nullable PartyControllerEntity party;

        Show(ServerWorld world, MobEntity token, boolean back, Vec3d at, Vec3d way) {
            super(world, token, () -> {
            });
            this.back = back;
            this.at = at;
            this.way = way;
            this.party = PartyControllerEntity.getRunningPartyOf(token.getUuid()).orElse(null);
        }

        /** The party it was part of is over, or the token is gone (or no longer a token). */
        boolean over() {
            return party != null && (party.isRemoved() || !party.getPartyData().isStarted())
                    || token.isRemoved() || !((TokenizedEntityInterface) token).steveparty$isTokenized();
        }

        void tick() {
            if (done) return;
            if (cart == null || cart.isRemoved() || over()) {
                finish();
                return;
            }
            tick++;
            if (tick < ARRIVE_TICKS) {
                // It rolls in, rattling
                place(from.lerp(mark, ease(tick / (double) ARRIVE_TICKS)), yawOf(direction(from, mark)));
                if (tick % 5 == 0) cart.playSound(ModSounds.BOOMCART_ROLL, 0.6f, 0.9f + world.random.nextFloat() * 0.3f);
                return;
            }
            if (tick == ARRIVE_TICKS) {
                place(mark, yawOf(back ? way.multiply(-1) : way));
                cart.stageFuse(LAUNCH_TICKS - ARRIVE_TICKS, LAUNCH_TICKS - ARRIVE_TICKS);
                cart.playSound(ModSounds.BOOMCART_LIGHT, 1f, 1f);
                if (back) {
                    cart.triggerAnim(BoomcartEntity.ACTION_CONTROLLER, "roar");
                    cart.playSound(ModSounds.BOOMCART_ROAR, 1f, 1.1f);
                }
            }
            int left = LAUNCH_TICKS - 1 - tick;
            cart.stageFuse(Math.max(0, left), LAUNCH_TICKS - ARRIVE_TICKS);
            if (left <= 0) {
                if (back) crash();
                else blast();
                finish();
                return;
            }
            sparks(left);
            if (back) {
                if (tick < ARRIVE_TICKS + ROAR_TICKS) return;
                // The charge: faster and faster, straight at the token
                double f = (tick - ARRIVE_TICKS - ROAR_TICKS) / (double) (LAUNCH_TICKS - 1 - ARRIVE_TICKS - ROAR_TICKS);
                Vec3d hit = at.add(way.multiply(HIT_AT));
                place(mark.lerp(hit, f * f), yawOf(way.multiply(-1)));
                if (tick % 3 == 0) cart.playSound(ModSounds.BOOMCART_ROLL, 0.8f, 1.2f + (float) f * 0.5f);
                world.spawnParticles(ParticleTypes.CLOUD, cart.getX(), cart.getY() + 0.1, cart.getZ(), 1, 0.2, 0.02, 0.2, 0.01);
            } else {
                // Winding up: it quivers more and more as its fuse burns down
                double shake = 0.03 + 0.05 * (1 - left / (double) (LAUNCH_TICKS - ARRIVE_TICKS));
                Vec3d jitter = new Vec3d((world.random.nextDouble() - 0.5) * shake, 0, (world.random.nextDouble() - 0.5) * shake);
                place(mark.add(jitter), yawOf(way));
            }
        }

        /** The Boomcart at {@code pos}, facing {@code yaw}. */
        private void place(Vec3d pos, float yaw) {
            if (cart == null) return;
            cart.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
            cart.bodyYaw = yaw;
            cart.headYaw = yaw;
            cart.setVelocity(Vec3d.ZERO);
        }

        /** Its lit fuse: sparks and smoke on its top, the hiss quicker as it burns down. */
        private void sparks(int left) {
            if (cart == null) return;
            double top = cart.getY() + BoomcartEntity.HEIGHT + 0.4;
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, cart.getX(), top, cart.getZ(), 2, 0.06, 0.06, 0.06, 0.08);
            if (tick % 2 == 0) world.spawnParticles(ParticleTypes.SMOKE, cart.getX(), top, cart.getZ(), 1, 0.03, 0.03, 0.03, 0.01);
            if (tick % (left < 10 ? 2 : 4) == 0) cart.playSound(ModSounds.BOOMCART_FUSE, 0.7f, 1.0f + 0.04f * Math.max(0, 20 - left));
        }

        /** Forward: a big cartoon blast behind the token (particles and sounds only: nothing is touched). */
        private void blast() {
            if (cart == null) return;
            Vec3d c = cart.getPos().add(0, BoomcartEntity.HEIGHT * 0.6, 0);
            world.playSound(null, c.x, c.y, c.z, ModSounds.BOOMCART_EXPLODE, SoundCategory.NEUTRAL, 1.2f, 1f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.NEUTRAL, 0.7f, 1.2f);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
            smokeRing(c, 24, 0.35);
            world.spawnParticles(ParticleTypes.FIREWORK, c.x, c.y + 0.3, c.z, 30, 0.2, 0.2, 0.2, 0.25);
            world.spawnParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 6, 0.3, 0.2, 0.3, 0);
            // The puff that sends the token off, at its feet
            world.spawnParticles(ParticleTypes.POOF, at.x, at.y + 0.2, at.z, 10, 0.25, 0.1, 0.25, 0.05);
        }

        /** Back: a comic bonk into the token, a little blast and stars (particles and sounds only). */
        private void crash() {
            if (cart == null) return;
            Vec3d c = cart.getPos().add(0, BoomcartEntity.HEIGHT * 0.6, 0);
            Vec3d hit = c.lerp(at.add(0, 0.6, 0), 0.5);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.NEUTRAL, 0.5f, 1.8f);
            world.playSound(null, c.x, c.y, c.z, ModSounds.BOOMCART_EXPLODE, SoundCategory.NEUTRAL, 0.8f, 1.5f);
            world.spawnParticles(ParticleTypes.EXPLOSION, hit.x, hit.y, hit.z, 2, 0.2, 0.2, 0.2, 0);
            world.spawnParticles(ParticleTypes.CRIT, hit.x, hit.y + 0.4, hit.z, 16, 0.3, 0.3, 0.3, 0.3);
            world.spawnParticles(ParticleTypes.FIREWORK, hit.x, hit.y + 0.3, hit.z, 14, 0.15, 0.15, 0.15, 0.18);
            smokeRing(c, 14, 0.22);
        }

        /** A ring of smoke puffing out level from {@code c}. */
        private void smokeRing(Vec3d c, int count, double speed) {
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                double dx = Math.cos(angle), dz = Math.sin(angle);
                // count 0: (dx, dy, dz) is the particle's velocity, times speed
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x + dx * 0.4, c.y - 0.2, c.z + dz * 0.4, 0, dx, 0.05, dz, speed);
                world.spawnParticles(ParticleTypes.POOF, c.x + dx * 0.3, c.y, c.z + dz * 0.3, 0, dx, 0.1, dz, speed * 0.8);
            }
        }

        void finish() {
            if (!close()) return;
            if (cart != null && !cart.isRemoved()) cart.discard();
            dismissActors();
            onDone.run();
        }
    }

    /** Ease in and out: slow start, slow stop. */
    private static double ease(double f) {
        f = MathHelper.clamp(f, 0, 1);
        return f * f * (3 - 2 * f);
    }
}
