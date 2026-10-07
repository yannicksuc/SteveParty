package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.FirstPersonArm;
import fr.lordfinn.steveparty.client.entity.GlandouilleCarryClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player carrying Glandouilles, seen from outside: both arms straight forward under the stack, the way he looks. */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelGlandouilleCarryMixin {
    /** Arms raised to just under horizontal, a little inward. */
    @Unique
    private static final float STEVEPARTY$CARRY_PITCH = -1.45F, STEVEPARTY$CARRY_INWARD = 0.12F;

    /** After the biped pose, before the sleeves copy it. */
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            shift = At.Shift.AFTER))
    private void steveparty$carryArms(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                      float headYaw, float headPitch, CallbackInfo ci) {
        if (FirstPersonArm.posing || !GlandouilleCarryClient.carrying(entity)) return;
        PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;
        // the stack is held where he looks: the arms turn with the head
        float turn = MathHelper.clamp(headYaw, -60f, 60f) * MathHelper.RADIANS_PER_DEGREE;
        model.rightArm.pitch = STEVEPARTY$CARRY_PITCH;
        model.leftArm.pitch = STEVEPARTY$CARRY_PITCH;
        model.rightArm.yaw = turn - STEVEPARTY$CARRY_INWARD;
        model.leftArm.yaw = turn + STEVEPARTY$CARRY_INWARD;
        model.rightArm.roll = 0F;
        model.leftArm.roll = 0F;
    }
}
