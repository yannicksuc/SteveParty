package fr.lordfinn.steveparty.blocks.custom.pipe;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Travelling through pipes.
 * <ul>
 *     <li><b>Going in</b> a mouth: a right click on it, sneaking in front of it (looking at it, close) or in it, or
 *     coming at it fast enough (falling, flying with elytra: no damage, the speed is kept inside). Mobs (tokens
 *     included) and items go in by getting into the mouth's hollow. The traveller shrinks to fit and rides a
 *     {@link PipeCarrierEntity} along the pipes.</li>
 *     <li><b>The way</b>: to another end of the network, picked at random. At an open end (a mouth) it pops out. At a
 *     capped end (the pipe goes into a solid block: a warp) it comes out of the nearest mouth of the same colour in another network
 *     ({@link PipeNetworks#nearestMouth}), or where the cartridge of that end says ({@link PipeDestinationProvider});
 *     with nowhere to go (or a mouth blocked by a block in front of it), it travels back through the pipes and comes out of the mouth it went in.</li>
 *     <li>Inside, nothing hurts it (walls, landing, cramming) and it does not collide; it comes out with the speed it
 *     had inside, without any fall damage from before.</li>
 * </ul>
 * Everything happens when something goes in: no scan of the world, only the players who sneak are looked at.
 */
public final class PipeTravel {
    /** Speed inside (blocks per tick), and the fastest one kept from a fast entry. */
    public static final double BASE_SPEED = 0.6, MAX_SPEED = 3.0;
    /** Speed toward a mouth from which a player goes in without sneaking (blocks per tick, a fall of about 2 blocks). */
    public static final double FAST_ENTRY = 0.55;
    /** Ticks after coming out before going in again. */
    public static final int COOLDOWN = 20;
    /** Largest size of a traveller inside (blocks): bigger ones shrink to it. */
    public static final double ROOM = 0.7;
    private static final Identifier SHRINK = Steveparty.id("pipe_travel");

    /** Called when a traveller comes out of a mouth (hook for the mini-games). */
    public static final Event<Arrived> ARRIVED = EventFactory.createArrayBacked(Arrived.class, listeners -> (world, entity, mouth, opening) -> {
        for (Arrived listener : listeners) listener.onArrived(world, entity, mouth, opening);
    });

    @FunctionalInterface
    public interface Arrived {
        void onArrived(ServerWorld world, Entity entity, BlockPos mouth, Direction opening);
    }

    private record Entry(BlockPos mouth, Direction opening, double speed) {}

    /** Entities going in at the end of the tick (not while they move). */
    private static final Map<ServerWorld, Map<Entity, Entry>> ENTRIES = new HashMap<>();
    /** When each traveller last came out (world time). */
    private static final Map<UUID, Long> LEFT_AT = new HashMap<>();

    private PipeTravel() {}

    public static void initialize() {
        ServerTickEvents.END_WORLD_TICK.register(PipeTravel::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !isTravelling(entity)
                || !(source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.FALL) || source.isOf(DamageTypes.FLY_INTO_WALL)
                || source.isOf(DamageTypes.CRAMMING)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ENTRIES.clear();
            LEFT_AT.clear();
        });
    }

    public static boolean isTravelling(Entity entity) {
        if (entity.getVehicle() instanceof PipeCarrierEntity) return true;
        Map<Entity, Entry> entries = entity.getWorld() instanceof ServerWorld world ? ENTRIES.get(world) : null;
        return entries != null && entries.containsKey(entity);
    }

    /** Players, mobs and items; not riding anything nor carrying anyone, and not just out of a pipe. */
    public static boolean canTravel(ServerWorld world, Entity entity) {
        if (!(entity instanceof LivingEntity || entity instanceof ItemEntity) || entity instanceof ArmorStandEntity) return false;
        if (!entity.isAlive() || entity.isSpectator() || entity.hasVehicle() || entity.hasPassengers()) return false;
        Long left = LEFT_AT.get(entity.getUuid());
        return left == null || world.getTime() - left >= COOLDOWN;
    }

    /**
     * {@code entity} goes into the mouth of the pipe at {@code mouth} opening on {@code opening}, at the end of the
     * tick, at {@code speed} (at least {@link #BASE_SPEED}).
     *
     * @return false if it cannot travel
     */
    public static boolean enter(ServerWorld world, BlockPos mouth, Direction opening, Entity entity, double speed) {
        if (!canTravel(world, entity) || PipeShape.mouth(world.getBlockState(mouth), opening) == null) return false;
        ENTRIES.computeIfAbsent(world, w -> new LinkedHashMap<>()).putIfAbsent(entity, new Entry(mouth.toImmutable(), opening, speed));
        return true;
    }

    private static void tick(ServerWorld world) {
        Map<Entity, Entry> entries = ENTRIES.remove(world);
        if (entries != null) entries.forEach((entity, entry) -> start(world, entity, entry.mouth(), entry.opening(), entry.speed()));
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSneaking()) sneak(world, player);
        }
    }

    // ---------------------------------------------------------------- going in

    /** The mouth of this pipe the point {@code eye} is in front of (the one most facing it), or null. */
    public static @Nullable Direction mouthFacing(BlockState state, BlockPos pos, Vec3d eye) {
        if (!PipeBlock.isPipe(state)) return null;
        Vec3d offset = eye.subtract(Vec3d.ofCenter(pos));
        Direction best = null;
        double bestDot = 0.3;
        for (PipeShape.End end : PipeShape.ends(state)) {
            if (end.capped()) continue;
            double dot = offset.dotProduct(Vec3d.of(end.dir().getVector()));
            if (dot > bestDot) {
                best = end.dir();
                bestDot = dot;
            }
        }
        return best;
    }

    /** The hollow of a mouth (where what goes in stands), in world coordinates. */
    public static Box hollow(BlockPos pos, Direction opening) {
        double lo = 2 / 16.0, hi = 14 / 16.0, depth = PipeShape.HOLLOW / 16.0;
        double[] min = {lo, lo, lo}, max = {hi, hi, hi};
        int axis = opening.getAxis().ordinal();
        boolean positive = opening.getDirection() == Direction.AxisDirection.POSITIVE;
        min[axis] = positive ? 1 - depth : 0;
        max[axis] = positive ? 1 : depth;
        return new Box(min[0], min[1], min[2], max[0], max[1], max[2]).offset(pos);
    }

    /** How fast the entity moves (the server does not keep a player's velocity: its last move). */
    private static Vec3d motion(Entity entity) {
        if (entity instanceof PlayerEntity) return new Vec3d(entity.getX() - entity.prevX, entity.getY() - entity.prevY, entity.getZ() - entity.prevZ);
        return entity.getVelocity();
    }

    /**
     * An entity touching a pipe: in the hollow of a mouth, a mob or an item goes in; a player only if it comes fast
     * (else it sneaks or clicks).
     */
    public static void touch(ServerWorld world, BlockPos pos, BlockState state, Entity entity) {
        if (entity.hasVehicle()) return;
        for (PipeShape.End end : PipeShape.ends(state)) {
            if (end.capped() || !entity.getBoundingBox().intersects(hollow(pos, end.dir()))) continue;
            double toward = -motion(entity).dotProduct(Vec3d.of(end.dir().getVector()));
            if (!(entity instanceof PlayerEntity) || toward >= FAST_ENTRY) enter(world, pos, end.dir(), entity, toward);
            return;
        }
    }

    /**
     * Landing in an upward mouth: a mob or an item goes in; a player if it fell fast enough or sneaks. It takes no
     * fall damage then.
     *
     * @return true if it goes in (no fall damage)
     */
    public static boolean land(ServerWorld world, BlockPos pos, BlockState state, Entity entity, float fallDistance) {
        if (PipeShape.mouth(state, Direction.UP) == null || !entity.getBoundingBox().expand(0, 0.01, 0).intersects(hollow(pos, Direction.UP))) return false;
        double speed = Math.max(-entity.getVelocity().y, Math.sqrt(2 * 0.08 * fallDistance));
        if (entity instanceof PlayerEntity && speed < FAST_ENTRY && !entity.isSneaking()) return false;
        if (!enter(world, pos, Direction.UP, entity, speed)) return false;
        entity.fallDistance = 0;
        return true;
    }

    /** A sneaking player: in the mouth it stands in, or the one it looks at up close. */
    private static void sneak(ServerWorld world, ServerPlayerEntity player) {
        if (!canTravel(world, player)) return;
        BlockPos feet = BlockPos.ofFloored(player.getX(), player.getY() + 0.01, player.getZ());
        BlockState under = world.getBlockState(feet);
        if (PipeShape.mouth(under, Direction.UP) != null && player.getBoundingBox().expand(0, 0.01, 0).intersects(hollow(feet, Direction.UP))) {
            enter(world, feet, Direction.UP, player, 0);
            return;
        }
        // Where it looks (its yaw and pitch: the server does not keep a player's head turned like the client does)
        Vec3d eye = player.getEyePos();
        Vec3d reach = eye.add(Vec3d.fromPolar(player.getPitch(), player.getYaw()).multiply(2.0));
        BlockHitResult blockHit = world.raycast(new RaycastContext(eye, reach, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));
        if (blockHit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = blockHit.getBlockPos();
        Direction mouth = mouthFacing(world.getBlockState(pos), pos, player.getEyePos());
        if (mouth != null) enter(world, pos, mouth, player, 0);
    }

    private static void start(ServerWorld world, Entity entity, BlockPos mouth, Direction opening, double speed) {
        if (entity.isRemoved() || entity.hasVehicle() || PipeShape.mouth(world.getBlockState(mouth), opening) == null) return;
        PipeNetworks.Network network = PipeNetworks.of(world).network(mouth);
        if (network == null) return;
        List<PipeNetworks.End> others = new ArrayList<>();
        for (PipeNetworks.End end : network.ends()) {
            if (!(end.pos().equals(mouth) && end.dir() == opening)) others.add(end);
        }
        if (others.isEmpty()) return;
        PipeNetworks.End target = others.get(world.random.nextInt(others.size()));
        PipeNetworks.End origin = new PipeNetworks.End(mouth, opening, false);
        List<Vec3d> points = route(network, origin, target);
        if (points.isEmpty()) return;
        PipeCarrierEntity carrier = PipeCarrierEntity.create(world, points, MathHelper.clamp(speed, BASE_SPEED, MAX_SPEED), target, origin);
        world.spawnEntity(carrier);
        if (!entity.startRiding(carrier, true)) {
            carrier.discard();
            return;
        }
        Vec3d at = face(mouth, opening, 0.5);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS, 1.0F, 0.6F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_PUFFER_FISH_BLOW_OUT, SoundCategory.BLOCKS, 0.6F, 1.5F);
    }

    /** The points along the pipes from the end {@code from} (its opening) to the end {@code to}. */
    private static List<Vec3d> route(PipeNetworks.Network network, PipeNetworks.End from, PipeNetworks.End to) {
        List<BlockPos> pipes = network.path(from.pos(), to.pos());
        if (pipes.isEmpty()) return List.of();
        List<Vec3d> points = new ArrayList<>();
        points.add(face(from.pos(), from.dir(), 0.5));
        for (BlockPos pipe : pipes) points.add(Vec3d.ofCenter(pipe));
        points.add(face(to.pos(), to.dir(), to.capped() ? 0.3 : 0.5));
        return points;
    }

    private static Vec3d face(BlockPos pos, Direction dir, double distance) {
        return Vec3d.ofCenter(pos).add(Vec3d.of(dir.getVector()).multiply(distance));
    }

    // ---------------------------------------------------------------- the way out

    /** The carrier is at the end of its path. */
    public static void arrive(ServerWorld world, PipeCarrierEntity carrier) {
        Entity traveller = carrier.getFirstPassenger();
        PipeNetworks.End target = carrier.target();
        if (traveller == null || target == null) {
            carrier.discard();
            return;
        }
        if (target.capped()) {
            PipeNetworks.End warp = warpDestination(world, target, traveller);
            if (warp != null && !blocked(world, warp)) {
                warp(world, carrier, traveller, target, warp);
            } else {
                goBack(world, carrier, traveller);
            }
        } else if (blocked(world, target) && !carrier.hasReturned()) {
            goBack(world, carrier, traveller);
        } else {
            exit(world, carrier, traveller, target);
        }
    }

    /** Where a capped end warps to: its cartridge's choice, else the nearest mouth of the same colour in another network. */
    private static @Nullable PipeNetworks.End warpDestination(ServerWorld world, PipeNetworks.End capped, Entity traveller) {
        if (world.getBlockEntity(capped.pos()) instanceof PipeBlockEntity pipe) {
            PipeDestinationProvider provider = pipe.destinationProvider();
            PipeDestinationProvider.Exit exit = provider == null ? null : provider.destination(world, capped.pos(), traveller);
            if (exit != null && PipeShape.mouth(world.getBlockState(exit.pos()), exit.opening()) != null) {
                return new PipeNetworks.End(exit.pos(), exit.opening(), false);
            }
        }
        PipeNetworks networks = PipeNetworks.of(world);
        PipeNetworks.Network own = networks.network(capped.pos());
        return own == null ? null : networks.nearestMouth(capped.pos(), own);
    }

    /** A block in front of the mouth (nothing can come out). */
    private static boolean blocked(ServerWorld world, PipeNetworks.End end) {
        BlockPos front = end.pos().offset(end.dir());
        return !world.getBlockState(front).getCollisionShape(world, front).isEmpty();
    }

    /** Back to where it came in (once). */
    private static void goBack(ServerWorld world, PipeCarrierEntity carrier, Entity traveller) {
        PipeNetworks.End origin = carrier.origin();
        PipeNetworks.End target = carrier.target();
        PipeNetworks.Network network = target == null ? null : PipeNetworks.of(world).network(target.pos());
        if (origin == null || network == null || carrier.hasReturned() || PipeShape.mouth(world.getBlockState(origin.pos()), origin.dir()) == null) {
            if (target != null && !target.capped()) exit(world, carrier, traveller, target);
            else if (origin != null) exit(world, carrier, traveller, origin);
            else carrier.discard();
            return;
        }
        List<BlockPos> pipes = network.path(target.pos(), origin.pos());
        if (pipes.isEmpty()) {
            exit(world, carrier, traveller, origin);
            return;
        }
        List<Vec3d> points = new ArrayList<>();
        points.add(carrier.getPos());
        for (BlockPos pipe : pipes) points.add(Vec3d.ofCenter(pipe));
        points.add(face(origin.pos(), origin.dir(), 0.5));
        carrier.markReturned();
        carrier.setLeg(points, carrier.speed(), origin);
        world.playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.7F, 0.6F);
    }

    /** From a capped end to another pipe's mouth: out of this carrier, into a new one there. */
    private static void warp(ServerWorld world, PipeCarrierEntity carrier, Entity traveller, PipeNetworks.End from, PipeNetworks.End to) {
        double speed = carrier.speed();
        Vec3d here = Vec3d.ofCenter(from.pos());
        world.playSound(null, here.x, here.y, here.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 0.4F, 1.6F);
        Vec3d center = Vec3d.ofCenter(to.pos());
        carrier.setDismountAt(center);
        traveller.stopRiding();
        carrier.discard();
        traveller.requestTeleport(center.x, center.y - traveller.getHeight() / 2, center.z);
        List<Vec3d> points = List.of(center, face(to.pos(), to.dir(), 0.5));
        PipeCarrierEntity next = PipeCarrierEntity.create(world, points, speed, to, to);
        next.markReturned();
        world.spawnEntity(next);
        if (!traveller.startRiding(next, true)) {
            next.discard();
            exit(world, null, traveller, to);
        }
    }

    /**
     * Out of the mouth {@code end}: standing in front of it (on top of an upward one, under a downward one), thrown
     * out at the speed it had inside (popping up out of an upward one).
     */
    private static void exit(ServerWorld world, @Nullable PipeCarrierEntity carrier, Entity traveller, PipeNetworks.End end) {
        double speed = carrier == null ? BASE_SPEED : carrier.speed();
        Direction dir = end.dir();
        Vec3d face = face(end.pos(), dir, 0.5);
        if (carrier != null) {
            carrier.setDismountAt(face);
            traveller.stopRiding();
            carrier.discard();
        }
        double width = traveller.getWidth(), height = traveller.getHeight();
        Vec3d at = switch (dir) {
            case UP -> new Vec3d(face.x, end.pos().getY() + 1.0, face.z);
            case DOWN -> new Vec3d(face.x, end.pos().getY() - height - 0.01, face.z);
            default -> new Vec3d(face.x + dir.getOffsetX() * (width / 2 + 0.02), end.pos().getY(), face.z + dir.getOffsetZ() * (width / 2 + 0.02));
        };
        traveller.requestTeleport(at.x, at.y, at.z);
        Vec3d velocity = Vec3d.of(dir.getVector()).multiply(MathHelper.clamp(speed, 0.3, 1.2));
        if (dir == Direction.UP) velocity = new Vec3d(0, Math.max(velocity.y, 0.5), 0);
        if (!(traveller instanceof PlayerEntity) && dir.getAxis().isVertical()) {
            // Mobs and items hop off to a side instead of falling back in
            double angle = world.random.nextDouble() * Math.PI * 2, push = 0.12 + world.random.nextDouble() * 0.08;
            velocity = velocity.add(Math.cos(angle) * push, 0, Math.sin(angle) * push);
        }
        traveller.setVelocity(velocity);
        traveller.velocityModified = true;
        traveller.fallDistance = 0;
        LEFT_AT.put(traveller.getUuid(), world.getTime());
        world.playSound(null, face.x, face.y, face.z, SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP, SoundCategory.BLOCKS, 0.7F, 1.3F);
        world.playSound(null, face.x, face.y, face.z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.BLOCKS, 1.0F, 1.2F);
        ARRIVED.invoker().onArrived(world, traveller, end.pos(), dir);
    }

    // ---------------------------------------------------------------- size inside

    /** Inside, a traveller bigger than {@link #ROOM} shrinks to it (a modifier never saved); items are not picked up. */
    public static void shrink(Entity passenger) {
        if (passenger instanceof ItemEntity item) item.setPickupDelayInfinite();
        if (!(passenger instanceof LivingEntity living)) return;
        EntityAttributeInstance scale = living.getAttributeInstance(EntityAttributes.SCALE);
        if (scale == null || scale.getModifier(SHRINK) != null) return;
        double size = Math.max(living.getWidth(), living.getHeight());
        if (size <= ROOM) return;
        scale.addTemporaryModifier(new EntityAttributeModifier(SHRINK, ROOM / size - 1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        living.calculateDimensions();
    }

    public static void unshrink(Entity passenger) {
        if (passenger instanceof ItemEntity item) item.setPickupDelay(10);
        if (!(passenger instanceof LivingEntity living)) return;
        EntityAttributeInstance scale = living.getAttributeInstance(EntityAttributes.SCALE);
        if (scale != null && scale.removeModifier(SHRINK)) living.calculateDimensions();
    }
}
