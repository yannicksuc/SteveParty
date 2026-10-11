package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.service.BoardActors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The board actors are holograms (see {@link BoardActors}): never aimed at (crosshair, projectiles) but while their
 * show wants blows or clicks on them, no knockback, and killed by a command they drop nothing (no loot, no equipment,
 * no experience).
 */
@Mixin(LivingEntity.class)
public abstract class BoardActorLivingMixin {
    /**
     * Not aimed at: the crosshair goes through it to the block or the mob behind (no outline, no name, no click on it),
     * and projectiles fly through. Our mobs' own {@code canHit} call this one ({@code super}).
     */
    @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramAim(CallbackInfoReturnable<Boolean> cir) {
        if (!BoardActors.isTargetable((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramKnockback(CallbackInfo ci) {
        if (BoardActors.isHologram((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramDrops(CallbackInfo ci) {
        if (BoardActors.isBoardActor((Entity) (Object) this)) ci.cancel();
    }
}
