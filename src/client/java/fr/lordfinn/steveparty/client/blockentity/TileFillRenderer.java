package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TilePartBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The base under a sloped tile: fills the hollows between the tilted tile and the steps under it, so that it looks
 * like a solid wedge resting on the stairs. Drawn in the tile's cell coordinates (floor of its cell at y = 0):
 * <ul>
 *     <li>on each outer edge of the tile's blocks, a wall from the ground (the top of whatever is under it, found in
 *     the world, quarter of block by quarter) up to the slope;</li>
 *     <li>a cap on the slope itself, just under the tile, closing the wedge where the tilted tile doesn't reach.</li>
 * </ul>
 * Textured with the tile's own fill texture at the block pixel density (16 px per block, aligned on the block grid:
 * walls by their position and height, the cap by its position on the slope), never stretched.
 */
final class TileFillRenderer {
    private static final float CAP_OFFSET = 0.004f;

    private TileFillRenderer() {
    }

    /**
     * @param cells offsets (dx, dz) of the blocks the tile covers, from its own block
     */
    static void render(World world, BlockPos pos, TileSupport support, List<int[]> cells, Sprite sprite, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getCutout());
        MatrixStack.Entry entry = matrices.peek();
        for (int[] cell : cells) {
            int cx = cell[0], cz = cell[1];
            // Edges of this block not shared with another block of the tile: {x0, z0, x1, z1, outward nx, nz}
            double[][] edges = {{0, 0, 1, 0, 0, -1}, {1, 0, 1, 1, 1, 0}, {1, 1, 0, 1, 0, 1}, {0, 1, 0, 0, -1, 0}};
            for (double[] edge : edges) {
                int nx = (int) edge[4], nz = (int) edge[5];
                if (covers(cells, cx + nx, cz + nz)) continue;
                for (int half = 0; half < 2; half++) {
                    double ax = cx + MathHelper.lerp(half * 0.5, edge[0], edge[2]), az = cz + MathHelper.lerp(half * 0.5, edge[1], edge[3]);
                    double bx = cx + MathHelper.lerp(half * 0.5 + 0.5, edge[0], edge[2]), bz = cz + MathHelper.lerp(half * 0.5 + 0.5, edge[1], edge[3]);
                    // The ground of the quarter along this half edge, sampled just inside the block
                    double mx = (ax + bx) / 2 - nx * 0.05, mz = (az + bz) / 2 - nz * 0.05;
                    double topA = support.surfaceY(ax, az), topB = support.surfaceY(bx, bz);
                    double ground = ground(world, pos, mx, mz, support.surfaceY(mx, mz));
                    if (topA - ground < 1.0E-3 && topB - ground < 1.0E-3) continue;
                    wall(consumer, entry, sprite, light, ax, az, bx, bz, ground, Math.max(ground, topA), Math.max(ground, topB), nx, nz);
                }
            }
            cap(consumer, entry, sprite, light, support, cx, cz);
        }
    }

    private static boolean covers(List<int[]> cells, int x, int z) {
        for (int[] cell : cells) if (cell[0] == x && cell[1] == z) return true;
        return false;
    }

    /**
     * Top of the ground under the point ({@code x}, {@code z}) (tile cell coordinates) below {@code ceiling}: the
     * highest part of the blocks there (tiles excepted), searched 3 blocks down; the ceiling if nothing is found.
     */
    private static double ground(World world, BlockPos pos, double x, double z, double ceiling) {
        int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
        double fx = x - bx, fz = z - bz;
        int top = MathHelper.floor(ceiling + 1.0E-3);
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int by = top; by >= top - 3; by--) {
            cursor.set(pos.getX() + bx, pos.getY() + by, pos.getZ() + bz);
            BlockState state = world.getBlockState(cursor);
            if (state.isAir() || state.getBlock() instanceof ATileBlock || state.getBlock() instanceof TilePartBlock) continue;
            double best = Double.NEGATIVE_INFINITY;
            for (Box box : state.getOutlineShape(world, cursor, ShapeContext.absent()).getBoundingBoxes()) {
                if (box.minX <= fx && fx <= box.maxX && box.minZ <= fz && fz <= box.maxZ && by + box.maxY <= ceiling + 1.0E-3) {
                    best = Math.max(best, by + box.maxY);
                }
            }
            if (best != Double.NEGATIVE_INFINITY) return best;
        }
        return ceiling;
    }

    /** A vertical wall between two points of an edge, from the ground up to the slope, cut at each block boundary. */
    private static void wall(VertexConsumer consumer, MatrixStack.Entry entry, Sprite sprite, int light,
                             double ax, double az, double bx, double bz, double ground, double topA, double topB, int nx, int nz) {
        // Position along the edge, in the block: u of the texture
        boolean alongX = az == bz;
        double ua = alongX ? ax - Math.floor(Math.min(ax, bx)) : az - Math.floor(Math.min(az, bz));
        double ub = alongX ? bx - Math.floor(Math.min(ax, bx)) : bz - Math.floor(Math.min(az, bz));
        int from = MathHelper.floor(ground), to = MathHelper.floor(Math.max(topA, topB) - 1.0E-4);
        for (int band = from; band <= to; band++) {
            // The polygon of the wall inside the band [band, band + 1]: bottom edge, then the slope, clipped
            List<double[]> polygon = new ArrayList<>(); // {t along the edge 0..1, y}
            polygon.add(new double[]{0, ground});
            polygon.add(new double[]{1, ground});
            polygon.add(new double[]{1, topB});
            polygon.add(new double[]{0, topA});
            polygon = clip(polygon, band, true);
            polygon = clip(polygon, band + 1, false);
            if (polygon.size() < 3) continue;
            float[][] vertices = new float[polygon.size()][];
            for (int i = 0; i < polygon.size(); i++) {
                double t = polygon.get(i)[0], y = polygon.get(i)[1];
                double u = MathHelper.lerp(t, ua, ub), v = band + 1 - y;
                vertices[i] = new float[]{(float) MathHelper.lerp(t, ax, bx), (float) y, (float) MathHelper.lerp(t, az, bz),
                        sprite.getFrameU((float) u), sprite.getFrameV((float) v)};
            }
            polygon(consumer, entry, light, vertices, nx, 0, nz, true);
        }
    }

    /** Keeps the part of a polygon {t, y} above ({@code above}) or below the height {@code limit}. */
    private static List<double[]> clip(List<double[]> polygon, double limit, boolean above) {
        List<double[]> result = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            boolean inA = above ? a[1] >= limit : a[1] <= limit, inB = above ? b[1] >= limit : b[1] <= limit;
            if (inA) result.add(a);
            if (inA != inB) {
                double k = (limit - a[1]) / (b[1] - a[1]);
                result.add(new double[]{a[0] + k * (b[0] - a[0]), limit});
            }
        }
        return result;
    }

    /**
     * The slope over a block, just under the tile, in 4x4 pieces textured by their position on the slope (so its
     * pixels keep their size on the tilted surface).
     */
    private static void cap(VertexConsumer consumer, MatrixStack.Entry entry, Sprite sprite, int light, TileSupport support, int cx, int cz) {
        double gx = support.gradientX(), gz = support.gradientZ();
        double length = Math.sqrt(gx * gx + gz * gz);
        double ux = gx / length, uz = gz / length;
        // Along the slope, a horizontal step of 1 is sqrt(1 + slope^2) on the surface
        double stretch = Math.sqrt(1 + length * length);
        float nyLength = (float) Math.sqrt(1 + length * length);
        float nx = (float) (-gx / nyLength), ny = 1 / nyLength, nz = (float) (-gz / nyLength);
        int pieces = 4;
        for (int i = 0; i < pieces; i++) {
            for (int j = 0; j < pieces; j++) {
                double x0 = cx + i / (double) pieces, x1 = cx + (i + 1) / (double) pieces;
                double z0 = cz + j / (double) pieces, z1 = cz + (j + 1) / (double) pieces;
                double[][] corners = {{x0, z0}, {x0, z1}, {x1, z1}, {x1, z0}};
                double[] us = new double[4], vs = new double[4];
                double minU = Double.MAX_VALUE, minV = Double.MAX_VALUE, maxU = -Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
                for (int k = 0; k < 4; k++) {
                    double x = corners[k][0], z = corners[k][1];
                    us[k] = x * -uz + z * ux;              // across the slope
                    vs[k] = -(x * ux + z * uz) * stretch;  // down the slope, as long as on the surface
                    minU = Math.min(minU, us[k]); maxU = Math.max(maxU, us[k]);
                    minV = Math.min(minV, vs[k]); maxV = Math.max(maxV, vs[k]);
                }
                // Back into the texture (one block of pixels): keep each piece whole in it
                double shiftU = Math.floor(minU), shiftV = Math.floor(minV);
                if (maxU - shiftU > 1) shiftU = maxU - 1;
                if (maxV - shiftV > 1) shiftV = maxV - 1;
                float[][] vertices = new float[4][];
                for (int k = 0; k < 4; k++) {
                    double x = corners[k][0], z = corners[k][1];
                    vertices[k] = new float[]{(float) x, (float) (support.surfaceY(x, z) - CAP_OFFSET), (float) z,
                            sprite.getFrameU((float) (us[k] - shiftU)), sprite.getFrameV((float) (vs[k] - shiftV))};
                }
                polygon(consumer, entry, light, vertices, nx, ny, nz, false);
            }
        }
    }

    /** A convex polygon (x, y, z, u, v per vertex), as quads (a fan; triangles repeat their last vertex). */
    private static void polygon(VertexConsumer consumer, MatrixStack.Entry entry, int light, float[][] vertices,
                                float nx, float ny, float nz, boolean bothSides) {
        for (int side = 0; side < (bothSides ? 2 : 1); side++) {
            float sign = side == 0 ? 1 : -1;
            for (int first = 1; first < vertices.length - 1; first += 2) {
                int[] order = {0, first, first + 1, Math.min(first + 2, vertices.length - 1)};
                for (int k = 0; k < 4; k++) {
                    // Counter-clockwise as seen from the front (the order of the vanilla up faces), reversed for the back
                    float[] v = vertices[order[side == 0 ? k : 3 - k]];
                    consumer.vertex(entry.getPositionMatrix(), v[0], v[1], v[2]).color(255, 255, 255, 255).texture(v[3], v[4])
                            .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx * sign, ny * sign, nz * sign);
                }
            }
        }
    }
}
