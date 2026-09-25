package fr.lordfinn.steveparty.utils;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Where an entity sent "to a block" should land: standing on the top of that block, never inside it.
 */
public final class SafeLanding {
    /** How many blocks above the target are searched for a free space. */
    public static final int MAX_SEARCH_UP = 16;

    private SafeLanding() {}

    /**
     * Feet position (block centered) on the top of {@code target}'s collision shape. If the entity doesn't fit there
     * (something above), the nearest free space higher in the same column. An air target gives its bottom (so a
     * position saved on the block above the floor still lands on the floor).
     * <p>
     * If no free space is found within {@link #MAX_SEARCH_UP} blocks, the top of the target is returned anyway.
     */
    public static Vec3d findLandingPos(World world, Entity entity, BlockPos target) {
        double x = target.getX() + 0.5;
        double z = target.getZ() + 0.5;
        Vec3d first = null;
        for (int dy = 0; dy <= MAX_SEARCH_UP; dy++) {
            BlockPos pos = target.up(dy);
            if (world.isOutOfHeightLimit(pos)) break;
            double feetY = pos.getY() + topOf(world, entity, pos);
            Vec3d candidate = new Vec3d(x, feetY, z);
            if (first == null) first = candidate;
            Box box = entity.getDimensions(entity.getPose()).getBoxAt(candidate).contract(1.0E-7);
            if (world.isSpaceEmpty(entity, box)) return candidate;
        }
        return first != null ? first : new Vec3d(x, target.getY() + 1, z);
    }

    /** Height (0..1.5) of the top of the collision shape of the block at {@code pos}, 0 if it has none. */
    private static double topOf(World world, Entity entity, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.of(entity));
        return shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y);
    }
}
