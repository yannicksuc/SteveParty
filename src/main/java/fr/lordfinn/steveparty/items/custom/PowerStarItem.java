package fr.lordfinn.steveparty.items.custom;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Rarity;

import static net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE;
import static net.minecraft.component.DataComponentTypes.RARITY;

public class PowerStarItem extends Item {
    public PowerStarItem(Settings settings) {
        super( settings.component(RARITY, Rarity.RARE).component(ENCHANTMENT_GLINT_OVERRIDE, true));
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }
}
