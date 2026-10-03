package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A projectile hits nothing on the other side of a mini-game zone's border: an arrow flying along it reaches no
 * player, no item frame and no button over there, an ender pearl does not land there ({@link ZoneBorder#blocksHit}).
 */
@Mixin(ProjectileEntity.class)
public abstract class ZoneBubbleProjectileMixin {

    @Inject(method = "onCollision(Lnet/minecraft/util/hit/HitResult;)V", at = @At("HEAD"), cancellable = true)
    private void steveparty$noHitAcrossZoneBorder(HitResult hit, CallbackInfo ci) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.blocksHit((ProjectileEntity) (Object) this, hit)) ci.cancel();
    }
}
