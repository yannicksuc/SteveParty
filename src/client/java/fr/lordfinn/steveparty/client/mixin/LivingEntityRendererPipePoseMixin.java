package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import fr.lordfinn.steveparty.client.pipe.PipeTravellerPose;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A traveller lying in a pipe ({@link PipeTravellerPose}): facing the pipe's frame (yaw 0), looking ahead, lying (not
 * sitting, not crouching), set on render's body yaw, head yaw and pitch before they are used.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererPipePoseMixin {
    @Shadow
    protected EntityModel<?> model;

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isInPose(Lnet/minecraft/entity/EntityPose;)Z", ordinal = 0))
    private void steveparty$lyingInPipe(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                        VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci,
                                        @Local(index = 7) LocalFloatRef bodyYaw, @Local(index = 9) LocalFloatRef headYaw,
                                        @Local(index = 10) LocalFloatRef pitch) {
        if (!PipeTravellerPose.lying(entity)) return;
        bodyYaw.set(0);
        headYaw.set(0);
        pitch.set(PipeTravellerPose.lyingPitch());
        if (model instanceof BipedEntityModel<?> biped) biped.sneaking = false;
    }
}
