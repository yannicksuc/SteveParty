package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The items of a mini-game session never lie out of its zone: what a spectator, or a participant out of its zone,
 * drops (thrown, or lost on dying) is destroyed instead ({@link ZoneBorder#blocksDrop}).
 */
@Mixin(PlayerEntity.class)
public abstract class ZoneBubblePlayerEntityMixin {

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void steveparty$noSessionItemOutOfZone(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.blocksDrop((PlayerEntity) (Object) this)) cir.setReturnValue(null);
    }
}
