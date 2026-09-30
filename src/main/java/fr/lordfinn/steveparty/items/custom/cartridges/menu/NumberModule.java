package fr.lordfinn.steveparty.items.custom.cartridges.menu;

import net.minecraft.item.ItemStack;

import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * A whole number from {@link #min()} to {@link #max()}: a − / + stepper around a big figure, and (up to 9) one lamp per
 * value, lit up to the current one. The mouse wheel over it changes it by one. Value: the number.
 */
public final class NumberModule extends CartridgeModule {
    public static final int ROW_H = 18;

    private final int min, max;
    private final ToIntFunction<ItemStack> getter;
    private final ObjIntConsumer<CartridgeEdit> setter;
    private final ToIntFunction<ItemStack> color;

    /**
     * @param color the colour of the figure and of the lit lamps, for the cartridge's state (e.g. green forward)
     */
    public NumberModule(String id, String labelKey, int min, int max, ToIntFunction<ItemStack> getter,
                        ObjIntConsumer<CartridgeEdit> setter, ToIntFunction<ItemStack> color) {
        super(id, labelKey);
        if (min > max) throw new IllegalArgumentException("empty range: " + id);
        this.min = min;
        this.max = max;
        this.getter = getter;
        this.setter = setter;
        this.color = color;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public int color(ItemStack stack) {
        return color.applyAsInt(stack);
    }

    @Override
    public int height() {
        return labelHeight() + ROW_H;
    }

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public int get(ItemStack stack) {
        return getter.applyAsInt(stack);
    }

    @Override
    public boolean accepts(ItemStack stack, int value) {
        return value >= min && value <= max;
    }

    @Override
    public void set(CartridgeEdit edit, int value) {
        setter.accept(edit, value);
    }
}
