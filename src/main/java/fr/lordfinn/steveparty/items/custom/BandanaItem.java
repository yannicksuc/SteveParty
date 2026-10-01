package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * The Boxed Trader's bandana, stolen with shears (or given back to him). Its colour (0-4, the merchant's
 * BandanaColor: teal, blue, pink, orange, yellow) lives in {@link ModComponents#BANDANA_COLOR}. It can be worn on the
 * head (vanilla equippable component, one equipment model per colour): no armour, a soft cloth sound, and merchants
 * simply ignore the players wearing one.
 */
public class BandanaItem extends Item {
    public static final String[] COLOR_NAMES = {"teal", "blue", "pink", "orange", "yellow"};

    public BandanaItem(Settings settings) {
        super(settings.component(ModComponents.BANDANA_COLOR, 0).component(DataComponentTypes.EQUIPPABLE, equippable(0)));
    }

    /** Worn on the head, with the equipment model of the colour (assets/steveparty/models/equipment/bandana_*.json). */
    public static EquippableComponent equippable(int color) {
        return EquippableComponent.builder(EquipmentSlot.HEAD)
                .equipSound(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER)
                .model(Steveparty.id("bandana_" + COLOR_NAMES[clamp(color)]))
                .build();
    }

    public static ItemStack create(int color) {
        ItemStack stack = new ItemStack(ModItems.BANDANA);
        setColor(stack, color);
        return stack;
    }

    public static void setColor(ItemStack stack, int color) {
        stack.set(ModComponents.BANDANA_COLOR, clamp(color));
        stack.set(DataComponentTypes.EQUIPPABLE, equippable(color));
    }

    public static int getColor(ItemStack stack) {
        return clamp(stack.getOrDefault(ModComponents.BANDANA_COLOR, 0));
    }

    private static int clamp(int color) {
        return MathHelper.clamp(color, 0, BoxedTraderEntity.BANDANA_COLORS - 1);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.steveparty.bandana.color." + COLOR_NAMES[getColor(stack)]).formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
