package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.FirstPersonArm;
import fr.lordfinn.steveparty.client.entity.GlandouilleCarryClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player carrying Glandouilles, seen from outside: the main arm raised forward under the stack (drawn on it, see
 * GlandouilleInHand), swinging a little as he walks and up when he throws; the other one swings as usual.
 */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelGlandouilleCarryMixin {
    /** The main arm raised to a little under horizontal (the hand under the stack), turned a little outward. */
    @Unique
    private static final float STEVEPARTY$CARRY_PITCH = -1.1F, STEVEPARTY$CARRY_OUTWARD = 0.3F;

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
        boolean right = entity.getMainArm() == Arm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        float walk = MathHelper.cos(limbAngle * 0.6662F + (right ? MathHelper.PI : 0F)) * 0.3F * limbDistance;
        float swing = MathHelper.sin(MathHelper.sqrt(model.handSwingProgress) * MathHelper.PI) * 0.9F;
        arm.pitch = STEVEPARTY$CARRY_PITCH + walk - swing;
        arm.yaw = turn + (right ? -STEVEPARTY$CARRY_OUTWARD : STEVEPARTY$CARRY_OUTWARD);
        arm.roll = 0F;
    }
}
