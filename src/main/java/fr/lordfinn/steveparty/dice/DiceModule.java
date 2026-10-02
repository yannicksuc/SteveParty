package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A property of a whole die (its faces are {@link fr.lordfinn.steveparty.components.DiceFacesComponent}). A die
 * carries modules ({@link DiceModulesComponent}: each with a count, for the modules that stack) put on it by the Dice
 * Forge's module slots or at the crafting table; the thrown die ({@link DiceEntity}, {@link DiceRollSequence}) and the
 * move of the token ({@code TokenMovementService}) ask them what they change. Everything below does nothing by
 * default: a module overrides what it needs. Server side only.
 * <p>
 * A new module: {@code DiceModules.register(new DiceModule("id", maxCount, negative) { ...overrides... })}. Its item
 * ({@code dice_module_<id>}) is registered with it; it needs an item model and texture of that name, the lang keys
 * {@code item.steveparty.dice_module_<id>}, {@code dice_module.steveparty.<id>} (its name on a die) and
 * {@code dice_module.steveparty.<id>.desc} (its effect, {@code %s}: see {@link #description}), a 5 px pictogram
 * ({@code textures/gui/dice_module/<id>.png}, drawn over the icon of the dice carrying it) and its recipes (one line
 * in the recipe data generator, {@code offerModule}: the module item, and the craft that puts it on a die).
 */
public class DiceModule {
    private final String id;
    private final int maxCount;
    private final boolean negative;

    /**
     * @param maxCount how many of it a die may carry (1: it does not stack)
     * @param negative it plays against the roller: its line on the die is red
     */
    public DiceModule(String id, int maxCount, boolean negative) {
        this.id = id;
        this.maxCount = Math.max(1, maxCount);
        this.negative = negative;
    }

    public final String id() {
        return id;
    }

    public final int maxCount() {
        return maxCount;
    }

    public final boolean stacks() {
        return maxCount > 1;
    }

    public final boolean negative() {
        return negative;
    }

    /** Colour of its line on a die: red for a negative module, the usual aqua otherwise. */
    public final Formatting color() {
        return negative ? Formatting.RED : Formatting.AQUA;
    }

    /** Path of the module item: {@code dice_module_<id>}. */
    public final String itemPath() {
        return "dice_module_" + id;
    }

    /** The module item (AIR if it is not registered). */
    public final Item item() {
        return Registries.ITEM.get(Steveparty.id(itemPath()));
    }

    /** Its name on a die. */
    public Text name() {
        return Text.translatable("dice_module.steveparty." + id);
    }

    /** What {@code count} of it do, in one line ({@code %s} of the lang entry: see {@link #descriptionValue}). */
    public Text description(int count) {
        return Text.translatable("dice_module.steveparty." + id + ".desc", descriptionValue(count));
    }

    /** The number shown in the description for {@code count} modules (the count itself by default). */
    protected int descriptionValue(int count) {
        return count;
    }

    // ------------------------------------------------------------------ the roll (DiceRollSequence)

    /** Slow: the die turns slowly through its faces and its roller stops it by hand on the face shown. */
    public boolean stoppedByHand() {
        return false;
    }

    /** Choice: the roller picks the face of each die; nothing else of the roll applies. */
    public boolean rollerChooses() {
        return false;
    }

    /** Lucky: rolls made on top of the first one; the roller keeps one of the results. */
    public int extraRolls(int count) {
        return 0;
    }

    /** Reroll: times the roller may throw the result away and roll again. */
    public int rerolls(int count) {
        return 0;
    }

    /** Changes what the final result does (Reversed: backward, coins lost instead of gained). */
    public DiceOutcome modifyOutcome(DiceOutcome outcome, int count) {
        return outcome;
    }

    /** onRoll: the die was just thrown. */
    public void onThrown(DiceEntity dice, int count) {
    }

    /** afterRoll: the roll is final and was just announced. */
    public void afterRoll(DiceEntity dice, DiceOutcome outcome, int count) {
    }

    /** onSpent (Infinity): true if the die goes back to its roller once rolled instead of being lost. */
    public boolean returnsToRoller() {
        return false;
    }

    // ------------------------------------------------------------------ the move of the token

    /** Skeleton Key: the token walks through the Stop spaces and the shop check points without stopping. */
    public boolean ignoresStops() {
        return false;
    }

    /**
     * Homing: the branch the token takes by itself at a fork, or null to ask its player as usual.
     *
     * @param forks the board spaces the fork leads to (at least two)
     */
    public @Nullable BoardSpaceDestination chooseFork(MobEntity token, List<BoardSpaceDestination> forks, Random random) {
        return null;
    }

    @Override
    public String toString() {
        return "DiceModule[" + id + "]";
    }
}
