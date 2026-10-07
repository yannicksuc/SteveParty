package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.flip.GoalPoleCameraRoll;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The view zooms out a little while it rolls over on a goal pole ({@link GoalPoleCameraRoll}). */
@Mixin(GameRenderer.class)
public class GameRendererFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void steveparty$goalPoleZoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        float factor = GoalPoleCameraRoll.fovFactor(tickDelta);
        if (factor != 1F) cir.setReturnValue(Math.min(170D, cir.getReturnValueD() * factor));
    }
}
