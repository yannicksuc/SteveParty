package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.api.registry.ContentKeys;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
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
 * A new module: {@code DiceModules.register(new DiceModule("id", maxCount, negative) { ...overrides... })} (an
 * addon gives an {@link Identifier} of its own namespace). Its item ({@code <namespace>:dice_module_<path>}) is
 * registered with it; it needs an item model and texture of that name, the lang keys
 * {@code item.<namespace>.dice_module_<path>}, {@code dice_module.<namespace>.<path>} (its name on a die) and
 * {@code dice_module.<namespace>.<path>.desc} (its effect, {@code %s}: see {@link #description}), a 5 px pictogram
 * ({@code textures/gui/dice_module/<path>.png}, drawn over the icon of the dice carrying it) and its recipes (for
 * Steve Party's, one line in the recipe data generator, {@code offerModule}: the module item, and the craft that puts
 * it on a die).
 */
public class DiceModule {
    private final Identifier identifier;
    private final String id;
    private final int maxCount;
    private final boolean negative;

    /**
     * @param maxCount how many of it a die may carry (1: it does not stack)
     * @param negative it plays against the roller: its line on the die is red
     */
    public DiceModule(String id, int maxCount, boolean negative) {
        this(java.util.Objects.requireNonNull(ContentKeys.id(id), id), maxCount, negative);
    }

    /** A module of any namespace (an addon's). */
    public DiceModule(Identifier id, int maxCount, boolean negative) {
        this.identifier = id;
        this.id = ContentKeys.key(id);
        this.maxCount = Math.max(1, maxCount);
        this.negative = negative;
    }

    /** Its key on a die ({@link DiceModulesComponent}): {@code lucky} for Steve Party's, {@code myaddon:sticky} for an addon's. */
    public final String id() {
        return id;
    }

    /** Its id in {@link fr.lordfinn.steveparty.api.StevePartyRegistries#DICE_MODULES}. */
    public final Identifier identifier() {
        return identifier;
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

    /** Path of the module item: {@code dice_module_<path>}. */
    public final String itemPath() {
        return "dice_module_" + identifier.getPath();
    }

    /** Id of the module item, in the module's namespace. */
    public final Identifier itemId() {
        return Identifier.of(identifier.getNamespace(), itemPath());
    }

    /** The module item (AIR if it is not registered). */
    public final Item item() {
        return Registries.ITEM.get(itemId());
    }

    /** Start of its lang keys: {@code dice_module.<namespace>.<path>}. */
    protected final String translationKey() {
        return "dice_module." + identifier.getNamespace() + "." + identifier.getPath();
    }

    /** Its name on a die. */
    public Text name() {
        return Text.translatable(translationKey());
    }

    /** What {@code count} of it do, in one line ({@code %s} of the lang entry: see {@link #descriptionValue}). */
    public Text description(int count) {
        return Text.translatable(translationKey() + ".desc", descriptionValue(count));
    }

    /** What it does, on the tooltip of its item (its description by default). */
    public Text itemDescription() {
        return description(1);
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

    /**
     * onSpent (Power-up): true if the die is a power-up: spent once rolled instead of going back to its roller, and in a
     * party rolled in place of the player's die, as the power-up of the turn.
     */
    public boolean makesPowerUp() {
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
