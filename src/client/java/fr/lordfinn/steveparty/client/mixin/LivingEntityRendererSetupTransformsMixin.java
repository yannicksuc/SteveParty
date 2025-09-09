package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererSetupTransformsMixin<S extends LivingEntityRenderState & SmoothFlipState> {

    @Inject(method = "setupTransforms", at = @At("HEAD"), cancellable = true)
    private void applySmoothFlip(S state, MatrixStack matrices, float animationProgress, float bodyYaw, CallbackInfo ci) {
        float progress = state.getFlipProgress();
        if (progress > 0.001F) {
            // Interpolate vertical translation
            float heightOffset = (state.height * 1.2f) * progress;
            matrices.translate(0.0F, heightOffset, 0.0F);

            // Interpolate rotation around Z
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F * progress));
            state.pitch *= -1.0F;
            state.yawDegrees *= -1.0F;
            state.flipUpsideDown = false;
        }
    }
    @Inject(method = "setupTransforms", at = @At("TAIL"), cancellable = true)
    private void resetFlipUpsideDown(S state, MatrixStack matrices, float animationProgress, float bodyYaw, CallbackInfo ci) {
        float progress = state.getFlipProgress();
        if (progress > 0.001F) {
        }
    }
}
