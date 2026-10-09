package fr.lordfinn.steveparty.blocks.custom;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The pile of coins in a Magpie Nest, one coin cube (5 x 2 x 5 pixels) per coin it holds, and where a Pie stands on
 * it. Pure layout, the same on the server and on every client.
 * <p>
 * The first {@link #HAND_PLACED} coins are the ones placed by hand in the nest model (Blockbench), in their order:
 * a straight stack in the middle of the nest, each turned its own way. The next ones go on the stack, each resting
 * on the one below with a small tilt and a little sideways wander, now and then one sliding off onto the rim around
 * it (a little heap at its foot, never on the corner where a Pie sleeps). Their turns, tilts and wanders are
 * pseudo-random but fixed by the nest's position and the coin's index: the same pile on every client, every frame.
 * At most {@link #MAX_DRAWN} coins are drawn (a fuller nest keeps that pile).
 * <p>
 * Coordinates in pixels of the nest model facing north (0 to 16); {@link #rotate} turns them to the nest's facing
 * (the block state's y rotation: east 90, south 180, west 270).
 */
public final class MagpieNestPile {
    /** The most coins drawn. */
    public static final int MAX_DRAWN = 64;
    /** The coins placed by hand in the model: centre x, y, z and turn (degrees, about y), from the bottom up. */
    private static final float[][] HAND = {
            {8, 2, 8, 0}, {8, 4, 8, -22.5F}, {8, 6, 8, -67.5F}, {8, 8, 8, -42}, {8, 10, 8, -72}};
    public static final int HAND_PLACED = HAND.length;
    /** A coin: 5 x 2 x 5 pixels. */
    public static final float COIN_WIDTH = 5, COIN_HEIGHT = 2;
    /** At most this many coins slide off onto the rim. */
    private static final int MAX_HEAP = 12;
    /** The rim's top, where a Pie stands on a corner (pixels). */
    public static final float RIM = 4;
    /** The free corner of the rim (no twig over it), facing north: south-east. */
    private static final float CORNER = 13;

    /** Values per coin in {@link #layout}: centre x, y, z (pixels), turn about y, tilt about x, tilt about z (degrees). */
    public static final int STRIDE = 6;

    private MagpieNestPile() {
    }

    /** How many coins are drawn for {@code coins} in the nest. */
    public static int drawn(int coins) {
        return MathHelper.clamp(coins, 0, MAX_DRAWN);
    }

    /**
     * The first {@code count} coins of the pile of the nest at {@code pos} (count at most {@link #MAX_DRAWN}), facing
     * north: {@link #STRIDE} floats per coin.
     */
    public static float[] layout(BlockPos pos, int count) {
        count = drawn(count);
        float[] out = new float[count * STRIDE];
        long seed = mix(pos.asLong() ^ 0x5DEECE66DL);
        float x = 8, z = 8, top = HAND[HAND_PLACED - 1][1] + COIN_HEIGHT / 2;
        int heap = 0;
        for (int i = 0; i < count; i++) {
            int o = i * STRIDE;
            if (i < HAND_PLACED) {
                out[o] = HAND[i][0];
                out[o + 1] = HAND[i][1];
                out[o + 2] = HAND[i][2];
                out[o + 3] = HAND[i][3];
                continue;
            }
            long h = mix(seed + i * 0x9E3779B97F4A7C15L);
            float yaw = unit(h, 0) * 90;
            if (heap < MAX_HEAP && unit(h, 1) < 0.22F) {
                // slid off onto the rim: around the stack, never over the free corner (south-east)
                // 0 degrees: east (x+), 90: south (z+); the south-east corner (10 to 80) stays free
                float angle = (float) Math.toRadians(80 + unit(h, 2) * 290);
                float radius = 4.3F + unit(h, 3) * 0.9F;
                int layer = heap / 6;
                out[o] = 8 + (float) Math.cos(angle) * radius;
                out[o + 2] = 8 + (float) Math.sin(angle) * radius;
                out[o + 1] = RIM + COIN_HEIGHT / 2 + 0.4F + layer * 1.6F + unit(h, 4) * 0.4F;
                // leaning outwards, on the stack's side
                float lean = 12 + unit(h, 5) * 14;
                out[o + 3] = yaw;
                out[o + 4] = (float) Math.sin(angle) * lean;
                out[o + 5] = -(float) Math.cos(angle) * lean;
                heap++;
                continue;
            }
            x = MathHelper.clamp(x + (unit(h, 2) - 0.5F) * 0.7F, 7, 9);
            z = MathHelper.clamp(z + (unit(h, 3) - 0.5F) * 0.7F, 7, 9);
            float tiltX = (unit(h, 4) - 0.5F) * 8, tiltZ = (unit(h, 5) - 0.5F) * 8;
            // a tilted coin stands a little higher on the one below
            float step = COIN_HEIGHT + (Math.abs(tiltX) + Math.abs(tiltZ)) * 0.012F;
            out[o] = x;
            out[o + 1] = top + step / 2;
            out[o + 2] = z;
            out[o + 3] = yaw;
            out[o + 4] = tiltX;
            out[o + 5] = tiltZ;
            top += step;
        }
        return out;
    }

    /** The block state's y rotation for a nest facing {@code facing} (north 0, east 90, south 180, west 270). */
    public static int yRotation(Direction facing) {
        return ((int) facing.asRotation() + 180) % 360;
    }

    /** {@code (x, z)} in pixels of the north-facing model, turned to {@code facing}: {x, z}. */
    public static float[] rotate(float x, float z, Direction facing) {
        return switch (facing) {
            case EAST -> new float[]{16 - z, x};
            case SOUTH -> new float[]{16 - x, 16 - z};
            case WEST -> new float[]{z, 16 - x};
            default -> new float[]{x, z};
        };
    }

    /**
     * Where a Pie stands on the nest at {@code pos} facing {@code facing}: in the middle of an empty nest (on the rim's
     * height, {@link MagpieNestBlock#HEIGHT}); with coins (the pile in the middle), on the free corner of the rim.
     */
    public static Vec3d perch(BlockPos pos, Direction facing, int coins) {
        if (coins <= 0) return new Vec3d(pos.getX() + 0.5, pos.getY() + MagpieNestBlock.HEIGHT / 16.0, pos.getZ() + 0.5);
        float[] at = rotate(CORNER, CORNER, facing);
        return new Vec3d(pos.getX() + at[0] / 16.0, pos.getY() + RIM / 16.0, pos.getZ() + at[1] / 16.0);
    }

    /**
     * Which way a Pie on that perch looks (yaw): in an empty nest the nest's facing; on the corner, out over it, along
     * the diagonal.
     */
    public static float perchYaw(Direction facing, int coins) {
        if (coins <= 0) return facing.asRotation();
        float[] corner = rotate(CORNER, CORNER, facing);
        double dx = corner[0] - 8, dz = corner[1] - 8;
        return (float) (MathHelper.atan2(dz, dx) * (180 / Math.PI)) - 90;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A number in [0, 1) from the {@code n}th 10 bits of {@code h}. */
    private static float unit(long h, int n) {
        return ((h >>> (n * 10)) & 0x3FF) / 1024F;
    }
}
