package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.BubbleFloatPose;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In a Bubbler's bubble, any rider's whole body bobs and leans slowly, weightless ({@link BubbleFloatPose#sway}). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererBubbleSwayMixin {
    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void steveparty$swayInBubble(LivingEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw,
                                         float tickDelta, float scale, CallbackInfo ci) {
        BubbleFloatPose.sway(entity, matrices, animationProgress);
    }
}
