package fr.lordfinn.steveparty.entities.custom.frousseux;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A wild Frousseux's prank in multiplayer: what it stole off a player, it carries to <b>another</b> player near (the
 * nearest one in survival or adventure, alive, within {@link #RANGE} blocks of the same world, over loaded chunks) and
 * drops it at their feet. Nobody else around (solo): it keeps it and flees, as ever. The flight itself is
 * {@link FrousseuxFlight.Deliver}; its state lives in {@link FrousseuxEntity}.
 */
public final class FrousseuxCourier {
    /** Someone this close (blocks) when it steals gets the item. */
    public static final double RANGE = 48;
    /** On its way, it keeps to its receiver while they stay this close; farther, it picks someone else (or gives up). */
    public static final double KEEP_RANGE = 64;
    /** This close to the receiver (blocks, to their hitbox), it gives. */
    public static final double GIVE_RANGE = 1.6;
    /** It gives up after this long (ticks) without reaching them: it keeps the item and flees. */
    public static final int GIVE_UP_TICKS = 900;
    /** The dropped gift: no one picks it up before this many ticks (a moment to see it land). */
    public static final int GIFT_PICKUP_DELAY = 10;

    private FrousseuxCourier() {
    }

    /** The nearest player it may carry the item to, never {@code victim}; or null (nobody: it flees with it). */
    public static @Nullable ServerPlayerEntity receiver(ServerWorld world, FrousseuxEntity frousseux, @Nullable UUID victim) {
        ServerPlayerEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!canReceive(world, frousseux, player, victim, RANGE)) continue;
            double distance = player.squaredDistanceTo(frousseux);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * {@code player} may get the item: not the victim, in survival or adventure, alive, in its world within
     * {@code range} blocks, and every chunk on the way loaded.
     */
    public static boolean canReceive(ServerWorld world, FrousseuxEntity frousseux, ServerPlayerEntity player,
                                     @Nullable UUID victim, double range) {
        if (player.getUuid().equals(victim) || player.isSpectator() || player.isCreative() || !player.isAlive()) return false;
        if (player.getWorld() != world || player.isDisconnected()) return false;
        if (player.squaredDistanceTo(frousseux) > range * range) return false;
        return loadedBetween(world, frousseux.getPos(), player.getPos());
    }

    /** Every chunk on the straight line from {@code from} to {@code to} loaded (sampled every 8 blocks). */
    static boolean loadedBetween(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        int steps = Math.max(1, MathHelper.ceil(delta.length() / 8));
        for (int i = 0; i <= steps; i++) {
            Vec3d at = from.add(delta.multiply((double) i / steps));
            if (!world.isChunkLoaded(BlockPos.ofFloored(at))) return false;
        }
        return true;
    }
}
