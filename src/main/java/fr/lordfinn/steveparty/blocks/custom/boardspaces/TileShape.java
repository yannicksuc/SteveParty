package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * A tile the way it is drawn: its slab, two blocks wide around its block (a small tile a little more than its block, a
 * large one its 4 blocks), turned 45 degrees on an odd {@code rotation_8}, lowered or tilted onto its support. A box
 * in the tile's own frame and the transformation to its block's coordinates, so that it is aimed at and outlined
 * exactly, overhang included, without blocks around it (see {@link BoardSpaces#preferTile}).
 */
public final class TileShape {
    /** Half the width of the slab of a standard (and large) tile, and of a small one (18 px for 16). */
    private static final double HALF = 1, SMALL_HALF = 0.5 * 18 / 16;

    private final BlockPos pos;
    private final Box local;
    /** Tile frame to block coordinates (relative to {@link #pos}), and back. */
    private final Matrix4f toBlock, toLocal;

    private TileShape(BlockPos pos, Box local, Matrix4f toBlock) {
        this.pos = pos;
        this.local = local;
        this.toBlock = toBlock;
        this.toLocal = new Matrix4f(toBlock).invert();
    }

    /** The shape of the tile at {@code pos} (an {@link ATileBlock}), or null. */
    public static @Nullable TileShape of(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof ATileBlock ? of(state, pos) : null;
    }

    public static TileShape of(BlockState state, BlockPos pos) {
        TileLayout layout = state.get(ATileBlock.SIZE);
        TileSupport support = state.get(ATileBlock.SUPPORT);
        double cx = layout.centreX(), cz = layout.centreZ();
        double half = layout.size() == TileSize.SMALL ? SMALL_HALF : HALF;
        Matrix4f matrix = support.transform(cx, cz);
        if (state.get(ATileBlock.ROTATION_8) % 2 == 1) {
            // The 45 degree models: the same square turned about the middle of the tile
            matrix.translate((float) cx, 0, (float) cz).rotateY((float) Math.toRadians(45)).translate((float) -cx, 0, (float) -cz);
        }
        Box box = new Box(cx - half, 0, cz - half, cx + half, TileSupport.THICKNESS, cz + half);
        return new TileShape(pos.toImmutable(), box, matrix);
    }

    public BlockPos pos() {
        return pos;
    }

    /** The 8 corners of the slab, in world coordinates (bottom 4 then top 4, each in turn around the square). */
    public Vec3d[] corners() {
        double[][] xz = {{local.minX, local.minZ}, {local.maxX, local.minZ}, {local.maxX, local.maxZ}, {local.minX, local.maxZ}};
        Vec3d[] corners = new Vec3d[8];
        for (int i = 0; i < 8; i++) {
            double[] c = xz[i % 4];
            corners[i] = toWorld(c[0], i < 4 ? local.minY : local.maxY, c[1]);
        }
        return corners;
    }

    private Vec3d toWorld(double x, double y, double z) {
        Vector3f v = toBlock.transformPosition(new Vector3f((float) x, (float) y, (float) z));
        return new Vec3d(pos.getX() + v.x, pos.getY() + v.y, pos.getZ() + v.z);
    }

    private Vec3d toLocal(Vec3d world) {
        Vector3f v = toLocal.transformPosition(new Vector3f((float) (world.x - pos.getX()), (float) (world.y - pos.getY()),
                (float) (world.z - pos.getZ())));
        return new Vec3d(v.x, v.y, v.z);
    }

    /** Where the segment from {@code start} to {@code end} enters the slab (world coordinates), or null. */
    public @Nullable Hit raycast(Vec3d start, Vec3d end) {
        BlockHitResult hit = Box.raycast(List.of(local), toLocal(start), toLocal(end), BlockPos.ORIGIN);
        if (hit == null) return null;
        Vec3d at = toWorld(hit.getPos().x, hit.getPos().y, hit.getPos().z);
        Vector3f normal = toBlock.transformDirection(new Vector3f(hit.getSide().getUnitVector()));
        return new Hit(this, at, Direction.getFacing(normal.x, normal.y, normal.z));
    }

    /** A ray meeting a tile: where (world coordinates) and through which side (the nearest axis to its face's normal). */
    public record Hit(TileShape tile, Vec3d pos, Direction side) {
        /**
         * As a block hit: on the block of the tile under the point (a large tile's part there, else the tile's own), the
         * point kept within a block of that block's middle (what the server accepts from a click).
         */
        public BlockHitResult toBlockHit(BlockView world) {
            BlockPos tilePos = tile.pos;
            BlockPos cell = new BlockPos(MathHelper.floor(pos.x), tilePos.getY(), MathHelper.floor(pos.z));
            BlockPos target = cell.equals(tilePos) || TilePartBlock.isPartOf(world.getBlockState(cell), cell, tilePos) ? cell : tilePos;
            Vec3d middle = Vec3d.ofCenter(target);
            Vec3d kept = new Vec3d(MathHelper.clamp(pos.x, middle.x - 0.999, middle.x + 0.999),
                    MathHelper.clamp(pos.y, middle.y - 0.999, middle.y + 0.999),
                    MathHelper.clamp(pos.z, middle.z - 0.999, middle.z + 0.999));
            return new BlockHitResult(kept, side, target, false);
        }
    }
}
