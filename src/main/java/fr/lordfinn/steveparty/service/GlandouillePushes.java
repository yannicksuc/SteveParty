package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;


/**
 * What a Glandouille space does (see GlandouilleTileBehavior): a tower of invulnerable Glandouilles pops up behind the
 * token that stopped there and walks the path {@code distance} spaces on, pushing that token and every token it meets
 * on the way along with it, space by space, to its destination; there the tower leaves in a little cloud. Each token it
 * meets on the way (not the one it came for) makes the top Glandouille fall off, dizzy, and vanish; meeting one with
 * its last Glandouille, that one falls too and the push stops right there, short of the destination: the tokens stay
 * on that space. The lone
 * Glandouille of the cartridge's other setting only tries: it pushes, slips, gives up and sulks, and leaves; nobody
 * moves. Its Glandouilles are never saved and always removed at the end: none ever stays in the world.
 * <p>
 * Server thread only, not saved (a restart in the middle: the Glandouilles are not saved, the tokens stay where they
 * were).
 */
public final class GlandouillePushes {
    /** The tower pops up and stomps (ticks) before it walks. */
    public static final int SETUP_TICKS = 16;
    /** Ticks to walk from a space to the next. */
    public static final int STEP_TICKS = 12;
    /** The lone Glandouille's whole show (its push_fail animation lasts 4 s). */
    public static final int LONE_TICKS = 84;
    /** The board's tower, bottom first: these kinds, over and over, as high as the cartridge says. */
    private static final GlandouilleVariant[] TOWER = {GlandouilleVariant.CLASSIC, GlandouilleVariant.YOUNG,
            GlandouilleVariant.FROSTY, GlandouilleVariant.MOSSY};
    /** A fallen Glandouille tumbles this long (ticks) before it vanishes; out of Glandouilles, the show ends after it. */
    public static final int FALL_TICKS = 24;
    /** How far behind the token (and the tokens ahead of it) the tower stands. */
    private static final double BEHIND = 0.7;

    /** The running shows, by the token that landed. */
    private static final BoardSequences<Show> RUNNING = new BoardSequences<>();
    /** For the GameTests: each show's Glandouilles as they appear. */
    public static final List<Consumer<GlandouilleEntity>> SPAWN_LISTENERS = new CopyOnWriteArrayList<>();

    private GlandouillePushes() {
    }

    public static boolean isRunning(MobEntity token) {
        return RUNNING.isRunning(token);
    }

    /**
     * The spaces {@code steps} spaces on from {@code from} along the links (the first link to a board space at a fork;
     * check points are gone through and not counted), the last one the destination. Shorter at a dead end; empty if
     * nothing leads on.
     */
    public static List<BlockPos> route(ServerWorld world, BlockPos from, int steps) {
        List<BlockPos> spaces = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(from);
        BlockPos at = from;
        int counted = 0;
        for (int guard = 0; counted < steps && guard < steps * 4 + 16; guard++) {
            BoardSpaceBlockEntity space = ABoardSpaceBlock.getBoardSpaceEntity(world, at);
            if (space == null) break;
            BlockPos next = null;
            for (BoardSpaceDestination destination : space.getStockedDestinations()) {
                if (destination.isTile() && !visited.contains(destination.position())
                        && world.getBlockEntity(destination.position()) instanceof BoardSpaceBlockEntity) {
                    next = destination.position().toImmutable();
                    break;
                }
            }
            if (next == null) break;
            visited.add(next);
            spaces.add(next);
            if (ABoardSpaceBlock.countsAsStep(world.getBlockState(next).getBlock())) counted++;
            at = next;
        }
        // it must stop on a space that takes a step (a check point is only gone through)
        while (!spaces.isEmpty() && !ABoardSpaceBlock.countsAsStep(world.getBlockState(spaces.getLast()).getBlock())) {
            spaces.removeLast();
        }
        return spaces;
    }

