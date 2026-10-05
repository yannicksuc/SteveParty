package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererSetupTransformsMixin {

    @Inject(method = "setupTransforms", at = @At("HEAD"))
    private void applySmoothFlip(LivingEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta,
                                 float scale, CallbackInfo ci) {
        float progress = ((SmoothFlipState) entity).getFlipProgress();
        if (progress > 0.001F) {
            // Interpolate vertical translation
            float heightOffset = (entity.getHeight() * 1.275f) * progress;
            matrices.translate(0.0F, heightOffset, 0.0F);
            if (entity.isSneaking())
                matrices.translate(0.0F, +0.25F, 0.0F);

            // Compute player's facing yaw (bodyYaw is in degrees)
            float yawRad = (float) Math.toRadians(bodyYaw);

            // Local right vector = rotate (1, 0, 0) around Y by bodyYaw
            float rightX = (float) Math.cos(yawRad);
            float rightZ = (float) Math.sin(yawRad);

            // Rotation axis: player’s local right vector
            Vector3f flipAxis = new Vector3f(rightX, 0.0F, rightZ);

            // Apply rotation around that axis
            matrices.multiply(new Quaternionf().fromAxisAngleDeg(flipAxis, 180.0F * progress));
        }
    }

    /** While the smooth flip plays, the vanilla upside-down turn is not applied. */
    @WrapOperation(method = "setupTransforms", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;shouldFlipUpsideDown(Lnet/minecraft/entity/LivingEntity;)Z"))
    private boolean noVanillaFlip(LivingEntity entity, Operation<Boolean> original) {
        return ((SmoothFlipState) entity).getFlipProgress() <= 0.001F && original.call(entity);
    }

    /** The head's pitch, as the vanilla flip leaves it, turned over once more while the smooth flip plays. */
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isInPose(Lnet/minecraft/entity/EntityPose;)Z", ordinal = 0))
    private void flipPitch(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                           int light, CallbackInfo ci, @Local(index = 10) LocalFloatRef pitch) {
        if (((SmoothFlipState) entity).getFlipProgress() > 0.001F) pitch.set(-pitch.get());
    }
}
