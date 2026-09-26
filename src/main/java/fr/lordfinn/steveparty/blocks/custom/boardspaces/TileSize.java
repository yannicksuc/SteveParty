package fr.lordfinn.steveparty.blocks.custom.boardspaces;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringIdentifiable;

/**
 * The three sizes of a tile (both the Tile and the Advanced Tile), kept in the {@code size} block state and carried by
 * the item through the vanilla {@code minecraft:block_state} component (so one item per tile, whatever its size).
 * A tile alone in a crafting grid turns into the next size ({@link fr.lordfinn.steveparty.recipes.TileSizeRecipe}).
 */
public enum TileSize implements StringIdentifiable {
    /** The original tile: two blocks wide, centred on its block. */
    STANDARD("standard"),
    /** Its picture covers exactly its block, the border overhangs a little. */
    SMALL("small"),
    /** Covers exactly 2x2 blocks of the grid: the tile's block is the north-west one, the 3 others are {@link TilePartBlock}s. */
    LARGE("large");

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
        BlockStateComponent component = stack.get(DataComponentTypes.BLOCK_STATE);
        if (component == null) return STANDARD;
        TileSize size = component.getValue(ATileBlock.SIZE);
        return size == null ? STANDARD : size;
    }

    /** Makes {@code stack} place tiles of {@code size} (the standard size carries no component). */
    public static ItemStack with(ItemStack stack, TileSize size) {
        if (size == STANDARD) {
            BlockStateComponent component = stack.get(DataComponentTypes.BLOCK_STATE);
            if (component != null) {
                java.util.Map<String, String> properties = new java.util.HashMap<>(component.properties());
                properties.remove(ATileBlock.SIZE.getName());
                if (properties.isEmpty()) stack.remove(DataComponentTypes.BLOCK_STATE);
                else stack.set(DataComponentTypes.BLOCK_STATE, new BlockStateComponent(properties));
            }
            return stack;
        }
        BlockStateComponent component = stack.getOrDefault(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT);
        stack.set(DataComponentTypes.BLOCK_STATE, component.with(ATileBlock.SIZE, size));
        return stack;
    }
}
