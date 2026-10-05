package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import fr.lordfinn.steveparty.client.flip.GoalPoleFlipTracker;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererSmoothFlipMixin {

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void smoothFlip(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        // The progress itself is stored per entity (tick based, FPS independent) and only copied for this frame here
        boolean targetFlip = LivingEntityRenderer.shouldFlipUpsideDown(entity);
        ((SmoothFlipState) entity).setFlipProgress(GoalPoleFlipTracker.getModelProgress(entity, targetFlip, tickDelta));
    }
}
