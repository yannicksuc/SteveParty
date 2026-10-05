package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fr.lordfinn.steveparty.client.access.SquishStretchState;
import fr.lordfinn.steveparty.client.squish.SquishAnimations;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plays the client-only squish (tokenization) animation while the entity is drawn: scale steps and jelly squash and
 * stretch.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererSquishMixin {

    /** The scale the body is drawn at (LivingEntityRenderer#render), times the animation's current step. */
    @ModifyExpressionValue(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getScale()F"))
    private float steveparty$squishAnimation(float baseScale, @Local(argsOnly = true) LivingEntity entity,
                                             @Local(argsOnly = true, ordinal = 1) float tickDelta) {
        // Kept on the entity from one frame to the next: no deformation unless an animation sets one
        ((SquishStretchState) entity).steveparty$setStretch(1.0F);
        return baseScale * SquishAnimations.apply(entity, tickDelta);
    }

    /** Squash and stretch from the feet (after the base scale, before the body rotation), keeping the volume. */
    @Inject(method = "setupTransforms", at = @At("HEAD"))
    private void steveparty$squashAndStretch(LivingEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw,
                                             float tickDelta, float scale, CallbackInfo ci) {
        float stretch = ((SquishStretchState) entity).steveparty$getStretch();
        if (Math.abs(stretch - 1.0F) < 1.0E-3F || stretch <= 0) return;
        float width = (float) (1.0 / Math.sqrt(stretch));
        matrices.scale(width, stretch, width);
    }
}
