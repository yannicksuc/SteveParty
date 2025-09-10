package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void setGoalPoleFlipAngles(PlayerEntityRenderState state, CallbackInfo ci) {
        if (state instanceof SmoothFlipState smooth) {
            float progress = smooth.getFlipProgress();
            if (progress > 0.001F) {
                PlayerEntityModel model = (PlayerEntityModel)(Object)this;

                float armRotation = (float)Math.toRadians(180.0F * progress);

                model.rightArm.roll += armRotation;
                model.rightArm.yaw += 0;
                model.rightArm.pitch += 0;
                model.leftArm.roll -= armRotation / 2 - 0.2;
                model.rightArm.pivotY -= 3.5;
                model.leftLeg.pitch += 0.7;
                model.rightLeg.pitch += 0.6;
                model.leftLeg.roll -= 0.1;
                model.rightLeg.roll += 0.3;
                model.body.pitch += 0.1;
            }

        }
    }
}
