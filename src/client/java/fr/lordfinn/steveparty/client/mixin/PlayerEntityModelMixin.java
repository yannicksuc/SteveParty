package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import fr.lordfinn.steveparty.client.entity.FirstPersonArm;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {

    /**
     * 1.21.1 does not reset the model before posing it (1.21.3 does): what the player model mixins change and vanilla
     * does not set again (scales, leg pivots...) would stay on every player drawn after.
     */
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void steveparty$resetPose(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                      float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayerEntity)) return;
        PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
        model.head.resetTransform();
        model.hat.resetTransform();
        model.body.resetTransform();
        model.rightArm.resetTransform();
        model.leftArm.resetTransform();
        model.rightLeg.resetTransform();
        model.leftLeg.resetTransform();
    }

    /** After the biped pose, before the sleeves, trousers and jacket copy it. */
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            shift = At.Shift.AFTER))
    private void setGoalPoleFlipAngles(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                       float headYaw, float headPitch, CallbackInfo ci) {
        if (FirstPersonArm.posing) return;
        // (players only, as the player render state was: not the mobs using this model)
        if (entity instanceof AbstractClientPlayerEntity && entity instanceof SmoothFlipState smooth) {
            float progress = smooth.getFlipProgress();
            if (progress > 0.001F) {
                PlayerEntityModel model = (PlayerEntityModel)(Object)this;

                float armRotation = (float)Math.toRadians(180.0F * progress);

                model.rightArm.roll += armRotation;

                model.leftArm.roll -= (float) (armRotation / 2 - 0.2);
                model.rightArm.pivotY -= (float) (3.5 * progress);
                model.leftLeg.pitch += (float) (0.7 * progress);
                model.rightLeg.pitch += (float) (0.6 * progress);
                model.leftLeg.roll -= (float) (0.1 * progress);
                model.rightLeg.roll += (float) (0.3 * progress);
                model.body.pitch += (float) (0.1 * progress);

                if (entity.isSneaking()) {
                    model.leftLeg.pitch += (float) (0.7 * progress);
                    model.rightLeg.pitch += (float) (0.7 * progress);

                    model.leftLeg.roll -= (float) (0.4 * progress);
                    model.rightLeg.roll += (float) (0.3 * progress);

                    model.rightLeg.yaw -= (float) (0.5 * progress);
                    model.leftLeg.yaw += (float) (0.5 * progress);

                    model.leftLeg.pivotY += 1 * progress;
                    model.rightLeg.pivotY += 1 * progress;
                    model.leftLeg.pivotZ += 2 * progress;
                    model.rightLeg.pivotZ += 2 * progress;
                    model.leftLeg.pivotX += 1 * progress;
                    model.rightLeg.pivotX -= 1 * progress;
                }
            }

        }
    }
}
