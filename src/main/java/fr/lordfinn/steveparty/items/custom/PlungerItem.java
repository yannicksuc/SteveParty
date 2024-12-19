package fr.lordfinn.steveparty.items.custom;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import static fr.lordfinn.steveparty.sounds.ModSounds.PLUNGER_SUCK_IN;
import static fr.lordfinn.steveparty.sounds.ModSounds.PLUNGER_SUCK_OUT;

public class PlungerItem extends Item {
    public PlungerItem(Settings plunger) {
        super(plunger);
    }
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (entity.isSilent()) {
            entity.setSilent(false);
            user.sendMessage(Text.literal("Mob unsilenced!"), true);
            user.getWorld().playSound(user, user.getBlockPos(), PLUNGER_SUCK_OUT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        } else {
            entity.setSilent(true);
            user.sendMessage(Text.literal("Mob silenced!"), true);
            user.getWorld().playSound(user, user.getBlockPos(), PLUNGER_SUCK_IN, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        return ActionResult.SUCCESS;
    }
}
