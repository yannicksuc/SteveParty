package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

/**
 * What a tile lies on: the « support profile » of the block under it.
 * <p>
 * A tile always occupies the cell above its support (two blocks can't share a cell), but it is drawn, collides and is
 * outlined on the real surface of the support: lowered onto a bottom slab, snow layers or a carpet ({@code drop_N}:
 * N sixteenths below its cell), or tilted 45 degrees along the slope of stairs ({@code slope_*}: rising toward the
 * named side, through the step nose; along the diagonal on inner ({@code slope_*_*}) and outer ({@code outer_*})
 * corner stairs). A tilted tile keeps its square face (it is turned, not stretched). The profile is
 * read from the support's outline shape, quarter by quarter, when the tile is placed and whenever the block under
 * it changes, and kept in the block state (see {@link ATileBlock#SUPPORT}).
 * <p>
 * Surface heights are relative to the floor of the tile's cell, in blocks: 0 is the top of a full block under it.
 */
public enum TileSupport implements StringIdentifiable {
    FLAT("flat", 0, 0, 0, 0, 0),
    DROP_1("drop_1", 1, 0, 0, 0, 0), DROP_2("drop_2", 2, 0, 0, 0, 0), DROP_3("drop_3", 3, 0, 0, 0, 0),
    DROP_4("drop_4", 4, 0, 0, 0, 0), DROP_5("drop_5", 5, 0, 0, 0, 0), DROP_6("drop_6", 6, 0, 0, 0, 0),
    DROP_7("drop_7", 7, 0, 0, 0, 0), DROP_8("drop_8", 8, 0, 0, 0, 0), DROP_9("drop_9", 9, 0, 0, 0, 0),
    DROP_10("drop_10", 10, 0, 0, 0, 0), DROP_11("drop_11", 11, 0, 0, 0, 0), DROP_12("drop_12", 12, 0, 0, 0, 0),
    DROP_13("drop_13", 13, 0, 0, 0, 0), DROP_14("drop_14", 14, 0, 0, 0, 0), DROP_15("drop_15", 15, 0, 0, 0, 0),
    /** Straight stairs: rises toward the side the stairs face (the side of their high step). */
    SLOPE_NORTH("slope_north", 0, 0, -1, 0, 0b0011),
    SLOPE_EAST("slope_east", 0, 1, 0, 0, 0b1010),
    SLOPE_SOUTH("slope_south", 0, 0, 1, 0, 0b1100),
    SLOPE_WEST("slope_west", 0, -1, 0, 0, 0b0101),
    /**
     * Inner corner stairs (three high quarters): rises 45 degrees along the diagonal toward the corner opposite their
     * low quarter, resting on the inner edges of the high part (the nose of the L where it meets the low step).
     */
    SLOPE_NORTH_EAST("slope_north_east", 0, Inner.GRADIENT, -Inner.GRADIENT, Inner.PIVOT, 0b1011),
    SLOPE_SOUTH_EAST("slope_south_east", 0, Inner.GRADIENT, Inner.GRADIENT, Inner.PIVOT, 0b1110),
    SLOPE_SOUTH_WEST("slope_south_west", 0, -Inner.GRADIENT, Inner.GRADIENT, Inner.PIVOT, 0b1101),
    SLOPE_NORTH_WEST("slope_north_west", 0, -Inner.GRADIENT, -Inner.GRADIENT, Inner.PIVOT, 0b0111),
    /**
     * Outer corner stairs (one high quarter): rises along the diagonal toward that quarter, resting on its nose (the
     * middle of the block) and on the far corner of the low step (35 degrees).
     */
    OUTER_NORTH_EAST("outer_north_east", 0, Outer.GRADIENT, -Outer.GRADIENT, Outer.PIVOT, 0b0010),
    OUTER_SOUTH_EAST("outer_south_east", 0, Outer.GRADIENT, Outer.GRADIENT, Outer.PIVOT, 0b1000),
    OUTER_SOUTH_WEST("outer_south_west", 0, -Outer.GRADIENT, Outer.GRADIENT, Outer.PIVOT, 0b0100),
    OUTER_NORTH_WEST("outer_north_west", 0, -Outer.GRADIENT, -Outer.GRADIENT, Outer.PIVOT, 0b0001);

