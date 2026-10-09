package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * A whole number of a cartridge kept in a component of its own: {@code fallback} when not set (or no cartridge), always
 * read between {@code min} and {@code max}, and its stepper in the cartridge's menu.
 */
public record IntSetting(ComponentType<Integer> component, int min, int max, int fallback) {
    public int get(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return fallback;
        return Math.clamp(stack.getOrDefault(component, fallback), min, max);
    }

    public void set(ItemStack stack, int value) {
        stack.set(component, value);
    }

    /** Its − / + stepper, from {@code min} to {@code max}. */
    public NumberModule module(String id, String labelKey, ToIntFunction<ItemStack> color) {
        return module(id, labelKey, color, stack -> true);
    }

    /** Its stepper, greyed out while {@code enabled} says no. */
    public NumberModule module(String id, String labelKey, ToIntFunction<ItemStack> color, Predicate<ItemStack> enabled) {
        return new NumberModule(id, labelKey, min, max, this::get, (edit, value) -> set(edit.stack(), value), color, enabled);
    }
}
