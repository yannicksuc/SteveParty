package fr.lordfinn.steveparty.entities;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Our pets ({@link FollowsOwnerAnywhere}) go wherever their owner goes. One hook for every teleport, whatever its
 * cause (/tp, an ender pearl, chorus fruit, a portal, another mod, a respawn): once a tick, each player's position is
 * compared with the last one; a jump of more than {@link #JUMP} blocks or another dimension is a teleport. Coming
 * back online counts too (pets left in another dimension come along).
 * <ul>
 *     <li>Each pet tells where it is now and then ({@link #remember}, and when it loads): its owner, its world and its
 *     chunk.</li>
 *     <li>On its owner's teleport, each of their pets that {@link FollowsOwnerAnywhere#goesWithOwner goes along} is
 *     brought to its {@link FollowsOwnerAnywhere#arrivalSpot spot} by them, once their arrival chunk is loaded.</li>
 *     <li>Near (the same world, within {@link #NEAR} blocks), it is simply moved. Farther or into another dimension it
 *     is recreated there, as vanilla does across dimensions: the same entity (UUID, name, health, data) added anew,
 *     so every client sees it arrive (a long move in place could leave it unseen).</li>
 *     <li>Its chunk is held loaded until it is brought (a ticket expiring by itself after {@link #WAIT} ticks): its
 *     owner gone, it would unload before their arrival is ready; already unloaded, it is loaded again and the pet is
 *     brought when it loads.</li>
 * </ul>
 * Kept in memory only: after a server restart a pet is known again once it has been loaded. Server thread only.
 */
public final class PetTeleports {
    /** A player moving more than this in one tick has been teleported (blocks). */
    public static final double JUMP = 12.0;
    /** Within this, a pet is moved in place; farther (or another world), recreated there. */
    public static final double NEAR = 48.0;
    /** How long a pull waits for its pet (in an unloaded chunk) or for the arrival chunk (ticks). */
    static final int WAIT = 200;
    /** Ticks after the teleport before the pets are brought (the arrival settles). */
    static final int DELAY = 3;
    private static final ChunkTicketType<ChunkPos> TICKET =
            ChunkTicketType.create("steveparty_pet", Comparator.comparingLong(ChunkPos::toLong), WAIT);

    private record Seen(RegistryKey<World> world, Vec3d pos) {
    }

    private record Known(UUID owner, RegistryKey<World> world, ChunkPos chunk) {
    }

    private static final class Pull {
        final UUID pet, owner;
        int delay = DELAY, left = WAIT;

        Pull(UUID pet, UUID owner) {
            this.pet = pet;
            this.owner = owner;
        }
    }

    private static final Map<UUID, Seen> PLAYERS = new HashMap<>();
    private static final Map<UUID, Known> PETS = new HashMap<>();
    private static final List<Pull> PULLS = new ArrayList<>();

    private PetTeleports() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(PetTeleports::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof FollowsOwnerAnywhere) remember(entity);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            Entity.RemovalReason reason = entity.getRemovalReason();
            if (entity instanceof FollowsOwnerAnywhere && reason != null && reason.shouldDestroy()) PETS.remove(entity.getUuid());
        });
        // back online: their pets come along wherever they are
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ownerMoved(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAYERS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PLAYERS.clear();
            PETS.clear();
            PULLS.clear();
        });
    }

    /** A pet says where it is (its owner, world and chunk); a wild one is forgotten. */
    public static void remember(Entity pet) {
        if (!(pet instanceof FollowsOwnerAnywhere follower) || !(pet.getWorld() instanceof ServerWorld world)) return;
        UUID owner = follower.followedOwner();
        if (owner == null) PETS.remove(pet.getUuid());
        else PETS.put(pet.getUuid(), new Known(owner, world.getRegistryKey(), pet.getChunkPos()));
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Seen now = new Seen(player.getWorld().getRegistryKey(), player.getPos());
            Seen last = PLAYERS.put(player.getUuid(), now);
            if (last != null && (last.world() != now.world() || last.pos().squaredDistanceTo(now.pos()) > JUMP * JUMP)) {
                ownerMoved(player);
            }
        }
        if (PULLS.isEmpty()) return;
        for (Iterator<Pull> it = PULLS.iterator(); it.hasNext(); ) {
            Pull pull = it.next();
            if (pull.delay > 0) {
                pull.delay--;
                continue;
            }
            if (tryPull(server, pull) || --pull.left <= 0) it.remove();
        }
    }

    /** {@code owner} has been teleported (or came online): their pets are to be brought to them. */
    public static void ownerMoved(ServerPlayerEntity owner) {
        UUID id = owner.getUuid();
        for (Map.Entry<UUID, Known> entry : PETS.entrySet()) {
            if (!entry.getValue().owner().equals(id)) continue;
            UUID pet = entry.getKey();
            PULLS.removeIf(pull -> pull.pet.equals(pet));
            PULLS.add(new Pull(pet, id));
            // its chunk kept loaded until it is brought (its owner gone, it would unload): loaded again if it was not
            ServerWorld world = owner.getServer().getWorld(entry.getValue().world());
            ChunkPos chunk = entry.getValue().chunk();
            if (world != null) world.getChunkManager().addTicket(TICKET, chunk, 2, chunk);
        }
    }

    /** True when done (brought, or not to be brought); false to try again next tick. */
    private static boolean tryPull(MinecraftServer server, Pull pull) {
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(pull.owner);
        if (owner == null || !owner.isAlive() || owner.isSpectator()) return true;
        Entity pet = null;
        for (ServerWorld world : server.getWorlds()) {
            pet = world.getEntity(pull.pet);
            if (pet != null) break;
        }
        if (pet == null) return false; // its chunk is loading
        if (!(pet instanceof FollowsOwnerAnywhere follower) || !pet.isAlive() || !follower.goesWithOwner(owner)) return true;
        ServerWorld to = owner.getServerWorld();
        if (!to.shouldTickEntity(owner.getBlockPos())) return false; // its arrival there is ready for entities
        if (pet.getWorld() == to && pet.squaredDistanceTo(owner) < JUMP * JUMP) return true; // already by them
        bring(pet, to, follower.arrivalSpot(owner), owner.getYaw());
        return true;
    }

    /**
     * Brings {@code pet} to {@code spot} (its feet) in {@code to}: moved in place when near, else recreated there (the
     * same entity, every client seeing it arrive). Returns the pet as it is now (a new object when recreated).
     */
    public static @Nullable Entity bring(Entity pet, ServerWorld to, Vec3d spot, float yaw) {
        if (pet.hasVehicle()) pet.stopRiding();
        if (pet.getWorld() == to && pet.getPos().squaredDistanceTo(spot) < NEAR * NEAR) {
            pet.refreshPositionAndAngles(spot.x, spot.y, spot.z, yaw, pet.getPitch());
            pet.setVelocity(Vec3d.ZERO);
            if (pet instanceof MobEntity mob) mob.getNavigation().stop();
            return pet;
        }
        Entity copy = pet.getType().create(to);
        if (copy == null) return null;
        copy.copyFrom(pet);
        copy.refreshPositionAndAngles(spot.x, spot.y, spot.z, yaw, pet.getPitch());
        copy.setVelocity(Vec3d.ZERO);
        copy.setHeadYaw(yaw);
        pet.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        to.onDimensionChanged(copy);
        remember(copy);
        return copy;
    }

    /** A free spot for {@code pet} next to {@code owner} (its feet), else at their feet. */
    public static Vec3d spotNear(Entity pet, ServerPlayerEntity owner) {
        ServerWorld world = owner.getServerWorld();
        Vec3d at = owner.getPos();
        double[][] around = {{1.5, 0.5, 0}, {-1.5, 0.5, 0}, {0, 0.5, 1.5}, {0, 0.5, -1.5}, {1.2, 1.2, 1.2},
                {-1.2, 1.2, -1.2}, {0, 1.5, 0}};
        for (double[] offset : around) {
            Vec3d spot = at.add(offset[0], offset[1], offset[2]);
            if (world.isSpaceEmpty(pet, pet.getType().getDimensions().getBoxAt(spot))
                    && !world.getBlockState(BlockPos.ofFloored(spot)).isLiquid()) return spot;
        }
        return at;
    }
}