    /** 45 degrees along the diagonal (0.707 per block on each axis), on the inner edges of the L (0.354 from the middle). */
    private static final class Inner {
        static final double GRADIENT = Math.sqrt(0.5);
        static final double PIVOT = Math.sqrt(0.5) / 2;
    }

    /** Half a block per block on each axis: from the nose (middle, height 0) to the far low corner (-0.5). */
    private static final class Outer {
        static final double GRADIENT = 0.5;
        static final double PIVOT = 0;
    }

    /** Thickness of a tile (support + picture), in blocks. */
    public static final double THICKNESS = 2.0 / 16;
    private static final double EPSILON = 1.0E-3;

    private final String name;
    private final int drop;
    /** Height gained per block toward +x / +z (a 45 degree slope gains 1 per block along its uphill direction). */
    private final double gradientX, gradientZ;
    /** Height of the sloped surface at the centre of the cell. */
    private final double pivot;
    /** The high quarters of the stairs under a slope (bit 0 = north-west, 1 = north-east, 2 = south-west, 3 = south-east). */
    private final int highQuarters;

    TileSupport(String name, int drop, double gradientX, double gradientZ, double pivot, int highQuarters) {
        this.name = name;
        this.drop = drop;
        this.gradientX = gradientX;
        this.gradientZ = gradientZ;
        this.pivot = pivot;
        this.highQuarters = highQuarters;
    }

    @Override
    public String asString() {
        return name;
    }

    public boolean isFlat() {
        return this == FLAT;
    }

    public boolean isSloped() {
        return gradientX != 0 || gradientZ != 0;
    }

    /** Sixteenths the (level) surface lies below the cell floor. */
    public int drop() {
        return drop;
    }

    public double gradientX() {
        return gradientX;
    }

    public double gradientZ() {
        return gradientZ;
    }

    /** The tilt of the slope, in radians (45 degrees on straight stairs, 35 on corners; 0 when level). */
    public float angle() {
        return (float) Math.atan(Math.sqrt(gradientX * gradientX + gradientZ * gradientZ));
    }

    /**
     * Height of the underside of the tile at ({@code x}, {@code z}), in the coordinates of the tile's cell (0..1 over
     * it; the plane goes on beyond, over the other blocks of a large tile).
     */
    public double surfaceY(double x, double z) {
        return pivot - drop / 16.0 + gradientX * (x - 0.5) + gradientZ * (z - 0.5);
    }

    /**
     * For a sloped support (the profile of bottom stairs): the top of the support under quarter {@code quarter}
     * (0 = north-west, 1 = north-east, 2 = south-west, 3 = south-east), 0 for its high part, -0.5 for its step.
     */
    public double supportTop(int quarter) {
        if (!isSloped()) return -drop / 16.0;
        return ((highQuarters >> quarter) & 1) != 0 ? 0 : -0.5;
    }

    /** Height a token stands at on this tile, at ({@code x}, {@code z}) (tokens stay upright on the slope). */
    public double standY(double x, double z) {
        // On the tile's face: its thickness is across the tilted tile, taller seen straight up
        return surfaceY(x, z) + THICKNESS * Math.sqrt(1 + gradientX * gradientX + gradientZ * gradientZ);
    }

    public double standY() {
        return standY(0.5, 0.5);
    }

    /**
     * The transformation from a level tile drawn in its cell (cell coordinates, floor at y = 0) to this support:
     * lowered, or tilted about the point ({@code x}, {@code z}) of the tile (its centre), so that it lies on the slope
     * with its square face kept square.
     */
    public Matrix4f transform(double x, double z) {
        Matrix4f matrix = new Matrix4f();
        if (!isSloped()) return matrix.translation(0, (float) (-drop / 16.0), 0);
        double length = Math.sqrt(gradientX * gradientX + gradientZ * gradientZ);
        // Uphill (ux, uz): turning about (-uz, 0, ux) lifts it
        float axisX = (float) (-gradientZ / length), axisZ = (float) (gradientX / length);
        return matrix.translation((float) x, (float) surfaceY(x, z), (float) z)
                .rotate(angle(), axisX, 0, axisZ)
                .translate((float) -x, 0, (float) -z);
    }

    public Matrix4f transform() {
        return transform(0.5, 0.5);
    }

    // ---------------------------------------------------------------- shapes

    private static final Map<TileSupport, VoxelShape[]> SHAPES = new EnumMap<>(TileSupport.class);
    private static final Map<TileSupport, VoxelShape[]> OUTLINES = new EnumMap<>(TileSupport.class);
    /** The outline stands a little above the tile's face, so that its lines are drawn over it (not hidden in it). */
    private static final double OUTLINE_LIFT = 1.0 / 32;

