package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void rotateArms(PlayerEntityRenderState state, CallbackInfo ci) {
        if (state instanceof SmoothFlipState smooth) {
            float progress = smooth.getFlipProgress();
            if (progress > 0.001F) {
                PlayerEntityModel model = (PlayerEntityModel)(Object)this;

                // Rotate both arms 180° smoothly
                float armRotation = (float)Math.toRadians(180.0F * progress);

                model.rightArm.pitch += armRotation;
                model.leftArm.pitch += armRotation;
            }
        }
    }
}
