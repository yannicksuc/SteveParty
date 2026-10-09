package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * One option among a few: a row of buttons (the chosen one pushed in, gold), or of colour swatches when every option
 * has a colour (the label then names the chosen one). Value: the option's index.
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

    public static final int BUTTON_H = 16;
    public static final int SWATCH_H = 18;

    private final List<Option> options;
    private final ToIntFunction<ItemStack> getter;
    private final ObjIntConsumer<CartridgeEdit> setter;
    private final Predicate<ItemStack> enabled;

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

    /** Colour swatches (every option has a colour) rather than text buttons. */
    public boolean swatches() {
        return options.stream().allMatch(option -> option.color() >= 0);
    }

    @Override
    public int height() {
        return labelHeight() + (swatches() ? SWATCH_H : BUTTON_H);
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
        return value >= 0 && value < options.size() && enabled(stack);
    }

    @Override
    public void set(CartridgeEdit edit, int value) {
        setter.accept(edit, value);
    }
}
