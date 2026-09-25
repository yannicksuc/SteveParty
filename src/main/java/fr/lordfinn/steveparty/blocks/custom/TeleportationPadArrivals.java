package fr.lordfinn.steveparty.blocks.custom;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * "Just arrived" guard of the teleportation pads: a player teleported onto a pad holding a « Here we go » book must
 * not be sent away again as soon as he lands. The pads ignore him until he steps off the place he landed on.
 * <p>
 * Server-side, in memory only (a reload drops the guards: nobody is standing on a pad at that time anyway).
 */
public final class TeleportationPadArrivals {
    private static final Map<UUID, Arrival> ARRIVALS = new HashMap<>();

    /** Where a player landed: the block column he landed in, two blocks high (a jump on the pad doesn't count as leaving it). */
    private record Arrival(RegistryKey<World> world, Box area) {
        boolean contains(ServerPlayerEntity player) {
            return player.getWorld().getRegistryKey() == world && player.getBoundingBox().intersects(area);
        }
    }

    private TeleportationPadArrivals() {}

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(TeleportationPadArrivals::forgetPlayersWhoLeft);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ARRIVALS.clear());
    }

    /** The player was just teleported at {@code landing} (feet position). */
    public static void markArrived(ServerPlayerEntity player, Vec3d landing) {
        BlockPos block = BlockPos.ofFloored(landing);
        Box area = new Box(block.getX(), block.getY(), block.getZ(), block.getX() + 1, block.getY() + 2, block.getZ() + 1);
        ARRIVALS.put(player.getUuid(), new Arrival(player.getWorld().getRegistryKey(), area));
    }

    /** @return true while the player stands where he was teleported: the pads must not teleport him. */
    public static boolean isJustArrived(ServerPlayerEntity player) {
        Arrival arrival = ARRIVALS.get(player.getUuid());
        if (arrival == null) return false;
        if (arrival.contains(player)) return true;
        ARRIVALS.remove(player.getUuid());
        return false;
    }

    private static void forgetPlayersWhoLeft(MinecraftServer server) {
        if (ARRIVALS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Arrival>> iterator = ARRIVALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Arrival> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || player.isRemoved() || !entry.getValue().contains(player))
                iterator.remove();
        }
    }
}
