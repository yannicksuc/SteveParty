package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Trap: when your token stops on a Trap space during a party, you may set it there (used up, see BoardTraps). Found
 * in the shops, the mini-games' rewards, a Common pot or an item space, like any item.
 */
public class BoardTrapItem extends Item {
    public BoardTrapItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        CartridgeItem.addWrapped(tooltip, Text.translatable("tooltip.steveparty.board_trap"), Formatting.GRAY);
        super.appendTooltip(stack, context, tooltip, type);
    }
}
