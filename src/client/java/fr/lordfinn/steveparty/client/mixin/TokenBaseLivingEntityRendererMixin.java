package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import fr.lordfinn.steveparty.client.token.TokenBaseRenderState;
import fr.lordfinn.steveparty.client.token.TokenBaseRenderer;
import fr.lordfinn.steveparty.entities.TokenBase;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tokens drawn by a vanilla living entity renderer stand on their coloured base (see {@link TokenBaseRenderer}).
 * <p>
 * In {@code LivingEntityRenderer#render}: the base is drawn first at the feet, in its own push / pop; the model is
 * raised by the base height right before {@code setupTransforms} (inside the model's push / pop, after the
 * {@code baseScale} scaling, so the offset is divided by it). The name tag, drawn afterwards by
 * {@code EntityRenderer#render} outside that push / pop, is already raised by the token's dimensions: no double
 * offset.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class TokenBaseLivingEntityRendererMixin {
    @Unique
    private static final String RENDER = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void steveparty$renderTokenBase(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        TokenBaseRenderState tokenState = (TokenBaseRenderState) entity;
        if (TokenBase.isToken(entity)) {
            tokenState.steveparty$setTokenBase(true, TokenBaseRenderer.colorOf(entity), TokenBase.BASE_HEIGHT,
                    TokenBaseRenderer.radiusFor(entity.getWidth()));
        } else {
            tokenState.steveparty$setTokenBase(false, 0, 0.0F, 0.0F);
        }
        if (!tokenState.steveparty$isToken() || entity.isInvisible()) return;
        matrices.push();
        TokenBaseRenderer.render(matrices, vertexConsumers, light, tokenState.steveparty$getBaseColor(),
                tokenState.steveparty$getBaseRadius(), tokenState.steveparty$getBaseHeight());
        matrices.pop();
    }

    /** A still pawn: head in line with the body (head yaw and pitch, render's locals, before they are used). */
    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isInPose(Lnet/minecraft/entity/EntityPose;)Z", ordinal = 0))
    private void steveparty$stillPawnHead(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci,
                                          @Local(index = 9) LocalFloatRef headYaw, @Local(index = 10) LocalFloatRef pitch) {
        if (!((TokenBaseRenderState) entity).steveparty$isToken()) return;
        headYaw.set(0.0F);
        pitch.set(0.0F);
    }

    /** ...every idle animation driven by time (tails, wings, tentacles, floating...) frozen on the frame it became a token... */
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getAnimationProgress(Lnet/minecraft/entity/LivingEntity;F)F"))
    private float steveparty$stillPawnAge(float animationProgress, @Local(argsOnly = true) LivingEntity entity) {
        if (!((TokenBaseRenderState) entity).steveparty$isToken()) return animationProgress;
        int pawnAge = ((TokenizedEntityInterface) entity).steveparty$getPawnAge();
        return pawnAge >= 0 ? pawnAge : animationProgress;
    }

    /** ...and legs at rest while the board slides it. */
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LimbAnimator;getSpeed(F)F"))
    private float steveparty$stillPawnLegs(float limbDistance, @Local(argsOnly = true) LivingEntity entity) {
        return ((TokenBaseRenderState) entity).steveparty$isToken() ? 0.0F : limbDistance;
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V"))
    private void steveparty$raiseTokenModel(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci,
                                            @Local(index = 11) float baseScale) {
        TokenBaseRenderState tokenState = (TokenBaseRenderState) entity;
        if (!tokenState.steveparty$isToken() || baseScale <= 1.0E-4F) return;
        // The vanilla upside-down (Dinnerbone) transform already uses the entity's height, base included
        boolean flipped = LivingEntityRenderer.shouldFlipUpsideDown(entity) && entity.deathTime <= 0 && !entity.isUsingRiptide()
                && !entity.isInPose(EntityPose.SLEEPING);
        if (flipped) return;
        matrices.translate(0.0F, tokenState.steveparty$getBaseHeight() / baseScale, 0.0F);
    }
}
