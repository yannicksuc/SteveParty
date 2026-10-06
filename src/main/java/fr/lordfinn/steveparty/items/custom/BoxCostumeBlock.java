package fr.lordfinn.steveparty.items.custom;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
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
 * him, and don't push him. Every side works it out from what it already knows (hidden, position), so the
 * one jumping on him has no jitter; once a block, he stays one while hidden on that centre, whoever stands on him.
 * As soon as he moves or stands up he is an ordinary player again: whoever stood on him just falls.</li>
 * <li>A valid spot: feet on a floor whose top is a whole flat square (a full block, but also a dirt path, farmland, a
 * slab, a carpet...), the cube above it free of blocks and of water or lava. Elsewhere (stairs, fence, water, a
 * block in the way...) he is neither pushed nor a block. The cube stands on that floor: on a dirt path it is 1/16
 * lower than on grass.</li>
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
     * been still long enough on the ground on a valid spot, a quarter of the way to its centre each tick, then right
     * on it. His own moves (any position other than where the push left him) start the wait again.
     */
    private static void pushToGrid(PlayerEntity player) {
        if (!(player instanceof Hider hider)) return;
        boolean unmoved = trackStillness(player, hider);
        Vec3d spot = unmoved && hider.steveparty$getStillTicks() >= STILL_TICKS && player.isOnGround() ? blockSpot(player) : null;
        if (spot == null) return;
        double dx = spot.x - player.getX(), dz = spot.z - player.getZ();
        if (dx == 0 && dz == 0) return;
        boolean arrives = dx * dx + dz * dz < PUSH_END * PUSH_END;
        double x = arrives ? spot.x : player.getX() + dx * PUSH, z = arrives ? spot.z : player.getZ() + dz * PUSH;
        player.setPosition(x, spot.y, z);
        player.setVelocity(0, player.getVelocity().y, 0);
        // The push itself is not a move of his: he is still "unmoved" next tick
        hider.steveparty$setLastPos(player.getPos());
    }

    /**
     * Works out whether the player is a block of the grid, resizing him when it changes. He becomes one standing
     * still on the centre of a valid spot with no one in the way; he stays one as long as he is hidden on that centre,
     * whoever stands on him or bumps into him (the sides that only watch him get his position and ground state late
     * or rounded: they must not let him flicker, or those standing on him fall through).
     */
    private static void updateAligned(PlayerEntity player) {
        if (!(player instanceof Hider hider)) return;
        Vec3d spot = BoxCostumeItem.isHiddenInBox(player) ? blockSpot(player) : null;
        boolean centred = spot != null && Math.abs(spot.x - player.getX()) < CENTRED && Math.abs(spot.z - player.getZ()) < CENTRED;
        boolean aligned;
        if (hider.steveparty$isBlockAligned()) {
            aligned = centred;
        } else {
            Vec3d last = hider.steveparty$getLastPos();
            aligned = centred && last != null && last.squaredDistanceTo(player.getPos()) < UNMOVED_SQUARED && nobodyInside(player, spot);
        }
        if (aligned == hider.steveparty$isBlockAligned()) return;
        hider.steveparty$setBlockAligned(aligned);
        player.calculateDimensions();
    }

    /** No mob, player or solid entity (boat, shulker, another hider-block) in the cube he would become. */
    private static boolean nobodyInside(PlayerEntity player, Vec3d spot) {
        return player.getWorld().getOtherEntities(player, cube(spot),
                entity -> !entity.isSpectator() && (entity instanceof LivingEntity || entity.isCollidable())).isEmpty();
    }

    private static Box cube(Vec3d spot) {
        return new Box(spot.x - 0.5, spot.y, spot.z - 0.5, spot.x + 0.5, spot.y + 1.0, spot.z + 0.5).contract(1.0E-3);
    }

    /**
     * @return where a hidden player can be a block, at his place: the centre of the block column he stands in, at the
     * height of his floor; null if he is not hidden, not standing on a floor (see {@link #floorTop}) or the cube
     * there would be in blocks or fluids
     */
    @Nullable
    public static Vec3d blockSpot(PlayerEntity player) {
        if (!BoxCostumeItem.isHiddenInBox(player)) return null;
        World world = player.getWorld();
        double y = player.getY();
        BlockPos support = BlockPos.ofFloored(player.getX(), y - CENTRED, player.getZ());
        double top = floorTop(world, support);
        if (Double.isNaN(top) || Math.abs(y - top) > CENTRED) return null;
        Vec3d spot = new Vec3d(support.getX() + 0.5, top, support.getZ() + 0.5);
        Box cube = cube(spot);
        if (world.getBlockCollisions(player, cube).iterator().hasNext()) return null;
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(cube.minX, cube.minY, cube.minZ), BlockPos.ofFloored(cube.maxX, cube.maxY, cube.maxZ))) {
            if (!world.getFluidState(pos).isEmpty()) return null;
        }
        return spot;
    }

    /**
     * @return the height of the top of the block at {@code pos} if its top is a whole flat square (any full block,
     * but also a dirt path, farmland, a slab, a carpet, soul sand...); NaN for no floor (air, stairs, a fence...)
     */
    public static double floorTop(World world, BlockPos pos) {
        VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
        if (shape.isEmpty()) return Double.NaN;
        double max = shape.getMax(Direction.Axis.Y);
        VoxelShape topLayer = VoxelShapes.cuboid(0, Math.max(0, max - 1.0E-3), 0, 1, max, 1);
        if (max > 1.0 || VoxelShapes.matchesAnywhere(topLayer, shape, BooleanBiFunction.ONLY_FIRST)) return Double.NaN;
        return pos.getY() + max;
    }
}
