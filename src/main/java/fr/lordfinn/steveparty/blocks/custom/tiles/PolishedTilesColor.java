package fr.lordfinn.steveparty.blocks.custom.tiles;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;

import java.util.List;

/**
 * A colour of the polished tiles, in the order of {@link ModBlocks#COLORS_WITH_DEFAULT}: the 16 dyes, and
 * {@link #DEFAULT} for the plain terracotta (which the concrete doesn't have).
 */
public enum PolishedTilesColor implements StringIdentifiable {
    DEFAULT, WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, GRAY, LIGHT_GRAY, CYAN, PURPLE, BLUE, BROWN, GREEN, RED, BLACK;

    public static final List<PolishedTilesColor> ALL = List.of(values());
    /** The 16 dyes, in the order of {@link ModBlocks#COLORS}. */
    public static final List<PolishedTilesColor> DYES = ALL.subList(1, ALL.size());

    /** Its name in the ids, as in {@link ModBlocks#COLORS_WITH_DEFAULT}. */
    @Override
    public String asString() {
        return ModBlocks.COLORS_WITH_DEFAULT[ordinal()];
    }

    /** Its name in the item's name (« white », « blanc »). */
    public Text text() {
        return Text.translatable("color.steveparty.polished_tiles." + asString());
    }
}
