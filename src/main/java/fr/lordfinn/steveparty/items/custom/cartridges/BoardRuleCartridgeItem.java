package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * A cartridge whose settings are a few numbers by name ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}), and which may
 * remember a few more in its board space ({@link ModComponents#BOARD_CARTRIDGE_STATE}): the Threshold obstacle, the
 * Common pot, the Key gate and the Trap. A missing number is its default.
 */
public abstract class BoardRuleCartridgeItem extends CartridgeItem {
    protected BoardRuleCartridgeItem(Settings settings) {
        super(settings);
    }

    /** One of the settings: its name, its default, its range; read, written and edited in the menu. */
    public record Setting(String key, int fallback, int min, int max) {
        public int get(@Nullable ItemStack stack) {
            return setting(stack, key, fallback, min, max);
        }

        public void put(ItemStack stack, int value) {
            putSetting(stack, key, value);
        }

        /** A choice among {@code options} (the option's index is the value). */
        public ChoiceModule choice(String id, String labelKey, List<ChoiceModule.Option> options) {
            return choice(id, labelKey, options, stack -> true);
        }

        public ChoiceModule choice(String id, String labelKey, List<ChoiceModule.Option> options, Predicate<ItemStack> enabled) {
            return new ChoiceModule(id, labelKey, options, this::get, (edit, value) -> put(edit.stack(), value), enabled);
        }

        /** Its − / + stepper, from {@code min} to {@code max}. */
        public NumberModule number(String id, String labelKey, ToIntFunction<ItemStack> color) {
            return number(id, labelKey, color, stack -> true);
        }

        public NumberModule number(String id, String labelKey, ToIntFunction<ItemStack> color, Predicate<ItemStack> enabled) {
            return new NumberModule(id, labelKey, min, max, this::get, (edit, value) -> put(edit.stack(), value), color, enabled);
        }

        /** A yes / no choice of the menu ({@code gui.steveparty.cartridge_menu.no} / {@code .yes}): 0 no, 1 yes. */
        public ChoiceModule yesNo(String id, String labelKey) {
            return choice(id, labelKey, List.of(new ChoiceModule.Option(MENU_KEY + "no"), new ChoiceModule.Option(MENU_KEY + "yes")));
        }
    }

    /** The setting {@code key}, {@code fallback} if not set, kept between {@code min} and {@code max}. */
    public static int setting(@Nullable ItemStack stack, String key, int fallback, int min, int max) {
        return IntMaps.get(stack, ModComponents.BOARD_CARTRIDGE_SETTINGS, key, fallback, min, max);
    }

    public static void putSetting(ItemStack stack, String key, int value) {
        IntMaps.put(stack, ModComponents.BOARD_CARTRIDGE_SETTINGS, key, value);
    }

    /** A remembered number ({@code fallback} if none). */
    public static int state(@Nullable ItemStack stack, String key, int fallback) {
        return IntMaps.of(stack, ModComponents.BOARD_CARTRIDGE_STATE).getOrDefault(key, fallback);
    }

    /** Remembers a number; null forgets it. */
    public static void putState(ItemStack stack, String key, @Nullable Integer value) {
        IntMaps.put(stack, ModComponents.BOARD_CARTRIDGE_STATE, key, value);
    }
}
