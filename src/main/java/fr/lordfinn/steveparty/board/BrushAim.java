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

import java.util.Set;
import java.util.function.Predicate;

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
 * The brush also aims at its <b>ghosts</b>: the cells its dangling links lead to (no board space there, see
 * {@link BoardLinks#dangling}), seen as a tile slab in that cell. And it finds the <b>blobs</b> of a stroke: the look
 * lingering on one spot of a surface where there is no board space (see {@link Blob}).
 */
public final class BrushAim {
    /** No other holder nor target: only board spaces, routers and ghosts are aimed at (the Wrench, the helmet). */
    private static final Predicate<BlockPos> BOARD_ONLY = pos -> false;
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

    /** A blob: the look stays this close (blocks) to where it first met the surface... */
    public static final double BLOB_RADIUS = 0.5;
    /** ...for this many ticks of the stroke. */
    public static final int BLOB_TICKS = 12;

    /** The board space {@code entity} looks at (its eyes, its look), or null. */
    public static @Nullable BlockPos aimed(Entity entity, World world, float tickDelta) {
        return aimed(entity, world, tickDelta, Set.of());
    }

    /** The board space or ghost {@code entity} looks at (its eyes, its look), or null. */
    public static @Nullable BlockPos aimed(Entity entity, World world, float tickDelta, Set<BlockPos> ghosts) {
        return along(world, entity, entity.getCameraPosVec(tickDelta), entity.getRotationVec(tickDelta), ghosts);
    }

    /** The board space {@code entity} would look at with this rotation, or null. */
    public static @Nullable BlockPos aimed(Entity entity, World world, float pitch, float yaw) {
        return aimed(entity, world, pitch, yaw, Set.of());
    }

    /** The board space or ghost {@code entity} would look at with this rotation, or null. */
    public static @Nullable BlockPos aimed(Entity entity, World world, float pitch, float yaw, Set<BlockPos> ghosts) {
        return along(world, entity, entity.getEyePos(), direction(pitch, yaw), ghosts);
    }

    /** The holder, ghost or target ({@code targets}: see {@link BrushLinks#aims}) {@code entity} would look at with this rotation, or null. */
    public static @Nullable BlockPos aimed(Entity entity, World world, float pitch, float yaw, Set<BlockPos> ghosts, Predicate<BlockPos> targets) {
        return along(world, entity, entity.getEyePos(), direction(pitch, yaw), ghosts, targets);
    }

    /** The holder, ghost or target {@code entity} looks at (its eyes, its look), or null. */
    public static @Nullable BlockPos aimed(Entity entity, World world, float tickDelta, Set<BlockPos> ghosts, Predicate<BlockPos> targets) {
        return along(world, entity, entity.getCameraPosVec(tickDelta), entity.getRotationVec(tickDelta), ghosts, targets);
    }

    /** The ghosts of the brush held by {@code entity} at {@code level}: its dangling links within reach, see {@link BoardLinks#dangling}. */
    public static java.util.Map<BlockPos, java.util.List<BlockPos>> ghosts(Entity entity, World world, int level) {
        return BoardLinks.dangling(world, entity.getEyePos(), REACH, level);
    }

    /** Unit vector of a look (same as an entity's rotation vector). */
    public static Vec3d direction(float pitch, float yaw) {
        float p = pitch * MathHelper.RADIANS_PER_DEGREE, y = -yaw * MathHelper.RADIANS_PER_DEGREE;
        float cosYaw = MathHelper.cos(y), sinYaw = MathHelper.sin(y), cosPitch = MathHelper.cos(p);
        return new Vec3d(sinYaw * cosPitch, -MathHelper.sin(p), cosYaw * cosPitch);
    }

    /** The board space on the ray from {@code eye} toward {@code direction}, or null. */
    public static @Nullable BlockPos along(World world, Entity entity, Vec3d eye, Vec3d direction) {
        return along(world, entity, eye, direction, Set.of());
    }

    /** The board space or ghost on the ray from {@code eye} toward {@code direction}, or null. */
    public static @Nullable BlockPos along(World world, Entity entity, Vec3d eye, Vec3d direction, Set<BlockPos> ghosts) {
        return along(world, entity, eye, direction, ghosts, BOARD_ONLY);
    }

    /**
     * The board space, ghost or other holder of a cartridge (see {@link BrushLinks}) on the ray, or the block it hits
     * if {@code targets} takes it (what the last holder of the stroke links: a chest, a stall...), or null.
     */
    public static @Nullable BlockPos along(World world, Entity entity, Vec3d eye, Vec3d direction, Set<BlockPos> ghosts,
                                           Predicate<BlockPos> targets) {
        Vec3d end = eye.add(direction.normalize().multiply(REACH));
        HitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));
        hit = BoardSpaces.preferTile(world, eye, end, hit);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = BoardSpaces.resolve(world, blockHit.getBlockPos());
            if (BoardLinks.container(world, pos) != null) return pos;
            // The brush's stroke: any holder of a cartridge, and what the last one links
            if (targets != BOARD_ONLY && (BrushLinks.isHolder(world, pos) || targets.test(pos))) return pos;
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
                if (!ghosts.isEmpty() && ghosts.contains(candidate) && ghostBox(candidate).raycast(eye, until).isPresent()) {
                    return candidate.toImmutable();
                }
            }
            return null;
        }, view -> null);
    }

    /** The box a ghost is seen in: a tile's, in its cell. */
    static Box ghostBox(BlockPos pos) {
        return new Box(pos.getX(), pos.getY() - MARGIN, pos.getZ(), pos.getX() + 1, pos.getY() + VISUAL_HEIGHT + MARGIN, pos.getZ() + 1);
    }

    // ---------------------------------------------------------------- blobs

    /** The surface {@code entity} looks at, up to {@link #REACH} blocks away, or null. */
    public static @Nullable BlockHitResult surface(Entity entity, World world) {
        Vec3d eye = entity.getEyePos();
        HitResult hit = world.raycast(new RaycastContext(eye, eye.add(entity.getRotationVector().multiply(REACH)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    /**
     * The cell a blob on {@code surface} links to, where a tile could stand: the cell above the painted block if a
     * tile could be placed there (air or replaceable), else the one above that; a replaceable block painted (tall
     * grass...) is that cell itself.
     */
    public static BlockPos blobCell(World world, BlockPos surface) {
        if (world.getBlockState(surface).isReplaceable()) return surface.toImmutable();
        BlockPos above = surface.up();
        return world.getBlockState(above).isReplaceable() ? above : above.up();
    }

    /**
     * Follows the look of a stroke, tick after tick, to find its blobs (« un pâté »): the look staying within
     * {@link #BLOB_RADIUS} of the spot where it met a surface without board space for {@link #BLOB_TICKS} ticks. A blob
     * is made once; the look has to leave the spot to make another.
     */
    public static final class Blob {
        private @Nullable Vec3d spot;
        private int ticks;
        private boolean made;

        /**
         * One tick of the stroke.
         *
         * @param at where the look meets a surface, null if it aims at a board space, a ghost or nothing
         * @return true the tick a blob is made
         */
        public boolean tick(@Nullable Vec3d at) {
            if (at == null) {
                spot = null;
                return false;
            }
            if (spot == null || spot.squaredDistanceTo(at) > BLOB_RADIUS * BLOB_RADIUS) {
                spot = at;
                ticks = 1;
                made = false;
                return false;
            }
            if (made || ++ticks < BLOB_TICKS) return false;
            made = true;
            return true;
        }
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
