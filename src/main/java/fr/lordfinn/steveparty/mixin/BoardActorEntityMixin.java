package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.service.BoardActors;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The board actors are holograms (see {@link BoardActors}), whatever their class: they neither push nor are pushed
 * (by entities, fluids), never burn, are untouched by explosions, ride nothing but another actor (a Glandouille
 * tower), and are never saved.
 */
@Mixin(Entity.class)
public abstract class BoardActorEntityMixin {
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramPush(Entity entity, CallbackInfo ci) {
        if (BoardActors.isHologram((Entity) (Object) this) || BoardActors.isHologram(entity)) ci.cancel();
    }

    @Inject(method = "isPushedByFluids", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramFluids(CallbackInfoReturnable<Boolean> cir) {
        if (BoardActors.isHologram((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "isFireImmune", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramFire(CallbackInfoReturnable<Boolean> cir) {
        if (BoardActors.isHologram((Entity) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "isImmuneToExplosion", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramExplosion(CallbackInfoReturnable<Boolean> cir) {
        if (BoardActors.isHologram((Entity) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "canStartRiding", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramRide(Entity vehicle, CallbackInfoReturnable<Boolean> cir) {
        if (BoardActors.isHologram((Entity) (Object) this) && !BoardActors.isHologram(vehicle)) cir.setReturnValue(false);
    }

    @Inject(method = "shouldSave", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramSave(CallbackInfoReturnable<Boolean> cir) {
        if (BoardActors.isBoardActor((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
