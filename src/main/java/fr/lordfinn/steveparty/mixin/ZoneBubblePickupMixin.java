package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Items, experience orbs and arrows are only picked up by who may touch the place they lie in: nobody reaches
 * through the border of a mini-game zone, and a spectator picks nothing up ({@link ZoneBorder#blocksPickup}).
 */
@Mixin({ItemEntity.class, ExperienceOrbEntity.class, PersistentProjectileEntity.class})
public abstract class ZoneBubblePickupMixin {

    @Inject(method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void steveparty$noPickupAcrossZoneBorder(PlayerEntity player, CallbackInfo ci) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.blocksPickup(player, (Entity) (Object) this)) ci.cancel();
    }
}
