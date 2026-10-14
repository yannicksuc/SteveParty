package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * One option among a few: a row of buttons (the chosen one pushed in, gold), or of colour swatches when every option
 * has a colour (the label then names the chosen one). Value: the option's index.
 * <p>
 * {@link #toggles()}: each button on or off by itself, at least one on (value: a bit per option, the first option's
 * the lowest). {@link #perRow}: the buttons wrap after that many. {@link #fitted()}: each button as wide as its text
 * asks, the room left shared out.
 */
public final class ChoiceModule extends CartridgeModule {
    /** A button: its text (translation key) and, for a swatch, its colour (-1: none), with an optional tooltip key. */
    public record Option(String key, int color, String tooltipKey) {
        public Option(String key) {
            this(key, -1, null);
        }

        public Option(String key, int color) {
            this(key, color, null);
        }

        /** A text button whose tooltip is its key + {@code .tooltip}. */
        public static Option tipped(String key) {
            return new Option(key, -1, key + ".tooltip");
        }
    }

    /** Odd: a text centred in it has as much room above as below its capitals. */
    public static final int BUTTON_H = 15;
    public static final int SWATCH_H = 18;
    /** Between two buttons, across and down. */
    public static final int GAP = 3;
    /** A fitted button: its text's width plus this (two pixels each side of its ink); between two of them, {@link #FITTED_GAP}. */
    public static final int FITTED_PAD = 4, FITTED_GAP = 2;

    private final List<Option> options;
    private final ToIntFunction<ItemStack> getter;
    private final ObjIntConsumer<CartridgeEdit> setter;
    private final Predicate<ItemStack> enabled;
    private boolean toggles;
    private int perRow = Integer.MAX_VALUE;
    private boolean fitted;

    public ChoiceModule(String id, String labelKey, List<Option> options, ToIntFunction<ItemStack> getter,
                        ObjIntConsumer<CartridgeEdit> setter, Predicate<ItemStack> enabled) {
        super(id, labelKey);
        if (options.isEmpty()) throw new IllegalArgumentException("a choice without options: " + id);
        this.options = List.copyOf(options);
        this.getter = getter;
        this.setter = setter;
        this.enabled = enabled;
    }

    public ChoiceModule(String id, String labelKey, List<Option> options, ToIntFunction<ItemStack> getter,
                        ObjIntConsumer<CartridgeEdit> setter) {
        this(id, labelKey, options, getter, setter, stack -> true);
    }

    public List<Option> options() {
        return options;
    }

    /** Each option on or off by itself (a bit each), at least one on. */
    public ChoiceModule toggles() {
        toggles = true;
        return this;
    }

    public boolean isToggles() {
        return toggles;
    }

    /** The buttons wrap after {@code count} in a row. */
    public ChoiceModule perRow(int count) {
        perRow = Math.max(1, count);
        return this;
    }

    /** How many buttons a row holds. */
    public int columns() {
        return Math.min(perRow, options.size());
    }

    public int rows() {
        return (options.size() + columns() - 1) / columns();
    }

    /** Each button as wide as its text asks (a single row of short texts of uneven widths). */
    public ChoiceModule fitted() {
        fitted = true;
        return this;
    }

    public boolean isFitted() {
        return fitted;
    }

    /**
     * The widths of a fitted row's buttons: each its text's ({@code textWidths}) plus {@link #FITTED_PAD}, the room left
     * shared out; null when they don't fit in {@code room} (equal widths then).
     */
    public static int @Nullable [] fittedWidths(int[] textWidths, int room) {
        int n = textWidths.length, need = (n - 1) * FITTED_GAP;
        for (int width : textWidths) need += width + FITTED_PAD;
        if (n == 0 || need > room) return null;
        int extra = room - need;
        int[] widths = new int[n];
        for (int o = 0; o < n; o++) widths[o] = textWidths[o] + FITTED_PAD + extra / n + (o < extra % n ? 1 : 0);
        return widths;
    }

    /** Whether option {@code option} is on for the value {@code value}. */
    public boolean isOn(int value, int option) {
        return toggles ? (value >> option & 1) != 0 : value == option;
    }

    /** The value after a click on option {@code option}: that one chosen, or (toggles) switched. */
    public int clicked(int value, int option) {
        return toggles ? value ^ (1 << option) : option;
    }

    /** The height of its buttons (its label excluded). */
    public int bodyHeight() {
        int row = swatches() ? SWATCH_H : BUTTON_H;
        return rows() * row + (rows() - 1) * GAP;
    }

    /** Colour swatches (every option has a colour) rather than text buttons. */
    public boolean swatches() {
        return options.stream().allMatch(option -> option.color() >= 0);
    }

    @Override
    public int height() {
        return labelHeight() + bodyHeight();
    }

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public boolean enabled(ItemStack stack) {
        return enabled.test(stack);
    }

    @Override
    public int get(ItemStack stack) {
        return getter.applyAsInt(stack);
    }

    @Override
    public boolean accepts(ItemStack stack, int value) {
        if (toggles) return value > 0 && value < 1 << options.size() && enabled(stack);
        return value >= 0 && value < options.size() && enabled(stack);
    }

    @Override
    public void set(CartridgeEdit edit, int value) {
        setter.accept(edit, value);
    }
}
