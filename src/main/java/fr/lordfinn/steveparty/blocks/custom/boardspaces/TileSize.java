package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import com.mojang.serialization.Codec;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringIdentifiable;

/**
 * The three sizes of a tile (both the Tile and the Advanced Tile). The item carries it in its {@code steveparty:tile-size}
 * component (one item per tile, whatever its size); the placed tile in its {@code size} block state
 * ({@link TileLayout}, which also tells toward which side a large tile spreads).
 */
public enum TileSize implements StringIdentifiable {
    /** The original tile: two blocks wide, centred on its block. */
    STANDARD("standard"),
    /** Its picture covers exactly its block, the border overhangs a little. */
    SMALL("small"),
    /** Covers exactly 2x2 blocks of the grid: the tile's block and 3 {@link TilePartBlock}s. */
    LARGE("large");

    public static final Codec<TileSize> CODEC = StringIdentifiable.createCodec(TileSize::values);

    /**
     * Scale of the model of a small tile: its 32 px base shrunk to 18 px, overhanging its block by 1 px on each side
     * (its face is drawn apart, 16x16 over exactly its block, at the block's pixel density).
     */
    public static final float SMALL_SCALE = 18f / 32f;

    private final String name;

    TileSize(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return name;
    }

    public TileSize next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** The size a tile item places. */
    public static TileSize of(ItemStack stack) {
        return stack.getOrDefault(ModComponents.TILE_SIZE, STANDARD);
    }

    /** Makes {@code stack} place tiles of {@code size} (the standard size carries no component). */
    public static ItemStack with(ItemStack stack, TileSize size) {
        if (size == STANDARD) stack.remove(ModComponents.TILE_SIZE);
        else stack.set(ModComponents.TILE_SIZE, size);
        return stack;
    }
}