    static {
        for (TileSupport support : values()) {
            VoxelShape[] shapes = new VoxelShape[9], outlines = new VoxelShape[9];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    shapes[(dx + 1) * 3 + dz + 1] = support.buildCollision(dx, dz);
                    outlines[(dx + 1) * 3 + dz + 1] = support.buildOutline(dx, dz);
                }
            }
            SHAPES.put(support, shapes);
            OUTLINES.put(support, outlines);
        }
    }

    /** What is outlined and aimed at: the collision {@link #shape(int, int)}, its top a little higher. */
    public VoxelShape outline(int dx, int dz) {
        return OUTLINES.get(this)[(dx + 1) * 3 + dz + 1];
    }

    public VoxelShape outline() {
        return outline(0, 0);
    }

    /** Collision of a tile lying on this support (may reach down into the support's cell). */
    public VoxelShape shape() {
        return shape(0, 0);
    }

    /**
     * What a token collides with on a sloped tile: a level platform at the height it stands at (tokens stay upright,
     * in the middle of the tile, where the board puts them), {@code standY} above the floor of the block.
     */
    public static VoxelShape tokenPlatform(double standY) {
        return VoxelShapes.cuboid(0, Math.max(standY - 1.0 / 16, -1), 0, 1, Math.max(standY, -1 + 1.0 / 32), 1);
    }

    /**
     * Outline and collision of the part of the tile's surface over the block at ({@code dx}, {@code dz}) from the
     * tile's cell (the other blocks of a large tile), in that block's coordinates; never more than a block below it.
     */
    public VoxelShape shape(int dx, int dz) {
        return SHAPES.get(this)[(dx + 1) * 3 + dz + 1];
    }

    /** Aimed at and outlined: the tile's volume (its face lies on the slope, its thickness above it). */
    private VoxelShape buildOutline(int dx, int dz) {
        if (!isSloped()) {
            double bottom = -drop / 16.0;
            return VoxelShapes.cuboid(0, bottom, 0, 1, bottom + THICKNESS + OUTLINE_LIFT, 1);
        }
        return slices(dx, dz, 8, true);
    }

    /**
     * Walked on: level tiles are their slab; a sloped tile is the slope itself in steps of a quarter of a block,
     * each at the height of its lower edge, so that it is climbed and walked down as smoothly as bare stairs: its lower
     * edge is on the lower step (half a block up from the step below, like the stairs), no step higher than that.
     */
    private VoxelShape buildCollision(int dx, int dz) {
        if (!isSloped()) {
            double bottom = -drop / 16.0;
            return VoxelShapes.cuboid(0, bottom, 0, 1, bottom + THICKNESS, 1);
        }
        // Steps a quarter of a block long: longer than a walking stride per tick, so the step-up never slows the walk
        return slices(dx, dz, 4, false);
    }

    private VoxelShape slices(int dx, int dz, int steps, boolean outline) {
        boolean diagonal = gradientX != 0 && gradientZ != 0;
        double lift = outline ? OUTLINE_LIFT : 0;
        VoxelShape shape = VoxelShapes.empty();
        for (int i = 0; i < steps; i++) {
            for (int j = 0; j < (diagonal ? steps : 1); j++) {
                double x0, x1, z0, z1;
                if (diagonal) {
                    x0 = i / (double) steps; x1 = (i + 1) / (double) steps;
                    z0 = j / (double) steps; z1 = (j + 1) / (double) steps;
                } else if (gradientX != 0) {
                    x0 = i / (double) steps; x1 = (i + 1) / (double) steps; z0 = 0; z1 = 1;
                } else {
                    z0 = i / (double) steps; z1 = (i + 1) / (double) steps; x0 = 0; x1 = 1;
                }
                double high = Math.max(Math.max(surfaceY(x0 + dx, z0 + dz), surfaceY(x1 + dx, z1 + dz)),
                        Math.max(surfaceY(x0 + dx, z1 + dz), surfaceY(x1 + dx, z0 + dz)));
                double low = Math.min(Math.min(surfaceY(x0 + dx, z0 + dz), surfaceY(x1 + dx, z1 + dz)),
                        Math.min(surfaceY(x0 + dx, z1 + dz), surfaceY(x1 + dx, z0 + dz)));
                double top = outline ? high + THICKNESS * Math.sqrt(2) + lift : low;
                double bottom = Math.max(Math.min(low, top - 1.0 / 16), -1);
                // Deeper than the block below (the far end of a large tile going down a slope): the stairs there carry it
                if (!outline && top <= -1 + 1.0 / 32) continue;
                if (top <= bottom) top = bottom + 1.0 / 32;
                shape = VoxelShapes.union(shape, VoxelShapes.cuboid(x0, bottom, z0, x1, top, z1));
            }
        }
        return shape.simplify();
    }

    // ---------------------------------------------------------------- detection

    /** Reads the support profile of the block under {@code tilePos}. */
    public static TileSupport compute(BlockView world, BlockPos tilePos) {
        BlockPos below = tilePos.down();
        BlockState state = world.getBlockState(below);
        if (state.isAir()) return FLAT;
        return fromShape(state.getOutlineShape(world, below, ShapeContext.absent()));
    }

    /**
     * Profile of a support whose outline is {@code shape} (support cell coordinates): the top of each quarter of the
     * cell decides. Level tops lower the tile; the quarters of bottom stairs (two high, two half a block lower) tilt it.
     * Anything else (holes, parts sticking into the tile's cell, odd shapes) keeps it level on the highest part.
     */
    public static TileSupport fromShape(VoxelShape shape) {
        if (shape.isEmpty()) return FLAT;
        double[] tops = new double[4]; // quarters: 0 = north-west, 1 = north-east, 2 = south-west, 3 = south-east
        double highest = Double.NEGATIVE_INFINITY;
        for (int q = 0; q < 4; q++) {
            tops[q] = quarterTop(shape, 0.25 + 0.5 * (q & 1), 0.25 + 0.5 * (q >> 1));
            highest = Math.max(highest, tops[q]);
        }
        if (highest == Double.NEGATIVE_INFINITY || highest > 1 + EPSILON) return FLAT;
        boolean[] high = new boolean[4];
        int highCount = 0;
        for (int q = 0; q < 4; q++) {
            high[q] = Math.abs(tops[q] - highest) < EPSILON;
            if (high[q]) highCount++;
        }
        if (highCount < 4 && highest > 1 - EPSILON) {
            TileSupport slope = slope(tops, high, highCount, highest);
            if (slope != null) return slope;
        }
        return level(highest);
    }

    private static TileSupport level(double top) {
        int drop = (int) Math.round((1 - top) * 16);
        if (drop <= 0) return FLAT;
        return values()[Math.min(drop, 15)];
    }

    private static @Nullable TileSupport slope(double[] tops, boolean[] high, int highCount, double highest) {
        // The low quarters must be exactly half a block lower (the step of bottom stairs): the slope touches them
        for (int q = 0; q < 4; q++) {
            if (!high[q] && Math.abs(tops[q] - (highest - 0.5)) > EPSILON) return null;
        }
        if (highCount == 2) {
            if (high[0] && high[1]) return SLOPE_NORTH;
            if (high[2] && high[3]) return SLOPE_SOUTH;
            if (high[0] && high[2]) return SLOPE_WEST;
            if (high[1] && high[3]) return SLOPE_EAST;
        }
        if (highCount == 3) {
            // Inner corner: rises toward the quarter opposite the low one
            if (!high[3]) return SLOPE_NORTH_WEST;
            if (!high[2]) return SLOPE_NORTH_EAST;
            if (!high[1]) return SLOPE_SOUTH_WEST;
            return SLOPE_SOUTH_EAST;
        }
        if (highCount == 1) {
            // Outer corner: rises toward its high quarter
            if (high[0]) return OUTER_NORTH_WEST;
            if (high[1]) return OUTER_NORTH_EAST;
            if (high[2]) return OUTER_SOUTH_WEST;
            return OUTER_SOUTH_EAST;
        }
        return null;
    }

    /** Top of the parts of {@code shape} standing over the point ({@code x}, {@code z}), or -infinity if none. */
    private static double quarterTop(VoxelShape shape, double x, double z) {
        double top = Double.NEGATIVE_INFINITY;
        for (Box box : shape.getBoundingBoxes()) {
            if (box.minX <= x && x <= box.maxX && box.minZ <= z && z <= box.maxZ) top = Math.max(top, box.maxY);
        }
        return top;
    }
}
