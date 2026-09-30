package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;

/**
 * The colour of the tile (the cartridge's {@link ModComponents#COLOR}), like a dye used on the tile: the 16 dyes and
 * the cartridge's own colour. Values: 0..15 a dye (its {@link DyeColor#getId()}), {@link #DEFAULT} its own colour.
 */
public final class ColorModule extends CartridgeModule {
    public static final int DEFAULT = 16;
    public static final int PER_ROW = 9;
    public static final int SWATCH = 13, GAP = 2;

    private final int defaultColor;

    /** @param defaultColor the cartridge's own colour (the tile's when not dyed) */
    public ColorModule(String id, String labelKey, int defaultColor) {
        super(id, labelKey);
        this.defaultColor = defaultColor & 0xFFFFFF;
    }

    public int defaultColor() {
        return defaultColor;
    }

    /** The colour of swatch {@code value}. */
    public int colorOf(int value) {
        return value == DEFAULT ? defaultColor : DyeColor.byId(value).getEntityColor() & 0xFFFFFF;
    }

    @Override
    public int height() {
        return labelHeight() + 2 * SWATCH + GAP;
    }

    @Override
    public boolean editable() {
        return true;
    }

    /** The swatch of the cartridge's colour; -1 when it is none of them (e.g. a token's colour on a start tile). */
    @Override
    public int get(ItemStack stack) {
        Integer color = stack.get(ModComponents.COLOR);
        int rgb = color == null ? defaultColor : color & 0xFFFFFF;
        if (rgb == defaultColor) return DEFAULT;
        for (DyeColor dye : DyeColor.values()) {
            if ((dye.getEntityColor() & 0xFFFFFF) == rgb) return dye.getId();
        }
        return -1;
    }

    @Override
    public boolean accepts(ItemStack stack, int value) {
        return value >= 0 && value <= DEFAULT;
    }

    /** On its board space (active slot): like a dye on the tile, sent to the players around; else on the cartridge. */
    @Override
    public void set(CartridgeEdit edit, int value) {
        // Stored like a dye used on the tile does (its entity colour, as is)
        int color = value == DEFAULT ? defaultColor : DyeColor.byId(value).getEntityColor();
        BoardSpaceBlockEntity boardSpace = edit.boardSpace();
        if (boardSpace != null && edit.isActiveOnBoardSpace()) {
            ABoardSpaceBehavior.setColor(boardSpace, color);
        } else {
            edit.stack().set(ModComponents.COLOR, color);
        }
    }
}
