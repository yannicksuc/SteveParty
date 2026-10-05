package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.telescope.TelescopeClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Looking through a Telescope: the view zooms in, and the hand is out of it. */
@Mixin(GameRenderer.class)
public class GameRendererTelescopeMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void steveparty$telescopeZoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        float multiplier = TelescopeClient.fovMultiplier(tickDelta);
        if (multiplier != 1f) cir.setReturnValue(cir.getReturnValueD() * multiplier);
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void steveparty$telescopeHidesHand(CallbackInfo ci) {
        if (TelescopeClient.hidesHand()) ci.cancel();
    }
}
