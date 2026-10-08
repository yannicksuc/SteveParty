package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.entity.Bucketable;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieVillagerEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The vanilla right-click actions that would break a pawn do nothing on a token: picking it up in a bucket (it would
 * leave the board as a plain mob), shearing a mooshroom or curing a zombie villager (both turn it into another mob,
 * without its token data), lighting a creeper, and taming, sitting, feeding or saddling pets and horses (they would
 * change its pose or grow it up). Mounting a horse token with an empty hand still works: riding a tiny horse pawn
 * is part of the fun.
 * <p>
 * The harmless ones keep working (shearing a sheep, milking, dyeing, brushing...), and so do the items acting on
 * entities (token, wand, name tag): PASS lets the held item handle the click.
 */
@Mixin(MobEntity.class)
public abstract class TokenVanillaActionsMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void steveparty$keepPawnsIntact(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        MobEntity mob = (MobEntity) (Object) this;
        if (!TokenBase.isToken(mob)) return;
        // An empty hand poses it (TokenPoseInteractMixin), even a pet's
        if (fr.lordfinn.steveparty.entities.TokenPoses.posesOnClick(mob, player, hand)) return;
        if (steveparty$breaksPawn(mob, player.getStackInHand(hand))) cir.setReturnValue(ActionResult.PASS);
    }

    @Unique
    private static boolean steveparty$breaksPawn(MobEntity mob, ItemStack stack) {
        return mob instanceof TameableEntity
                || (mob instanceof AbstractHorseEntity && !stack.isEmpty())
                || (mob instanceof Bucketable && stack.isOf(Items.WATER_BUCKET))
                || (mob instanceof MooshroomEntity && stack.isOf(Items.SHEARS))
                || (mob instanceof ZombieVillagerEntity && stack.isOf(Items.GOLDEN_APPLE))
                || (mob instanceof CreeperEntity && stack.isIn(ItemTags.CREEPER_IGNITERS));
    }
}
