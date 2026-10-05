package fr.lordfinn.steveparty.client.compat.rei;

import fr.lordfinn.steveparty.compat.CartridgeApplications;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;

import java.util.List;
import java.util.Optional;

/**
 * A cartridge put in a tile: inputs the tile (its 3 sizes) and the cartridge, outputs the tile holding it. The cartridge
 * is also among the outputs (not drawn), so looking up its recipes shows where it goes as well as its usages.
 */
public class CartridgeApplicationDisplay extends BasicDisplay {
    private final CartridgeApplications.Application application;

    public CartridgeApplicationDisplay(CartridgeApplications.Application application, EntryIngredient tiles,
                                       EntryIngredient cartridge, EntryIngredient results) {
        super(List.of(tiles, cartridge), List.of(results, cartridge), Optional.empty());
        this.application = application;
    }

    public CartridgeApplications.Application application() {
        return application;
    }

    public EntryIngredient tiles() {
        return getInputEntries().get(0);
    }

    public EntryIngredient cartridge() {
        return getInputEntries().get(1);
    }

    public EntryIngredient results() {
        return getOutputEntries().get(0);
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return SteveReiPlugin.CARTRIDGE_APPLICATION;
    }
}
