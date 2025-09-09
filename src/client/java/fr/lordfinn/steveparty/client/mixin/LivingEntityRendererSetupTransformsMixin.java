package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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

            // Compute player's facing yaw (bodyYaw is in degrees)
            float yawRad = (float) Math.toRadians(state.bodyYaw);

            // Local right vector = rotate (1, 0, 0) around Y by bodyYaw
            float rightX = (float) Math.cos(yawRad);
            float rightZ = (float) Math.sin(yawRad);

            // Rotation axis: player’s local right vector
            Vector3f flipAxis = new Vector3f(rightX, 0.0F, rightZ);

            // Apply rotation around that axis
            matrices.multiply(new Quaternionf().fromAxisAngleDeg(flipAxis, 180.0F * progress));

            // Adjust state (optional, depending on how you handle mirrored rotations)
            state.pitch *= -1.0F;
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
