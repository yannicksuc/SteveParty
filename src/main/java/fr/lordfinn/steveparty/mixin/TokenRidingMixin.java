package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nobody rides a token (a tiny horse or pig pawn would be steered off the board), even with a saddle. */
@Mixin(Entity.class)
public abstract class TokenRidingMixin {
    @Inject(method = "startRiding(Lnet/minecraft/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
    private void steveparty$noRidingTokens(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity && TokenBase.isToken(vehicle)) cir.setReturnValue(false);
    }
}
