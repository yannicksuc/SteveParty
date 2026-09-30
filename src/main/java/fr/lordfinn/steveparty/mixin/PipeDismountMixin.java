package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sneaking does not get a player out of a pipe (sneaking is how one goes in). */
@Mixin(PlayerEntity.class)
public abstract class PipeDismountMixin {
    @Inject(method = "shouldDismount", at = @At("HEAD"), cancellable = true)
    private void steveparty$stayInPipe(CallbackInfoReturnable<Boolean> cir) {
        if (((PlayerEntity) (Object) this).getVehicle() instanceof PipeCarrierEntity) cir.setReturnValue(false);
    }
}
