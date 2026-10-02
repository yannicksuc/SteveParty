package fr.lordfinn.steveparty.minigame;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The faces of a zone's box: which one a player looks at, and moving one. Shared by the client (the face it
 * highlights) and the server (the face a scroll really moves), so that both agree.
 */
public final class ZoneFaces {
    /** How far a face can be looked at from. */
    public static final double REACH = 256;

    private ZoneFaces() {
    }

    /**
     * The face of {@code box} a ray meets: from outside, the face it enters by; from inside, the face it leaves by.
     *
     * @param eye  where the ray starts
     * @param look its direction
     * @return the face, null when the ray misses the box or meets it farther than {@link #REACH}
     */
    public static @Nullable Direction lookedAt(Vec3d eye, Vec3d look, Box box) {
        double[] origin = {eye.x, eye.y, eye.z}, direction = {look.x, look.y, look.z};
        double[] min = {box.minX, box.minY, box.minZ}, max = {box.maxX, box.maxY, box.maxZ};
        double enter = Double.NEGATIVE_INFINITY, leave = Double.POSITIVE_INFINITY;
        Direction enterFace = null, leaveFace = null;
        for (int axis = 0; axis < 3; axis++) {
            Direction low = axis == 0 ? Direction.WEST : axis == 1 ? Direction.DOWN : Direction.NORTH;
            Direction high = low.getOpposite();
            if (Math.abs(direction[axis]) < 1.0e-9) {
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) return null;
                continue;
            }
            double toLow = (min[axis] - origin[axis]) / direction[axis], toHigh = (max[axis] - origin[axis]) / direction[axis];
            boolean lowFirst = toLow < toHigh;
            double near = lowFirst ? toLow : toHigh, far = lowFirst ? toHigh : toLow;
            if (near > enter) {
                enter = near;
                enterFace = lowFirst ? low : high;
            }
            if (far < leave) {
                leave = far;
                leaveFace = lowFirst ? high : low;
            }
        }
        if (enter > leave || leave < 0) return null;
        boolean inside = enter < 0;
        double distance = inside ? leave : enter;
        return distance > REACH ? null : inside ? leaveFace : enterFace;
    }

    /**
     * The box with one face moved.
     *
     * @param amount  blocks the face moves by: outward when positive, inward when negative
     * @param bottomY the lowest block of the world
     * @param topY    its highest block
     * @return the new box: at least one block thick, within the world, and not grown past {@link PageZone#MAX_SIDE}
     * (a box already longer can only shrink); null when the face can't move that way at all
     */
    public static @Nullable BlockBox moved(BlockBox box, Direction face, int amount, int bottomY, int topY) {
        int size = switch (face.getAxis()) {
            case X -> box.getBlockCountX();
            case Y -> box.getBlockCountY();
            case Z -> box.getBlockCountZ();
        };
        int moved = amount > 0 ? Math.min(amount, Math.max(0, PageZone.MAX_SIDE - size)) : Math.max(amount, 1 - size);
        if (face == Direction.UP) moved = Math.min(moved, topY - box.getMaxY());
        if (face == Direction.DOWN) moved = Math.min(moved, box.getMinY() - bottomY);
        if (moved == 0) return null;
        int minX = box.getMinX(), minY = box.getMinY(), minZ = box.getMinZ(), maxX = box.getMaxX(), maxY = box.getMaxY(), maxZ = box.getMaxZ();
        switch (face) {
            case WEST -> minX -= moved;
            case EAST -> maxX += moved;
            case DOWN -> minY -= moved;
            case UP -> maxY += moved;
            case NORTH -> minZ -= moved;
            case SOUTH -> maxZ += moved;
        }
        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
