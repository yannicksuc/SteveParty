package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A cartridge whose settings are a few numbers by name ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}), and which may
 * remember a few more in its board space ({@link ModComponents#BOARD_CARTRIDGE_STATE}): the Threshold obstacle, the
 * Common pot, the Key gate and the Trap. A missing number is its default.
 */
public abstract class BoardRuleCartridgeItem extends CartridgeItem {
    protected BoardRuleCartridgeItem(Settings settings) {
        super(settings);
    }

    /** The setting {@code key}, {@code fallback} if not set, kept between {@code min} and {@code max}. */
    public static int setting(ItemStack stack, String key, int fallback, int min, int max) {
        Map<String, Integer> settings = stack == null ? Map.of() : stack.getOrDefault(ModComponents.BOARD_CARTRIDGE_SETTINGS, Map.of());
        return Math.clamp(settings.getOrDefault(key, fallback), min, max);
    }

    public static void putSetting(ItemStack stack, String key, int value) {
        Map<String, Integer> settings = new HashMap<>(stack.getOrDefault(ModComponents.BOARD_CARTRIDGE_SETTINGS, Map.of()));
        settings.put(key, value);
        stack.set(ModComponents.BOARD_CARTRIDGE_SETTINGS, Map.copyOf(settings));
    }

    /** A remembered number ({@code fallback} if none). */
    public static int state(ItemStack stack, String key, int fallback) {
        Map<String, Integer> state = stack == null ? Map.of() : stack.getOrDefault(ModComponents.BOARD_CARTRIDGE_STATE, Map.of());
        return state.getOrDefault(key, fallback);
    }

    /** Remembers a number; null forgets it. */
    public static void putState(ItemStack stack, String key, Integer value) {
        Map<String, Integer> state = new HashMap<>(stack.getOrDefault(ModComponents.BOARD_CARTRIDGE_STATE, Map.of()));
        if (value == null) state.remove(key);
        else state.put(key, value);
        if (state.isEmpty()) stack.remove(ModComponents.BOARD_CARTRIDGE_STATE);
        else stack.set(ModComponents.BOARD_CARTRIDGE_STATE, Map.copyOf(state));
    }

    /** A yes / no choice of the menu ({@code gui.steveparty.cartridge_menu.no} / {@code .yes}). */
    public static ChoiceModule yesNo(String id, String labelKey, String key, boolean fallback) {
        return new ChoiceModule(id, labelKey, List.of(new ChoiceModule.Option(MENU_KEY + "no"),
                new ChoiceModule.Option(MENU_KEY + "yes")),
                stack -> setting(stack, key, fallback ? 1 : 0, 0, 1),
                (edit, value) -> putSetting(edit.stack(), key, value));
    }
}
