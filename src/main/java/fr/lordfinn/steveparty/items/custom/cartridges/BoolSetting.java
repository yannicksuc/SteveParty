package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A yes / no of a cartridge kept in a component of its own (no by default), and its two buttons in the menu. */
public record BoolSetting(ComponentType<Boolean> component) {
    public boolean get(@Nullable ItemStack stack) {
        return stack != null && stack.getOrDefault(component, false);
    }

    public void set(ItemStack stack, boolean value) {
        stack.set(component, value);
    }

    /** Two buttons: {@code no} (index 0), then {@code yes} (index 1). */
    public ChoiceModule choice(String id, String labelKey, ChoiceModule.Option no, ChoiceModule.Option yes) {
        return new ChoiceModule(id, labelKey, List.of(no, yes), stack -> get(stack) ? 1 : 0,
                (edit, value) -> set(edit.stack(), value == 1));
    }
}
