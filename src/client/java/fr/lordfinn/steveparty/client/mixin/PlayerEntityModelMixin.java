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

                model.leftArm.roll -= (float) (armRotation / 2 - 0.2);
                model.rightArm.pivotY -= (float) (3.5 * progress);
                model.leftLeg.pitch += (float) (0.7 * progress);
                model.rightLeg.pitch += (float) (0.6 * progress);
                model.leftLeg.roll -= (float) (0.1 * progress);
                model.rightLeg.roll += (float) (0.3 * progress);
                model.body.pitch += (float) (0.1 * progress);

                if (state.sneaking) {
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