    /**
     * A tower of {@code height} Glandouilles pushes {@code token} (stopped on {@code from}) along {@code route};
     * {@code onDone} runs once it has gone. False (nothing happens) for an empty route.
     */
    public static boolean pushTower(ServerWorld world, BlockPos from, List<BlockPos> route, MobEntity token, int height,
                                    Runnable onDone) {
        if (route.isEmpty() || RUNNING.isRunning(token)) return false;
        List<Vec3d> points = new ArrayList<>();
        points.add(BoardSpaces.standPos(world, from));
        for (BlockPos space : route) points.add(BoardSpaces.standPos(world, space));
        List<BlockPos> spaces = new ArrayList<>();
        spaces.add(from.toImmutable());
        spaces.addAll(route);
        Show show = new Show(world, token, onDone);
        show.points = points;
        show.spaces = spaces;
        Vec3d dir = direction(points.get(0), points.get(1));
        Vec3d start = points.get(0).subtract(dir.multiply(BEHIND));
        float yaw = yawOf(dir);
        GlandouilleEntity below = null;
        for (int i = 0; i < Math.max(1, height); i++) {
            GlandouilleEntity one = show.spawn(TOWER[i % TOWER.length], start, yaw);
            if (one == null) continue;
            if (below != null) one.startRiding(below, true);
            else show.bottom = one;
            show.crew.add(one);
            below = one;
        }
        if (show.bottom == null) return false;
        for (GlandouilleEntity one : show.crew) one.actOut(GlandouilleEntity.Mood.TELEGRAPH);
        poof(world, start, 12);
        RUNNING.run(show, show::tickTower);
        return true;
    }

    /** The lone Glandouille tries to push {@code token} (on {@code from}) and can't. */
    public static boolean pushAlone(ServerWorld world, BlockPos from, MobEntity token, @Nullable BlockPos toward, Runnable onDone) {
        if (RUNNING.isRunning(token)) return false;
        Vec3d at = BoardSpaces.standPos(world, from);
        Vec3d dir = toward != null ? direction(at, BoardSpaces.standPos(world, toward)) : Vec3d.fromPolar(0, token.getYaw());
        Show show = new Show(world, token, onDone);
        show.points = List.of(at);
        show.spaces = List.of(from.toImmutable());
        Vec3d start = at.subtract(dir.multiply(BEHIND * 0.8));
        GlandouilleEntity one = show.spawn(GlandouilleVariant.CLASSIC, start, yawOf(dir));
        if (one == null) return false;
        show.bottom = one;
        show.crew.add(one);
        one.actOut(GlandouilleEntity.Mood.PUSH_FAIL);
        poof(world, start, 8);
        RUNNING.run(show, show::tickLone);
        return true;
    }

    private static Vec3d direction(Vec3d from, Vec3d to) {
        Vec3d d = new Vec3d(to.x - from.x, 0, to.z - from.z);
        return d.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : d.normalize();
    }

