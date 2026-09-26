package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * How a tile lies on the block grid, kept in its {@code size} block state: its {@link TileSize}, and for a large (2x2)
 * tile the side toward which it spreads from its own block (its anchor, the block it was placed on: the highest one on
 * a slope). The 3 other blocks of a large tile are {@link TilePartBlock}s.
 */
public enum TileLayout implements StringIdentifiable {
    STANDARD("standard", TileSize.STANDARD, 0, 0),
    SMALL("small", TileSize.SMALL, 0, 0),
    LARGE_SOUTH_EAST("large_south_east", TileSize.LARGE, 1, 1),
    LARGE_SOUTH_WEST("large_south_west", TileSize.LARGE, -1, 1),
    LARGE_NORTH_EAST("large_north_east", TileSize.LARGE, 1, -1),
    LARGE_NORTH_WEST("large_north_west", TileSize.LARGE, -1, -1);

    private final String name;
    private final TileSize size;
    private final int dx, dz;

    TileLayout(String name, TileSize size, int dx, int dz) {
        this.name = name;
        this.size = size;
        this.dx = dx;
        this.dz = dz;
    }

    @Override
    public String asString() {
        return name;
    }

    public TileSize size() {
        return size;
    }

    public boolean isLarge() {
        return size == TileSize.LARGE;
    }

    /** Spreading side of a large tile along x (-1 west, 1 east; 0 for a single block tile). */
    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    public static TileLayout of(TileSize size) {
        return switch (size) {
            case STANDARD -> STANDARD;
            case SMALL -> SMALL;
            case LARGE -> LARGE_SOUTH_EAST;
        };
    }

    public static TileLayout large(int dx, int dz) {
        if (dx >= 0) return dz >= 0 ? LARGE_SOUTH_EAST : LARGE_NORTH_EAST;
        return dz >= 0 ? LARGE_SOUTH_WEST : LARGE_NORTH_WEST;
    }

    /** The middle of the tile, in its own block's coordinates (a large tile's: the corner shared by its 4 blocks). */
    public double centreX() {
        return 0.5 + 0.5 * dx;
    }

    public double centreZ() {
        return 0.5 + 0.5 * dz;
    }

    /** The blocks the tile covers, as offsets {dx, dz} from its own block (its own first). */
    public List<int[]> cells() {
        if (!isLarge()) return List.<int[]>of(new int[]{0, 0});
        return List.of(new int[]{0, 0}, new int[]{dx, 0}, new int[]{0, dz}, new int[]{dx, dz});
    }

    /** The other blocks of a large tile, from its own block at {@code anchor}. */
    public List<BlockPos> parts(BlockPos anchor) {
        if (!isLarge()) return List.of();
        return List.of(anchor.add(dx, 0, 0), anchor.add(0, 0, dz), anchor.add(dx, 0, dz));
    }

    /**
     * The layout of a large tile placed at {@code anchor} on {@code support}, aimed at {@code hit}: on a slope it spreads
     * downhill (its anchor is its highest block), across the slope and on level ground toward the aimed side.
     */
    public static TileLayout largeFor(TileSupport support, BlockPos anchor, Vec3d hit) {
        int aimX = hit.x - anchor.getX() < 0.5 ? -1 : 1, aimZ = hit.z - anchor.getZ() < 0.5 ? -1 : 1;
        int dx = support.gradientX() != 0 ? (support.gradientX() > 0 ? -1 : 1) : aimX;
        int dz = support.gradientZ() != 0 ? (support.gradientZ() > 0 ? -1 : 1) : aimZ;
        return large(dx, dz);
    }

    /** Whether a large tile spreading this way goes down {@code support}'s slope (from its anchor, the highest block). */
    public boolean goesDown(TileSupport support) {
        if (support.gradientX() != 0 && Math.signum(support.gradientX()) != -dx) return false;
        return support.gradientZ() == 0 || Math.signum(support.gradientZ()) == -dz;
    }
}
