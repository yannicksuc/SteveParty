package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
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
        Tooltips tips = Tooltips.of(tooltip).tags(Tooltips.Tag.DICE_MODULE);
        if (module.negative()) tips.tags(Tooltips.Tag.NEGATIVE);
        tips.summary(module.itemDescription());
        tips.more(more -> {
            if (module.stacks()) more.note("tooltip.steveparty.dice_module.stacks", Tooltips.value("×" + module.maxCount()));
            more.craft("tooltip.steveparty.dice_module.usage");
        });
    }
}
