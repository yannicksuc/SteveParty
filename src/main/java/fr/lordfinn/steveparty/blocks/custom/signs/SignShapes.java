package fr.lordfinn.steveparty.blocks.custom.signs;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outline shapes of the 16-way signs. Their models are drawn in "model space" (pixels, front facing north)
 * and turned by {@link #angleDegrees(int)} around the block's vertical axis, exactly like the client does.
 * Long pieces are cut in short segments before being turned, so that a diagonal board gets a snug outline
 * instead of one big box.
 */
public final class SignShapes {
    private SignShapes() {
    }

    /** Turn applied to the model of a sign with this rotation (same as the traffic sign always used). */
    public static float angleDegrees(int rotation) {
        return rotation * -22.5F + 180.0F;
    }

    /** A box in model pixels. */
    public record Box(double x1, double y1, double z1, double x2, double y2, double z2) {
    }

    /** @return the outline of these model boxes for each of the 16 rotations. */
    public static VoxelShape[] rotations(Box... boxes) {
        return rotations(boxes, new Box[0]);
    }

    /**
     * @param turning boxes turning with the sign
     * @param fixed   boxes that never turn (the post of the fence it stands on), in block pixels
     * @return the outline for each of the 16 rotations
     */
    public static VoxelShape[] rotations(Box[] turning, Box[] fixed) {
        VoxelShape still = VoxelShapes.empty();
        for (Box box : fixed) {
            still = VoxelShapes.union(still, VoxelShapes.cuboid(box.x1 / 16, box.y1 / 16, box.z1 / 16, box.x2 / 16, box.y2 / 16, box.z2 / 16));
        }
        Box[] boxes = turning;
        VoxelShape[] shapes = new VoxelShape[16];
        for (int rotation = 0; rotation < 16; rotation++) {
            VoxelShape shape = still;
            double angle = Math.toRadians(angleDegrees(rotation));
            for (Box box : boxes) {
                for (Box segment : split(box)) shape = VoxelShapes.union(shape, turned(segment, angle));
            }
            shapes[rotation] = shape.simplify();
        }
        return shapes;
    }

    /**
     * Outline of a board on a post: the board turned with the sign and moved back against its post (see
     * {@link AbstractStencilSignBlock#boardShift}), plus the post of the fence below a standing sign; a hung sign's
     * board is around the post behind it. Built once for each rotation / side / shift.
     */
    public static final class BoardOutline {
        private final Box[] board;
        private final Map<Long, VoxelShape> cache = new ConcurrentHashMap<>();

        public BoardOutline(Box... board) {
            this.board = board;
        }

        public VoxelShape get(BlockState state, double shift) {
            int rotation = state.get(AbstractStencilSignBlock.ROTATION);
            Direction hung = AbstractStencilSignBlock.hungFacing(state);
            long key = rotation | (hung == null ? 0L : hung.ordinal() + 1L) << 4 | Math.round(shift * 64) << 8;
            return cache.computeIfAbsent(key, k -> build(rotation, hung, shift));
        }

        private VoxelShape build(int rotation, Direction hung, double shift) {
            Box[] moved = new Box[board.length];
            for (int i = 0; i < board.length; i++) {
                Box box = board[i];
                moved[i] = new Box(box.x1, box.y1, box.z1 + shift, box.x2, box.y2, box.z2 + shift);
            }
            if (hung == null) return rotations(moved, new Box[]{SignPosts.POST})[rotation];
            return rotations(moved)[rotation].offset(-hung.getOffsetX(), 0, -hung.getOffsetZ());
        }
    }

    private static final double SEGMENT = 4.0;

    private static Box[] split(Box box) {
        double sx = box.x2 - box.x1, sz = box.z2 - box.z1;
        boolean alongX = sx >= sz;
        double length = alongX ? sx : sz;
        int count = Math.max(1, (int) Math.ceil(length / SEGMENT));
        Box[] parts = new Box[count];
        for (int i = 0; i < count; i++) {
            double a = i * length / count, b = (i + 1) * length / count;
            parts[i] = alongX
                    ? new Box(box.x1 + a, box.y1, box.z1, box.x1 + b, box.y2, box.z2)
                    : new Box(box.x1, box.y1, box.z1 + a, box.x2, box.y2, box.z1 + b);
        }
        return parts;
    }

    /** Bounding box, in block units, of a model box turned by {@code angle} around the block centre. */
    private static VoxelShape turned(Box box, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double x : new double[]{box.x1, box.x2}) {
            for (double z : new double[]{box.z1, box.z2}) {
                double cx = x - 8, cz = z - 8;
                // Same turn as RotationAxis.POSITIVE_Y on the client
                double rx = cx * cos + cz * sin + 8;
                double rz = -cx * sin + cz * cos + 8;
                minX = Math.min(minX, rx);
                maxX = Math.max(maxX, rx);
                minZ = Math.min(minZ, rz);
                maxZ = Math.max(maxZ, rz);
            }
        }
        return VoxelShapes.cuboid(minX / 16, box.y1 / 16, minZ / 16, maxX / 16, box.y2 / 16, maxZ / 16);
    }
}
