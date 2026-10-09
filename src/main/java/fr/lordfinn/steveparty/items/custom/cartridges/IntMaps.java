package fr.lordfinn.steveparty.items.custom.cartridges;

import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Whole numbers by name in one component of a cartridge (the Mistigri's settings, the board rule cartridges' settings
 * and state): a cartridge with many settings stores only those set, a missing one is its default.
 */
public final class IntMaps {
    private IntMaps() {}

    /** The numbers of {@code stack} (none for no cartridge). */
    public static Map<String, Integer> of(@Nullable ItemStack stack, ComponentType<Map<String, Integer>> component) {
        return stack == null ? Map.of() : stack.getOrDefault(component, Map.of());
    }

    /** The number {@code key}, {@code fallback} if not set, kept between {@code min} and {@code max}. */
    public static int get(@Nullable ItemStack stack, ComponentType<Map<String, Integer>> component, String key, int fallback,
                          int min, int max) {
        return Math.clamp(of(stack, component).getOrDefault(key, fallback), min, max);
    }

    /** Sets the number {@code key}; null forgets it (and no number left: the component goes). */
    public static void put(ItemStack stack, ComponentType<Map<String, Integer>> component, String key, @Nullable Integer value) {
        Map<String, Integer> numbers = new HashMap<>(stack.getOrDefault(component, Map.of()));
        if (value == null) numbers.remove(key);
        else numbers.put(key, value);
        if (numbers.isEmpty()) stack.remove(component);
        else stack.set(component, Map.copyOf(numbers));
    }
}
