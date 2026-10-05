package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.StencilHammerRenderState;
import fr.lordfinn.steveparty.client.entity.FirstPersonArm;
import fr.lordfinn.steveparty.client.hammer.StencilHammerStrikes;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third person Stencil Hammer strike: the arm goes up over the head, then smashes down in front. */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelStencilHammerMixin {

    /** After the biped pose, before the sleeves copy it. */
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            shift = At.Shift.AFTER))
    private void steveparty$hammerStrikeArm(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                            float headYaw, float headPitch, CallbackInfo ci) {
        if (FirstPersonArm.posing || !(entity instanceof StencilHammerRenderState hammer)) return;
        float t = hammer.steveparty$getHammerStrike();
        if (t < 0) return;
        PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
        ModelPart arm = hammer.steveparty$getHammerArm() == Arm.RIGHT ? model.rightArm : model.leftArm;
        arm.pitch = StencilHammerStrikes.armPitch(arm.pitch, t);
        arm.yaw = 0;
        arm.roll = 0;
    }
}
