package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Items lying on either side of a mini-game zone's border stay apart: two stacks don't merge across it, and a mob
 * takes none from the other side.
 */
@Mixin(ItemEntity.class)
public abstract class ZoneBubbleItemEntityMixin {

    @Inject(method = "tryMerge(Lnet/minecraft/entity/ItemEntity;)V", at = @At("HEAD"), cancellable = true)
    private void steveparty$noMergeAcrossZoneBorder(ItemEntity other, CallbackInfo ci) {
        if (!ZoneBorder.ACTIVE) return;
        ItemEntity self = (ItemEntity) (Object) this;
        if (ZoneBorder.across(self.getWorld(), self.getBlockPos(), other.getBlockPos())) ci.cancel();
    }

    @Inject(method = "cannotPickup()Z", at = @At("RETURN"), cancellable = true)
    private void steveparty$noMobPickupAcrossZoneBorder(CallbackInfoReturnable<Boolean> cir) {
        if (!ZoneBorder.ACTIVE || cir.getReturnValueZ()) return;
        if (ZoneBorder.blocksMobPickup((ItemEntity) (Object) this)) cir.setReturnValue(true);
    }
}
