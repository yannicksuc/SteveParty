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
 * N sixteenths below its cell), or sheared along the slope of stairs ({@code slope_*}: 45 degrees, rising toward the
 * named side, so that the tiles of a staircase make one continuous ramp touching every step nose). The profile is
 * read from the support's outline shape, quarter by quarter, when the tile is placed and whenever the block under
 * it changes, and kept in the block state (see {@link ATileBlock#SUPPORT}).
 * <p>
 * Surface heights are relative to the floor of the tile's cell, in blocks: 0 is the top of a full block under it.
 */
public enum TileSupport implements StringIdentifiable {
    FLAT("flat", 0, 0, 0, 0),
    DROP_1("drop_1", 1, 0, 0, 0), DROP_2("drop_2", 2, 0, 0, 0), DROP_3("drop_3", 3, 0, 0, 0),
    DROP_4("drop_4", 4, 0, 0, 0), DROP_5("drop_5", 5, 0, 0, 0), DROP_6("drop_6", 6, 0, 0, 0),
    DROP_7("drop_7", 7, 0, 0, 0), DROP_8("drop_8", 8, 0, 0, 0), DROP_9("drop_9", 9, 0, 0, 0),
    DROP_10("drop_10", 10, 0, 0, 0), DROP_11("drop_11", 11, 0, 0, 0), DROP_12("drop_12", 12, 0, 0, 0),
    DROP_13("drop_13", 13, 0, 0, 0), DROP_14("drop_14", 14, 0, 0, 0), DROP_15("drop_15", 15, 0, 0, 0),
    /** Straight stairs: rises toward the side the stairs face (the side of their high step). */
    SLOPE_NORTH("slope_north", 0, 0, -1, 0),
    SLOPE_EAST("slope_east", 0, 1, 0, 0),
    SLOPE_SOUTH("slope_south", 0, 0, 1, 0),
    SLOPE_WEST("slope_west", 0, -1, 0, 0);

    /** Thickness of a tile (support + picture), in blocks. */
    public static final double THICKNESS = 2.0 / 16;
    private static final double EPSILON = 1.0E-3;

    private final String name;
    private final int drop;
    /** Height gained per block toward +x / +z (a 45 degree slope gains 1 per block along its uphill direction). */
    private final double gradientX, gradientZ;
    /** Height of the sloped surface at the centre of the cell. */
    private final double pivot;

    TileSupport(String name, int drop, double gradientX, double gradientZ, double pivot) {
        this.name = name;
        this.drop = drop;
        this.gradientX = gradientX;
        this.gradientZ = gradientZ;
        this.pivot = pivot;
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

    /** Height of the underside of the tile at ({@code x}, {@code z}), in cell coordinates (0..1). */
    public double surfaceY(double x, double z) {
        return pivot - drop / 16.0 + gradientX * (x - 0.5) + gradientZ * (z - 0.5);
    }

    /**
     * For a sloped support (the profile of bottom stairs): the top of the support under quarter {@code quarter}
     * (0 = north-west, 1 = north-east, 2 = south-west, 3 = south-east), 0 for its high part, -0.5 for its step.
     */
    public double supportTop(int quarter) {
        double x = 0.25 + 0.5 * (quarter & 1), z = 0.25 + 0.5 * (quarter >> 1);
        if (!isSloped()) return -drop / 16.0;
        return surfaceY(x, z) - pivot >= -EPSILON ? 0 : -0.5;
    }

    /** Height a token stands at on this tile, at the centre of the cell (tokens stay upright). */
    public double standY() {
        return surfaceY(0.5, 0.5) + THICKNESS;
    }

    /**
     * The transformation from a level tile drawn in its cell (cell coordinates, floor at y = 0) to this support:
     * lowered, or sheared vertically along the slope (the tile keeps its footprint and follows the steps).
     */
    public Matrix4f transform() {
        Matrix4f matrix = new Matrix4f();
        // y' = y + gx * (x - 0.5) + gz * (z - 0.5) + pivot - drop
        matrix.m01((float) gradientX);
        matrix.m21((float) gradientZ);
        matrix.m31((float) (pivot - drop / 16.0 - 0.5 * gradientX - 0.5 * gradientZ));
        return matrix;
    }

    // ---------------------------------------------------------------- shapes

    private static final Map<TileSupport, VoxelShape> SHAPES = new EnumMap<>(TileSupport.class);

    static {
        for (TileSupport support : values()) SHAPES.put(support, support.buildShape());
    }

    /** Outline and collision of a tile lying on this support (may reach down into the support's cell). */
    public VoxelShape shape() {
        return SHAPES.get(this);
    }

    private VoxelShape buildShape() {
        if (!isSloped()) {
            double bottom = -drop / 16.0;
            return VoxelShapes.cuboid(0, bottom, 0, 1, bottom + THICKNESS, 1);
        }
        // A staircase of thin slabs under the sloped surface: fine enough to walk up (steps of 1/8 block) and to aim at
        int steps = 8;
        VoxelShape shape = VoxelShapes.empty();
        for (int i = 0; i < steps; i++) {
            for (int j = 0; j < (gradientX != 0 && gradientZ != 0 ? steps : 1); j++) {
                double x0, x1, z0, z1;
                if (gradientX != 0 && gradientZ != 0) {
                    x0 = i / (double) steps; x1 = (i + 1) / (double) steps;
                    z0 = j / (double) steps; z1 = (j + 1) / (double) steps;
                } else if (gradientX != 0) {
                    x0 = i / (double) steps; x1 = (i + 1) / (double) steps; z0 = 0; z1 = 1;
                } else {
                    z0 = i / (double) steps; z1 = (i + 1) / (double) steps; x0 = 0; x1 = 1;
                }
                double top = Math.max(Math.max(surfaceY(x0, z0), surfaceY(x1, z1)), Math.max(surfaceY(x0, z1), surfaceY(x1, z0)))
                        + THICKNESS;
                shape = VoxelShapes.union(shape, VoxelShapes.cuboid(x0, top - THICKNESS, z0, x1, top, z1));
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
