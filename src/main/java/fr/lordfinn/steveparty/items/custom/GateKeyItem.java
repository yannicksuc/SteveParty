package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Gate Key: in its player's inventory, it opens a Key gate on the board when their token comes to it (used up,
 * see KeyGates). Found in the shops, the mini-games' rewards, a Common pot or an item space, like any item.
 */
public class GateKeyItem extends Item {
    public GateKeyItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Tooltips.of(tooltip).summary("tooltip.steveparty.gate_key");
        super.appendTooltip(stack, context, tooltip, type);
    }
}
