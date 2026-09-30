package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.StencilHammerRenderState;
import fr.lordfinn.steveparty.client.hammer.StencilHammerStrikes;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third person Stencil Hammer strike: the arm goes up over the head, then smashes down in front. */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelStencilHammerMixin {

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void steveparty$hammerStrikeArm(PlayerEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof StencilHammerRenderState hammer)) return;
        float t = hammer.steveparty$getHammerStrike();
        if (t < 0) return;
        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        ModelPart arm = hammer.steveparty$getHammerArm() == Arm.RIGHT ? model.rightArm : model.leftArm;
        arm.pitch = StencilHammerStrikes.armPitch(arm.pitch, t);
        arm.yaw = 0;
        arm.roll = 0;
    }
}
