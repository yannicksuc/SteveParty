package fr.lordfinn.steveparty.client.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A die forged in the Dice Forge: inputs the die faces, the blank faces, the 5 star fragments, the optional modules and
 * the Gravity Core waking the forge; outputs forged dice (and the plain Simple Die, so looking up a die's recipes
 * finds the forge).
 */
public class DiceForgeDisplay extends BasicDisplay {
    public DiceForgeDisplay(EntryIngredient faces, EntryIngredient blankFaces, List<EntryIngredient> fragments,
                            EntryIngredient modules, EntryIngredient core, EntryIngredient dice) {
        super(inputs(faces, blankFaces, fragments, modules, core), List.of(dice), Optional.empty());
    }

    private static List<EntryIngredient> inputs(EntryIngredient faces, EntryIngredient blankFaces, List<EntryIngredient> fragments,
                                                EntryIngredient modules, EntryIngredient core) {
        List<EntryIngredient> inputs = new ArrayList<>(List.of(faces, blankFaces, modules, core));
        inputs.addAll(fragments);
        return inputs;
    }

    public EntryIngredient faces() {
        return getInputEntries().get(0);
    }

    public EntryIngredient blankFaces() {
        return getInputEntries().get(1);
    }

    public EntryIngredient modules() {
        return getInputEntries().get(2);
    }

    public EntryIngredient core() {
        return getInputEntries().get(3);
    }

    /** The 5 fragment slots. */
    public List<EntryIngredient> fragments() {
        return getInputEntries().subList(4, getInputEntries().size());
    }

    public EntryIngredient dice() {
        return getOutputEntries().getFirst();
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return SteveReiPlugin.DICE_FORGE;
    }
}
