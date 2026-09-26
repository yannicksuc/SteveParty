package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.BlockView;
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
        // In the middle of a large tile: on one of its parts
        BlockPos master = TilePartBlock.resolve(world, feet);
        if (master != null) return master;
        // Standing on a lowered tile: the feet are in the support's cell, the tile is the cell above
        BlockPos up = feet.up();
        BlockState above = world.getBlockState(up);
        if (above.getBlock() instanceof ATileBlock && above.get(ATileBlock.SUPPORT).standY() < 0
                && world.getBlockEntity(up) instanceof BoardSpaceBlockEntity) {
            return up;
        }
        BlockPos masterAbove = TilePartBlock.resolve(world, up);
        if (masterAbove != null && world.getBlockState(masterAbove).get(ATileBlock.SUPPORT).standY() < 0) return masterAbove;
        return null;
    }

    /**
     * A lowered or sloped tile reaches down into the cell of its support, where the vanilla ray (which visits the cells
     * in order and asks each one only about its own block) doesn't look for it: seen from the side or from low, it
     * would hit the slab or the stairs, or go through the tile. Walks the cells the ray crossed before its hit and
     * returns the tile (or large tile part) above one of them if the ray meets its shape first.
     */
    public static HitResult preferTile(BlockView world, Vec3d start, Vec3d end, HitResult hit) {
        Vec3d until = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        double limit = start.squaredDistanceTo(until) + 1.0E-6;
        BlockHitResult tile = BlockView.raycast(start, until, world, (view, cell) -> {
            BlockPos above = cell.up();
            BlockState state = view.getBlockState(above);
            if (!(state.getBlock() instanceof ATileBlock) && !(state.getBlock() instanceof TilePartBlock)) return null;
            VoxelShape shape = state.getOutlineShape(view, above);
            if (shape.isEmpty() || shape.getMin(Direction.Axis.Y) >= 0) return null;
            BlockHitResult found = shape.raycast(start, end, above);
            return found != null && start.squaredDistanceTo(found.getPos()) <= limit ? found : null;
        }, view -> null);
        return tile != null ? tile : hit;
    }

    /** The block standing for the board space at {@code pos}: the large tile a part belongs to, else {@code pos}. */
    public static BlockPos resolve(World world, BlockPos pos) {
        BlockPos master = TilePartBlock.resolve(world, pos);
        return master != null ? master : pos;
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
            // A large tile's middle is the corner shared by its 4 blocks
            double centre = state.get(ATileBlock.SIZE) == TileSize.LARGE ? 1 : 0.5;
            return new Vec3d(pos.getX() + centre, pos.getY() + state.get(ATileBlock.SUPPORT).standY(), pos.getZ() + centre);
        }
        VoxelShape shape = state.getCollisionShape(world, pos);
        double height = shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y);
        return new Vec3d(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
    }
}
