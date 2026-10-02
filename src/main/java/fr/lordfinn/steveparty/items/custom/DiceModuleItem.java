package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.dice.DiceModule;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The item of a dice module ({@link DiceModule}): placed in a module slot of the Dice Forge, every die forged carries
 * the module; crafted with a die, it puts the module on that die. It is never consumed.
 */
public class DiceModuleItem extends Item {
    private final DiceModule module;

    public DiceModuleItem(DiceModule module, Settings settings) {
        super(settings);
        this.module = module;
    }

    public DiceModule module() {
        return module;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(module.description(1).copy().formatted(module.negative() ? Formatting.RED : Formatting.GRAY));
        if (module.stacks()) {
            tooltip.add(Text.translatable("tooltip.steveparty.dice_module.stacks", module.maxCount()).formatted(Formatting.DARK_GRAY));
        }
        tooltip.add(Text.translatable("tooltip.steveparty.dice_module.usage").formatted(Formatting.DARK_GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
