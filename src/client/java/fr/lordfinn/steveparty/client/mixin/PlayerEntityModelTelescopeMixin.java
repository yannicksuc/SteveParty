package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.TelescopeRenderState;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player looking through a Telescope, seen from outside: bent at the hips towards the eyepiece (the sneaking bend,
 * as much as its height asks for), his right hand up on the tube if it is empty.
 */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelTelescopeMixin {
    /** The sneaking pose of the player model, whole (model px and radians). */
    @Unique
    private static final float STEVEPARTY$BODY_PITCH = 0.5F, STEVEPARTY$ARM_PITCH = 0.4F, STEVEPARTY$LEG_BACK = 4.0F,
            STEVEPARTY$HEAD_DOWN = 4.2F, STEVEPARTY$BODY_DOWN = 3.2F;
    /** The right arm holding the tube: forward, raised with it, a little inward. */
    @Unique
    private static final float STEVEPARTY$HOLD_PITCH = -1.75F, STEVEPARTY$HOLD_WITH_TUBE = 0.75F, STEVEPARTY$HOLD_YAW = -0.3F;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void steveparty$atTheEyepiece(PlayerEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof TelescopeRenderState telescope)) return;
        float ease = telescope.steveparty$telescopeEase();
        if (ease <= 0f) return;
        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        float bend = telescope.steveparty$telescopeBend() * ease;
        model.body.pitch += STEVEPARTY$BODY_PITCH * bend;
        model.leftArm.pitch += STEVEPARTY$ARM_PITCH * bend;
        model.rightLeg.pivotZ += STEVEPARTY$LEG_BACK * bend;
        model.leftLeg.pivotZ += STEVEPARTY$LEG_BACK * bend;
        model.head.pivotY += STEVEPARTY$HEAD_DOWN * bend;
        model.body.pivotY += STEVEPARTY$BODY_DOWN * bend;
        model.leftArm.pivotY += STEVEPARTY$BODY_DOWN * bend;
        model.rightArm.pivotY += STEVEPARTY$BODY_DOWN * bend;
        // an empty right hand goes up on the tube
        if (Float.isNaN(telescope.steveparty$telescopePitch())) {
            model.rightArm.pitch += STEVEPARTY$ARM_PITCH * bend;
            return;
        }
        float hold = STEVEPARTY$HOLD_PITCH + telescope.steveparty$telescopePitch() * MathHelper.RADIANS_PER_DEGREE * STEVEPARTY$HOLD_WITH_TUBE;
        model.rightArm.pitch = MathHelper.lerp(ease, model.rightArm.pitch, hold);
        model.rightArm.yaw = MathHelper.lerp(ease, model.rightArm.yaw, STEVEPARTY$HOLD_YAW);
        model.rightArm.roll = MathHelper.lerp(ease, model.rightArm.roll, 0F);
    }
}