    private static float yawOf(Vec3d dir) {
        return (float) (MathHelper.atan2(dir.z, dir.x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
    }

    private static void poof(ServerWorld world, Vec3d at, int count) {
        world.spawnParticles(ParticleTypes.POOF, at.x, at.y + 0.5, at.z, count, 0.35, 0.4, 0.35, 0.02);
        world.playSound(null, at.x, at.y, at.z, ModSounds.GLANDOUILLE_POOF, SoundCategory.NEUTRAL, 0.8f, 1f);
    }

    /** One show: its tower (or lone Glandouille), the tokens it carries along, where it goes. */
    private static final class Show extends BoardSequences.Sequence {
        final List<GlandouilleEntity> crew = new ArrayList<>();
        /** Fallen off the tower, with the tick they fell at: they vanish {@link #FALL_TICKS} later. */
        final Map<GlandouilleEntity, Integer> fallen = new HashMap<>();
        /** Out of Glandouilles: the tick it stopped at, -1 while it walks. */
        int stoppedAt = -1;
        final List<MobEntity> pushed = new ArrayList<>();
        List<Vec3d> points = List.of();
        List<BlockPos> spaces = List.of();
        GlandouilleEntity bottom;
        int tick;

        Show(ServerWorld world, MobEntity token, Runnable onDone) {
            super(world, token, onDone);
        }

        /** One of its Glandouilles, a board actor (with its hat), at {@code at}. */
        @Nullable GlandouilleEntity spawn(GlandouilleVariant variant, Vec3d at, float yaw) {
            GlandouilleEntity one = ModEntities.GLANDOUILLE.create(world);
            if (one == null) return null;
            one.setVariant(variant);
            one.setHat(true);
            cast(one);
            one.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
            one.bodyYaw = yaw;
            one.headYaw = yaw;
            world.spawnEntity(one);
            SPAWN_LISTENERS.forEach(listener -> listener.accept(one));
            return one;
        }

        void tickTower() {
            if (done) return;
            tick++;
            tickFallen();
            if (stoppedAt >= 0) {
                if (tick - stoppedAt >= FALL_TICKS) finish();
                return;
            }
            if (bottom == null || bottom.isRemoved()) {
                finish();
                return;
            }
            if (tick == 1) gather(0);
            if (tick < SETUP_TICKS) {
                if (tick == 4 || tick == 9) bottom.playSound(ModSounds.GLANDOUILLE_STOMP, 1f, 1f);
                hold(0, 0);
                return;
            }
            if (tick == SETUP_TICKS) {
                for (GlandouilleEntity one : crew) one.actOut(GlandouilleEntity.Mood.CHARGING);
                bottom.playSound(ModSounds.GLANDOUILLE_GROWL, 1f, 1f);
            }
            int walked = tick - SETUP_TICKS;
            int segment = walked / STEP_TICKS;
            int segments = points.size() - 1;
            if (segment >= segments) {
                // at the destination: the tokens stand on it for good, the tower leaves
                Vec3d end = points.getLast();
                for (MobEntity one : pushed) {
                    if (one.isRemoved()) continue;
                    one.requestTeleport(end.x, end.y, end.z);
                    one.setVelocity(Vec3d.ZERO);
                }
                finish();
                return;
            }
            float f = (walked % STEP_TICKS + 1) / (float) STEP_TICKS;
            // a token waiting on the next space joins the pushed ones as the tower gets there
            if (walked % STEP_TICKS == STEP_TICKS - 1 && !gather(segment + 1)) {
                // out of Glandouilles: everyone stays on that space
                Vec3d end = points.get(segment + 1);
                for (MobEntity one : pushed) {
                    if (one.isRemoved()) continue;
                    one.requestTeleport(end.x, end.y, end.z);
                    one.setVelocity(Vec3d.ZERO);
                }
                stoppedAt = tick;
                return;
            }
            hold(segment, f);
            if (walked % 4 == 0) bottom.playSound(ModSounds.GLANDOUILLE_STEP, 0.8f, 1.2f);
        }

        /** The tower and the pushed tokens, {@code f} of the way along segment {@code segment}. */
        private void hold(int segment, float f) {
            Vec3d a = points.get(segment), b = points.get(Math.min(segment + 1, points.size() - 1));
            Vec3d dir = direction(points.get(Math.min(segment, points.size() - 2)), points.get(Math.min(segment + 1, points.size() - 1)));
            Vec3d front = a.lerp(b, f);
            float yaw = yawOf(dir);
            Vec3d base = front.subtract(dir.multiply(BEHIND));
            bottom.refreshPositionAndAngles(base.x, base.y, base.z, yaw, 0);
            bottom.bodyYaw = yaw;
            bottom.headYaw = yaw;
            bottom.setVelocity(Vec3d.ZERO);
            Vec3d side = new Vec3d(-dir.z, 0, dir.x);
            int n = pushed.size();
            for (int i = 0; i < n; i++) {
                MobEntity one = pushed.get(i);
                if (one.isRemoved()) continue;
                double offset = (i - (n - 1) / 2.0) * 0.45;
                Vec3d at = front.add(side.multiply(offset)).add(dir.multiply(0.1 + 0.05 * (i % 2)));
                one.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
                one.setVelocity(Vec3d.ZERO);
                if (one instanceof MobEntity mob) mob.getNavigation().stop();
            }
        }

        /**
         * The tokens standing on space {@code index} of the way join the pushed ones (the landing one first, for free);
         * each other one makes the top Glandouille fall off. False if the tower ran out of Glandouilles there.
         */
        private boolean gather(int index) {
            if (index == 0 && !pushed.contains(token)) pushed.add(token);
            if (index >= spaces.size()) return true;
            BoardSpaceBlockEntity space = ABoardSpaceBlock.getBoardSpaceEntity(world, spaces.get(index));
            if (space == null) return true;
            for (MobEntity other : space.getTokensOnMe()) {
                if (pushed.contains(other) || other instanceof GlandouilleEntity) continue;
                // not one in the middle of its own move
                if (other instanceof TokenizedEntityInterface tokenized && tokenized.steveparty$getNbSteps() > 0) continue;
                world.playSound(null, other.getX(), other.getY(), other.getZ(), ModSounds.GLANDOUILLE_RAM,
                        SoundCategory.NEUTRAL, 0.7f, 1.3f);
                boolean last = crew.size() <= 1;
                fallOff();
                if (last) return false;
                pushed.add(other);
            }
            return true;
        }

        /** The top Glandouille falls off the tower, dizzy, tumbling aside. */
        private void fallOff() {
            if (crew.isEmpty()) return;
            GlandouilleEntity top = crew.removeLast();
            top.stopRiding();
            // a board actor floats and has no AI: given back to physics for its fall
            top.setNoGravity(false);
            top.setAiDisabled(false);
            top.actOut(GlandouilleEntity.Mood.STUNNED);
            double angle = world.random.nextDouble() * Math.PI * 2;
            top.setVelocity(Math.cos(angle) * 0.18, 0.4, Math.sin(angle) * 0.18);
            top.velocityModified = true;
            top.playSound(ModSounds.GLANDOUILLE_DIZZY, 1f, 1.1f);
            fallen.put(top, tick);
        }

        /** The fallen ones vanish in a little cloud once their tumble is over. */
        private void tickFallen() {
            fallen.entrySet().removeIf(entry -> {
                GlandouilleEntity one = entry.getKey();
                if (one.isRemoved()) return true;
                if (tick - entry.getValue() < FALL_TICKS) return false;
                poof(world, one.getPos(), 6);
                one.discard();
                return true;
            });
        }

        void tickLone() {
            if (done) return;
            tick++;
            if (bottom == null || bottom.isRemoved()) {
                finish();
                return;
            }
            // pushing in vain: the token only quivers, the Glandouille's feet slip
            if (tick < 40 && tick % 6 == 0) bottom.playSound(ModSounds.GLANDOUILLE_STEP, 1f, 1.5f);
            if (tick == 42) bottom.playSound(ModSounds.GLANDOUILLE_SLIDE, 1f, 0.8f);
            if (tick == 55) bottom.playSound(ModSounds.GLANDOUILLE_SULK, 1f, 1f);
            if (tick >= LONE_TICKS) finish();
        }

        void finish() {
            if (!close()) return;
            Vec3d at = null;
            for (GlandouilleEntity one : crew) if (!one.isRemoved()) at = one.getPos();
            if (at != null) poof(world, at, 14);
            for (int i = crew.size() - 1; i >= 0; i--) {
                GlandouilleEntity one = crew.get(i);
                one.stopRiding();
                one.discard();
            }
            for (GlandouilleEntity one : fallen.keySet()) one.discard();
            fallen.clear();
            dismissActors();
            onDone.run();
        }
    }
}
