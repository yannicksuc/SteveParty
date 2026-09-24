package fr.lordfinn.steveparty.blocks.custom.signs;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

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
     * @param standing outline of the sign for each rotation, without the post it stands on
     * @return the same outlines moved onto the post behind: where a sign hung on the side of a post is drawn
     */
    public static VoxelShape[] hung(VoxelShape[] standing) {
        VoxelShape[] shapes = new VoxelShape[16];
        for (int rotation = 0; rotation < 16; rotation++) {
            Direction facing = AbstractStencilSignBlock.facing(rotation);
            shapes[rotation] = facing == null ? standing[rotation]
                    : standing[rotation].offset(-facing.getOffsetX(), 0, -facing.getOffsetZ());
        }
        return shapes;
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
