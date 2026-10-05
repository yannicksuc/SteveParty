package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lordfinn.steveparty.client.entity.FirstPersonArm;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Flags the first person arm's posing ({@link FirstPersonArm}): the third person poses of the player model skip it. */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererFirstPersonArmMixin {
    @WrapOperation(method = "renderArm", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/PlayerEntityModel;setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V"))
    private void steveparty$firstPersonArm(PlayerEntityModel<?> model, LivingEntity player, float limbAngle, float limbDistance,
                                           float animationProgress, float headYaw, float headPitch, Operation<Void> original) {
        FirstPersonArm.posing = true;
        try {
            original.call(model, player, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        } finally {
            FirstPersonArm.posing = false;
        }
    }
}
