package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.board.ExplorerHelmet;
import fr.lordfinn.steveparty.items.ModArmorMaterials;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Explorer's Helmet: worn on the head (a helmet without armour), its headlamp lit, it shows the whole board (see
 * {@link ExplorerHelmet}). A key switches the lamp, and the view with it.
 */
public class ExplorerHelmetItem extends ArmorItem {
    private static final int CONTROLS_COLOR = 0xfcb017;

    public ExplorerHelmetItem(Settings settings) {
        super(ModArmorMaterials.EXPLORER_HELMET, Type.HELMET, settings);
    }

    /** No armour points (and no empty "When on Head:" tooltip section). */
    @Override
    public AttributeModifiersComponent getAttributeModifiers() {
        return AttributeModifiersComponent.DEFAULT;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        CartridgeItem.addWrapped(tooltip, Text.translatable("tooltip.steveparty.explorer_helmet"), Formatting.GRAY);
        tooltip.add(Text.translatable(ExplorerHelmet.lit(stack) ? "tooltip.steveparty.explorer_helmet.lit" : "tooltip.steveparty.explorer_helmet.unlit")
                .formatted(ExplorerHelmet.lit(stack) ? Formatting.YELLOW : Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.controls").setStyle(Style.EMPTY.withBold(true).withColor(CONTROLS_COLOR)));
        tooltip.add(Text.translatable("tooltip.steveparty.explorer_helmet.controls.wear").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.explorer_helmet.controls.lamp",
                Text.keybind("key.steveparty.explorer_helmet_lamp")).formatted(Formatting.GRAY));
    }
}
