package fr.lordfinn.steveparty.items.custom.glandouille;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Acorn Hat (Chapeau de gland): the cap a Glandouille lost (see GlandouilleEntity#popHat). Worn on the head, purely
 * for looks (vanilla {@link Equipment}: right click, or the helmet slot); given back to a hatless Glandouille, it puts
 * it on again.
 */
public class AcornHatItem extends Item implements Equipment {
    public AcornHatItem(Settings settings) {
        super(settings);
    }

    @Override
    public EquipmentSlot getSlotType() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public RegistryEntry<SoundEvent> getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return equipAndSwap(this, world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Tooltips.of(tooltip).tags(Tooltips.Tag.COSTUME).summary("item.steveparty.acorn_hat.tooltip");
    }
}
