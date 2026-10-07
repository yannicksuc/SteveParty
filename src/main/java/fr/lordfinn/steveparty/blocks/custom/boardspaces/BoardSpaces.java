package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
        BlockPos here = boardSpaceOrTile(world, feet);
        if (here != null) return here;
        // Standing on a lowered or sloped tile: the feet are in the cell under the tile (or under a part of it)
        BlockPos above = boardSpaceOrTile(world, feet.up());
        if (above != null && standPos(world, above).y < feet.getY() + 1 + 1.0E-6) return above;
        return null;
    }

    /** The board space at {@code pos}, or the large tile of the part there. */
    private static @Nullable BlockPos boardSpaceOrTile(World world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity) return pos;
        BlockPos master = TilePartBlock.resolve(world, pos);
        return master != null && world.getBlockEntity(master) instanceof BoardSpaceBlockEntity ? master : null;
    }

    /**
     * A tile is aimed at where it is drawn ({@link TileShape}): its slab overhangs its block (two blocks wide, turned 45
     * degrees, lowered or tilted onto its support), where the vanilla ray, which asks each cell it crosses only about
     * its own block, doesn't look for it. Walks the cells the ray crossed up to its hit and tries the tiles around
     * each one (and above and below: lowered and sloped tiles); the nearest slab met first wins.
     */
    public static HitResult preferTile(BlockView world, Vec3d start, Vec3d end, HitResult hit) {
        Vec3d until = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        // The cell outline of a tile stands a little above its slab: the slab just behind that hit is still this hit
        boolean onTile = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && isTileBlock(world.getBlockState(blockHit.getBlockPos()));
        double limit = start.distanceTo(until) + (onTile ? 0.25 : 1.0E-6);
        LongOpenHashSet tried = new LongOpenHashSet();
        TileShape.Hit[] best = {null};
        double[] bestDistance = {limit * limit};
        BlockPos.Mutable around = new BlockPos.Mutable();
        BlockView.raycast(start, until, world, (view, cell) -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        around.set(cell, dx, dy, dz);
                        if (!tried.add(around.asLong())) continue;
                        BlockState state = view.getBlockState(around);
                        if (!(state.getBlock() instanceof ATileBlock)) continue;
                        TileShape.Hit found = TileShape.of(state, around).raycast(start, end);
                        if (found == null) continue;
                        double distance = start.squaredDistanceTo(found.pos());
                        if (distance < bestDistance[0]) {
                            bestDistance[0] = distance;
                            best[0] = found;
                        }
                    }
                }
            }
            return null;
        }, view -> null);
        return best[0] != null ? best[0].toBlockHit(world) : hit;
    }

    private static boolean isTileBlock(BlockState state) {
        return state.getBlock() instanceof ATileBlock || state.getBlock() instanceof TilePartBlock;
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
            TileLayout layout = state.get(ATileBlock.SIZE);
            double x = layout.centreX(), z = layout.centreZ();
            return new Vec3d(pos.getX() + x, pos.getY() + state.get(ATileBlock.SUPPORT).standY(x, z), pos.getZ() + z);
        }
        VoxelShape shape = state.getCollisionShape(world, pos);
        double height = shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y);
        return new Vec3d(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
    }
}
