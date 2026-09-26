package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Where tokens stand on board spaces, and which board space a token stands on.
 * <p>
 * A board space is identified by its block position, but tokens stand on its real surface, which is not always the
 * floor of its cell: a tile lowered onto a bottom slab has its surface in the cell below ({@link TileSupport}).
 */
public final class BoardSpaces {
    private BoardSpaces() {
    }

    /** The position of the board space a token standing at {@code feet} (its block position) is on, or null. */
    public static @Nullable BlockPos boardSpacePosAt(World world, BlockPos feet) {
        if (world.getBlockEntity(feet) instanceof BoardSpaceBlockEntity) return feet;
        // Standing on a lowered tile: the feet are in the support's cell, the tile is the cell above
        BlockPos up = feet.up();
        BlockState above = world.getBlockState(up);
        if (above.getBlock() instanceof ATileBlock && above.get(ATileBlock.SUPPORT).standY() < 0
                && world.getBlockEntity(up) instanceof BoardSpaceBlockEntity) {
            return up;
        }
        return null;
    }

    /** The board space {@code entity} stands on, or null. */
    public static @Nullable BoardSpaceBlockEntity boardSpaceOf(Entity entity) {
        BlockPos pos = boardSpacePosAt(entity.getWorld(), entity.getBlockPos());
        if (pos == null) return null;
        BlockEntity blockEntity = entity.getWorld().getBlockEntity(pos);
        return blockEntity instanceof BoardSpaceBlockEntity boardSpace ? boardSpace : null;
    }

    /** Where a token stands on the board space at {@code pos}: the middle of its surface. */
    public static Vec3d standPos(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ATileBlock) {
            return new Vec3d(pos.getX() + 0.5, pos.getY() + state.get(ATileBlock.SUPPORT).standY(), pos.getZ() + 0.5);
        }
        VoxelShape shape = state.getCollisionShape(world, pos);
        double height = shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y);
        return new Vec3d(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
    }
}
