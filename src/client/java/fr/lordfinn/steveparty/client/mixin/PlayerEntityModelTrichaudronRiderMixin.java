package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.FirstPersonArm;
import fr.lordfinn.steveparty.client.entity.TrichaudronRiderClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Trichaudron's rider, seen from outside: both arms held forward, hands together on the reins. */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelTrichaudronRiderMixin {
    @Unique
    private static final float STEVEPARTY$REINS_PITCH = -1.05F, STEVEPARTY$REINS_INWARD = 0.25F;

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            shift = At.Shift.AFTER))
    private void steveparty$reinsArms(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                      float headYaw, float headPitch, CallbackInfo ci) {
        if (FirstPersonArm.posing || !TrichaudronRiderClient.riding(entity)) return;
        PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
        model.rightArm.pitch = STEVEPARTY$REINS_PITCH;
        model.rightArm.yaw = -STEVEPARTY$REINS_INWARD;
        model.rightArm.roll = 0F;
        model.leftArm.pitch = STEVEPARTY$REINS_PITCH;
        model.leftArm.yaw = STEVEPARTY$REINS_INWARD;
        model.leftArm.roll = 0F;
    }
}
