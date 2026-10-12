package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import fr.lordfinn.steveparty.components.MistigriOutcomes;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Kind;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Outcome;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Target;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Mistigri's sentences as an editable list ({@link MistigriOutcomes}): a bounded box of rows that scrolls, each row
 * one sentence (its kind's icon, who it falls on, its amount, its chance, a delete button that asks first); a row
 * clicked opens under it the palettes of kinds and targets, its amount (a number or a percentage) and its weight. Under
 * the box: add a sentence, and how many are put on the roulette at each landing (« 5/8 »).
 * <p>
 * A change is an action sent as one number ({@link #action}: what, which row, the value) and checked on the server
 * against the cartridge's list; the menu applies the same action to its own copy at once ({@link #apply}), so a quick
 * series of clicks reads right before the server's answer comes back.
 */
public final class OutcomeListModule extends CartridgeModule {
    /** A row closed; the box's height (rows shown); the line under it (add, draws). */
    public static final int ROW_H = 18, VISIBLE_ROWS = 7, LIST_H = VISIBLE_ROWS * ROW_H + 2, FOOTER_GAP = 3, FOOTER_H = 16;
    /** Its height under its title. */
    public static final int BODY_H = LIST_H + FOOTER_GAP + FOOTER_H;
    /** The actions. */
    public static final int ADD = 0, DELETE = 1, KIND = 2, TARGET = 3, AMOUNT = 4, PERCENT = 5, WEIGHT = 6, DRAWS = 7;
    private static final int OPS = 8, ROWS = 16;

    public OutcomeListModule(String id, @Nullable String labelKey) {
        super(id, labelKey);
    }

    /** Action {@code op} on row {@code row} (0 when it takes none) with {@code value} (0 or more). */
    public static int action(int op, int row, int value) {
        return (value * ROWS + row) * OPS + op;
    }

    public static int op(int action) {
        return action % OPS;
    }

    public static int row(int action) {
        return action / OPS % ROWS;
    }

    public static int value(int action) {
        return action / OPS / ROWS;
    }

    @Override
    public int height() {
        return labelHeight() + BODY_H;
    }

    @Override
    public boolean editable() {
        return true;
    }

    /** A value that changes with the list (a change is shown at once, without waiting for it). */
    @Override
    public int get(ItemStack stack) {
        return MistigriOutcomes.of(stack).hashCode();
    }

    @Override
    public boolean accepts(ItemStack stack, int value) {
        return value >= 0 && apply(MistigriOutcomes.of(stack), value) != null;
    }

    @Override
    public void set(CartridgeEdit edit, int value) {
        MistigriOutcomes next = apply(MistigriOutcomes.of(edit.stack()), value);
        if (next != null) MistigriOutcomes.set(edit.stack(), next);
    }

    /** {@code outcomes} after {@code action}; null if it is not a valid one for them. */
    public static @Nullable MistigriOutcomes apply(MistigriOutcomes outcomes, int action) {
        if (action < 0) return null;
        int op = op(action), row = row(action), value = value(action);
        if (op == ADD) return outcomes.size() < MistigriOutcomes.MAX ? outcomes.add(MistigriOutcomes.ADDED) : null;
        if (op == DRAWS) return value <= MistigriOutcomes.MAX ? outcomes.withDraws(value) : null;
        if (row >= outcomes.size()) return null;
        Outcome outcome = outcomes.get(row);
        return switch (op) {
            case DELETE -> outcomes.remove(row);
            case KIND -> value < Kind.values().length ? outcomes.with(row, outcome.withKind(Kind.values()[value])) : null;
            case TARGET -> value < Target.values().length && (outcome.kind().anyTarget || value == Target.LANDED.ordinal())
                    ? outcomes.with(row, outcome.withTarget(Target.values()[value])) : null;
            case AMOUNT -> outcome.kind().hasAmount() && value >= (outcome.percent() ? 1 : outcome.kind().min)
                    && value <= (outcome.percent() ? 100 : outcome.kind().max) ? outcomes.with(row, outcome.withAmount(value)) : null;
            case PERCENT -> value <= 1 && (value == 0 || outcome.kind().percent) ? outcomes.with(row, outcome.withPercent(value == 1)) : null;
            case WEIGHT -> value <= MistigriOutcomes.MAX_WEIGHT ? outcomes.with(row, outcome.withWeight(value)) : null;
            default -> null;
        };
    }
}
