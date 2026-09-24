package fr.lordfinn.steveparty.blocks.custom.signs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
     * Outline of a sign with a board, drawn where its model is ({@link AbstractStencilSignBlock#modelTransform}): on
     * or around its post, flat against a wall, on the floor or the ceiling; plus the post of the fence below a
     * standing sign. The board is cut in small pieces so that a turned board gets a snug outline. Built once for each
     * block state and board shift.
     */
    public static final class BoardOutline {
        private static final double PIECE = 4.0;
        private final Box[] board;
        private final Map<Long, VoxelShape> cache = new ConcurrentHashMap<>();

        public BoardOutline(Box... board) {
            this.board = board;
        }

        public VoxelShape get(AbstractStencilSignBlock sign, BlockView world, BlockPos pos, BlockState state) {
            long shift = Math.round(sign.boardShift(world, pos, state) * 64);
            long key = (long) Block.getRawIdFromState(state) << 32 | (shift & 0xFFFFFFFFL);
            return cache.computeIfAbsent(key, k -> build(sign.modelTransform(world, pos, state),
                    state.get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.POST));
        }

        private VoxelShape build(Matrix4f transform, boolean post) {
            VoxelShape shape = post ? VoxelShapes.cuboid(SignPosts.POST.x1 / 16, SignPosts.POST.y1 / 16, SignPosts.POST.z1 / 16,
                    SignPosts.POST.x2 / 16, SignPosts.POST.y2 / 16, SignPosts.POST.z2 / 16) : VoxelShapes.empty();
            Vector3f corner = new Vector3f();
            for (Box box : board) {
                int nx = Math.max(1, (int) Math.ceil((box.x2 - box.x1) / PIECE));
                int ny = Math.max(1, (int) Math.ceil((box.y2 - box.y1) / PIECE));
                for (int i = 0; i < nx; i++) {
                    for (int j = 0; j < ny; j++) {
                        double x1 = box.x1 + (box.x2 - box.x1) * i / nx, x2 = box.x1 + (box.x2 - box.x1) * (i + 1) / nx;
                        double y1 = box.y1 + (box.y2 - box.y1) * j / ny, y2 = box.y1 + (box.y2 - box.y1) * (j + 1) / ny;
                        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
                        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                        for (double x : new double[]{x1, x2}) {
                            for (double y : new double[]{y1, y2}) {
                                for (double z : new double[]{box.z1, box.z2}) {
                                    transform.transformPosition(corner.set(x / 16, y / 16, z / 16));
                                    minX = Math.min(minX, corner.x);
                                    minY = Math.min(minY, corner.y);
                                    minZ = Math.min(minZ, corner.z);
                                    maxX = Math.max(maxX, corner.x);
                                    maxY = Math.max(maxY, corner.y);
                                    maxZ = Math.max(maxZ, corner.z);
                                }
                            }
                        }
                        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(minX, minY, minZ, maxX, maxY, maxZ));
                    }
                }
            }
            return shape.simplify();
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
