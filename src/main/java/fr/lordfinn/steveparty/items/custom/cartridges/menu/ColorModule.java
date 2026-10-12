package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileDye;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * The colour of the tile (the cartridge's {@link ModComponents#COLOR}), like a dye used on the tile: the 16 dyes and
 * the base colour (no dye: the cartridge's own colour, else its host's). Values: 0..15 a dye (its
 * {@link DyeColor#getId()}), {@link #DEFAULT} the base colour.
 */
public final class ColorModule extends CartridgeModule {
    public static final int DEFAULT = 16;
    public static final int PER_ROW = 9;
    public static final int SWATCH = 13, GAP = 2;

    private final int defaultColor;

    /** @param defaultColor the base swatch's colour in a hand (no host), when the cartridge has no colour of its own */
    public ColorModule(String id, String labelKey, int defaultColor) {
        super(id, labelKey);
        this.defaultColor = defaultColor & 0xFFFFFF;
    }

    public int defaultColor() {
        return defaultColor;
    }

    /** The colour of swatch {@code value} (the base swatch: the neutral colour, see {@link #baseColor}). */
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

    /**
     * The colour of the base swatch ({@link #DEFAULT}) for {@code stack} held by {@code holder} (null: in a hand): the
     * cartridge's own colour if it has one ({@link TileDye#baseColor}), else its host's base colour (a tile's white, a
     * check point's blue), else the neutral {@link #defaultColor}.
     */
    public int baseColor(ItemStack stack, @Nullable BlockEntity holder) {
        Integer own = TileDye.baseColor(stack);
        if (own != null) return own & 0xFFFFFF;
        return holder instanceof BoardSpaceBlockEntity boardSpace ? boardSpace.baseColor() & 0xFFFFFF : defaultColor;
    }

    /**
     * The swatch of the cartridge's colour: no colour or its own colour is the base swatch; -1 when it is none of them
     * (e.g. a token's colour on a start tile).
     */
    @Override
    public int get(ItemStack stack) {
        Integer color = stack.get(ModComponents.COLOR);
        if (color == null) return DEFAULT;
        int rgb = color & 0xFFFFFF;
        Integer own = TileDye.baseColor(stack);
        if (own != null && (own & 0xFFFFFF) == rgb) return DEFAULT;
        for (DyeColor dye : DyeColor.values()) {
            if ((dye.getEntityColor() & 0xFFFFFF) == rgb) return dye.getId();
        }
        return -1;
    }

    @Override
    public boolean accepts(ItemStack stack, int value) {
        return value >= 0 && value <= DEFAULT;
    }

    /**
     * On its board space (active slot): like a dye on the tile, sent to the players around; else on the cartridge. The
     * base swatch takes the dye off, as a sponge does ({@link TileDye#clear}): the cartridge's own colour, or none, so
     * that its host shows its own (a check point stays blue).
     */
    @Override
    public void set(CartridgeEdit edit, int value) {
        BoardSpaceBlockEntity boardSpace = edit.boardSpace();
        boolean active = boardSpace != null && edit.isActiveOnBoardSpace();
        if (value == DEFAULT) {
            if (active) {
                TileDye.clear(boardSpace);
                return;
            }
            Integer own = TileDye.baseColor(edit.stack());
            if (own != null) edit.stack().set(ModComponents.COLOR, own);
            else edit.stack().remove(ModComponents.COLOR);
            return;
        }
        // Stored like a dye used on the tile does (its entity colour, as is)
        int color = DyeColor.byId(value).getEntityColor();
        if (active) {
            ABoardSpaceBehavior.setColor(boardSpace, color);
        } else {
            edit.stack().set(ModComponents.COLOR, color);
        }
    }
}
