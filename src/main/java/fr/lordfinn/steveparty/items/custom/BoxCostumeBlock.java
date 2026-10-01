package fr.lordfinn.steveparty.items.custom;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A player hidden in a Box Costume who stops becomes a block of the grid.
 * <ul>
 * <li>Hidden, he still sneak-walks and jumps freely. Once he has stayed {@link #STILL_TICKS} at the same place, on the
 * ground, he is gently pushed to the centre of the block cell he stands in (his own client does it, like any of his
 * moves: nothing to correct, no rubber-banding). Moving again is free; he is pushed again after the next stop.</li>
 * <li>Standing still on the centre of a valid cell, he is <b>block-aligned</b>: his bounding box is that cell's full
 * cube, and he is a hard obstacle like a shulker or a boat: players and mobs stand on him, jump on him and bump into
 * him, and don't push him. Both sides work it out from what they already know (hidden, position, on ground), so the
 * one jumping on him has no jitter. As soon as he moves or stands up he is an ordinary player again: whoever stood
 * on him just falls.</li>
 * <li>A valid cell: feet on the full top face of the block below, the cell free of blocks and of water or lava.
 * Elsewhere (slab, stairs, fence, water, a block in the way...) he is neither pushed nor a block.</li>
 * </ul>
 * Attacks and projectiles still hit him as usual (on the cube while he is one).
 */
public final class BoxCostumeBlock {
    /** Still for this long (2 s), a hidden player is pushed to the grid. */
    public static final int STILL_TICKS = 40;
    /** Share of the way to the cell's centre covered each tick, and the distance from which he is put right on it. */
    private static final double PUSH = 0.25, PUSH_END = 0.004;
    /** On the centre / unmoved, to these tolerances (positions of other players arrive rounded to 1/4096). */
    private static final double CENTRED = 0.01, UNMOVED_SQUARED = 1.0E-8;
    /** The cube: a full block, the eyes where a sneaking player's are. */
    private static final EntityDimensions CUBE = EntityDimensions.fixed(1.0F, 1.0F).withEyeHeight(1.27F);

    /** State kept on every player (see PlayerEntityBoxCostumeMixin). */
    public interface Hider {
        boolean steveparty$isBlockAligned();

        void steveparty$setBlockAligned(boolean aligned);

        /** Ticks spent unmoved while hidden. */
        int steveparty$getStillTicks();

        void steveparty$setStillTicks(int ticks);

        /** Where he was at the end of the last tick (null: unknown). */
        @Nullable
        Vec3d steveparty$getLastPos();

        void steveparty$setLastPos(@Nullable Vec3d pos);
    }

    private BoxCostumeBlock() {
    }

    /** A block of the grid right now: others collide with his cell's full cube. */
    public static boolean isBlockAligned(@Nullable PlayerEntity player) {
        return player instanceof Hider hider && hider.steveparty$isBlockAligned();
    }

    /** @return the cube's dimensions if the player is block-aligned, else null (his usual ones) */
    @Nullable
    public static EntityDimensions dimensions(PlayerEntity player) {
        return isBlockAligned(player) ? CUBE : null;
    }

    /** Every tick, on both sides, for every player. */
    public static void tick(PlayerEntity player) {
        // Only the side that moves the player pushes him: his own client
        tick(player, player.isMainPlayer());
    }

    /** @param moves whether this side moves the player (public for the GameTests, which have no client) */
    public static void tick(PlayerEntity player, boolean moves) {
        if (!(player instanceof Hider hider)) return;
        if (moves) pushToGrid(player);
        else trackStillness(player, hider);
        updateAligned(player);
        hider.steveparty$setLastPos(player.getPos());
    }

    /** Counts the ticks spent unmoved while hidden. @return whether he hasn't moved since the last tick */
    private static boolean trackStillness(PlayerEntity player, Hider hider) {
        Vec3d last = hider.steveparty$getLastPos();
        boolean unmoved = last != null && last.squaredDistanceTo(player.getPos()) < UNMOVED_SQUARED;
        hider.steveparty$setStillTicks(unmoved && BoxCostumeItem.isHiddenInBox(player) ? hider.steveparty$getStillTicks() + 1 : 0);
        return unmoved;
    }

    /**
     * One tick of the push to the grid, for the side that moves the player: once he has
     * been still long enough on a valid cell, a quarter of the way to its centre each tick, then right on it. His own
     * moves (any position other than where the push left him) start the wait again.
     */
    private static void pushToGrid(PlayerEntity player) {
        if (!(player instanceof Hider hider)) return;
        boolean unmoved = trackStillness(player, hider);
        BlockPos cell = unmoved && hider.steveparty$getStillTicks() >= STILL_TICKS ? validCell(player) : null;
        if (cell == null) return;
        double dx = cell.getX() + 0.5 - player.getX(), dz = cell.getZ() + 0.5 - player.getZ();
        if (dx == 0 && dz == 0) return;
        boolean arrives = dx * dx + dz * dz < PUSH_END * PUSH_END;
        double x = arrives ? cell.getX() + 0.5 : player.getX() + dx * PUSH, z = arrives ? cell.getZ() + 0.5 : player.getZ() + dz * PUSH;
        player.setPosition(x, cell.getY(), z);
        player.setVelocity(0, player.getVelocity().y, 0);
        // The push itself is not a move of his: he is still "unmoved" next tick
        hider.steveparty$setLastPos(player.getPos());
    }

    /** Works out whether the player is a block of the grid, resizing him when it changes. */
    private static void updateAligned(PlayerEntity player) {
        if (!(player instanceof Hider hider)) return;
        Vec3d last = hider.steveparty$getLastPos();
        BlockPos cell = BoxCostumeItem.isHiddenInBox(player) && last != null && last.squaredDistanceTo(player.getPos()) < UNMOVED_SQUARED
                ? validCell(player) : null;
        boolean aligned = cell != null && Math.abs(cell.getX() + 0.5 - player.getX()) < CENTRED
                && Math.abs(cell.getZ() + 0.5 - player.getZ()) < CENTRED;
        if (aligned == hider.steveparty$isBlockAligned()) return;
        hider.steveparty$setBlockAligned(aligned);
        player.calculateDimensions();
    }

    /**
     * @return the block cell a hidden player can be a block of: the one he stands in, feet on the full top face of
     * the block below, free of blocks and fluids; null if he is not hidden, not on the ground or the cell is no good
     */
    @Nullable
    public static BlockPos validCell(PlayerEntity player) {
        if (!BoxCostumeItem.isHiddenInBox(player) || !player.isOnGround()) return null;
        double y = player.getY();
        int floorY = MathHelper.floor(y + 0.5);
        if (Math.abs(y - floorY) > CENTRED) return null;
        World world = player.getWorld();
        BlockPos cell = BlockPos.ofFloored(player.getX(), floorY, player.getZ());
        BlockPos below = cell.down();
        if (!world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) return null;
        if (!world.getFluidState(cell).isEmpty()) return null;
        if (!world.isSpaceEmpty(player, new Box(cell).contract(1.0E-3))) return null;
        return cell;
    }
}
