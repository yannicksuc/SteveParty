package fr.lordfinn.steveparty.items.custom;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Rarity;

import static net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE;
import static net.minecraft.component.DataComponentTypes.RARITY;

public class EpicWithGlintBlockItem extends BlockItem {

    public EpicWithGlintBlockItem(Block block, Settings settings) {
        super(block, settings.component(RARITY, Rarity.EPIC).component(ENCHANTMENT_GLINT_OVERRIDE, true));
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }
}
