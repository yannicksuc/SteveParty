package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TilePartBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Which board space a ray is on, the way the player sees it rather than by its hitbox: the Tile Linker Brush and the
 * aim preview use it (same answer on both sides).
 * <ol>
 *     <li>the block the ray meets (outline shapes, a lowered or sloped tile found from every angle: see
 *     {@link BoardSpaces#preferTile}), if it is a board space or a router (a part of a large tile stands for it);</li>
 *     <li>otherwise, the first board space whose <i>visual</i> box the ray crosses before that hit: the whole cell
 *     around (a tile's or a check point's thin shape is smaller than what is drawn), from its foot up to a little above
 *     its top. A ray grazing a flat tile, or hitting the ground right next to it, still finds it.</li>
 * </ol>
 */
public final class BrushAim {
    /** Board spaces can be aimed at this far away. */
    public static final double REACH = WrenchActions.LONG_REACH;
    /** What is drawn of a board space reaches at least this high in its cell... */
    private static final double VISUAL_HEIGHT = 0.5;
    /** ...and the ray may pass this far above its shape (or below its foot) and still be on it. */
    private static final double MARGIN = 0.2;
    /** How far past the hit the visual pass still looks (the cell of a tile right behind the hit ground). */
    private static final double PAST_HIT = 0.35;

    private BrushAim() {
    }

    /** The board space {@code entity} looks at (its eyes, its look), or null. */
    public static @Nullable BlockPos aimed(Entity entity, World world, float tickDelta) {
        return along(world, entity, entity.getCameraPosVec(tickDelta), entity.getRotationVec(tickDelta));
    }

    /** The board space {@code entity} would look at with this rotation, or null. */
    public static @Nullable BlockPos aimed(Entity entity, World world, float pitch, float yaw) {
        return along(world, entity, entity.getEyePos(), direction(pitch, yaw));
    }

    /** Unit vector of a look (same as an entity's rotation vector). */
    public static Vec3d direction(float pitch, float yaw) {
        float p = pitch * MathHelper.RADIANS_PER_DEGREE, y = -yaw * MathHelper.RADIANS_PER_DEGREE;
        float cosYaw = MathHelper.cos(y), sinYaw = MathHelper.sin(y), cosPitch = MathHelper.cos(p);
        return new Vec3d(sinYaw * cosPitch, -MathHelper.sin(p), cosYaw * cosPitch);
    }

    /** The board space on the ray from {@code eye} toward {@code direction}, or null. */
    public static @Nullable BlockPos along(World world, Entity entity, Vec3d eye, Vec3d direction) {
        Vec3d end = eye.add(direction.normalize().multiply(REACH));
        HitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));
        hit = BoardSpaces.preferTile(world, eye, end, hit);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = BoardSpaces.resolve(world, blockHit.getBlockPos());
            if (BoardLinks.container(world, pos) != null) return pos;
        }
        // The visual pass, up to (a little past) what the ray hit
        Vec3d until = hit.getType() == HitResult.Type.MISS ? end
                : hit.getPos().add(direction.normalize().multiply(PAST_HIT));
        return BlockView.raycast(eye, until, world, (view, cell) -> {
            // The cell itself, the one above (a lowered tile reaching down into its support's cell), the one below
            for (BlockPos candidate : new BlockPos[]{cell, cell.up(), cell.down()}) {
                Box visual = visualBox(view, candidate);
                if (visual != null && visual.raycast(eye, until).isPresent()) {
                    BlockPos pos = BoardSpaces.resolve(world, candidate);
                    if (BoardLinks.container(world, pos) != null) return pos.toImmutable();
                }
            }
            return null;
        }, view -> null);
    }

    /** The box a board space (or router, or large tile part) at {@code pos} is seen in, or null for anything else. */
    static @Nullable Box visualBox(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.hasBlockEntity() && !(state.getBlock() instanceof TilePartBlock)) return null;
        if (!(state.getBlock() instanceof TilePartBlock) && BoardLinks.container(world, pos) == null) return null;
        VoxelShape shape = state.getOutlineShape(world, pos);
        double bottom = shape.isEmpty() ? 0 : Math.min(0, shape.getMin(Direction.Axis.Y));
        double top = shape.isEmpty() ? VISUAL_HEIGHT : Math.max(shape.getMax(Direction.Axis.Y), VISUAL_HEIGHT);
        return new Box(pos.getX(), pos.getY() + bottom - MARGIN, pos.getZ(), pos.getX() + 1, pos.getY() + top + MARGIN, pos.getZ() + 1);
    }
}
