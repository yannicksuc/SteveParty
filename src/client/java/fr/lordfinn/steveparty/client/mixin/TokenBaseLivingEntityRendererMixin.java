package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.token.TokenBaseRenderState;
import fr.lordfinn.steveparty.client.token.TokenBaseRenderer;
import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
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
    private static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;"
            + "Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void steveparty$updateTokenBase(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        TokenBaseRenderState tokenState = (TokenBaseRenderState) state;
        if (TokenBase.isToken(entity)) {
            tokenState.steveparty$setTokenBase(true, TokenBaseRenderer.colorOf(entity), TokenBase.BASE_HEIGHT,
                    TokenBaseRenderer.radiusFor(entity.getWidth()));
        } else {
            tokenState.steveparty$setTokenBase(false, 0, 0.0F, 0.0F);
        }
    }

    @Inject(method = RENDER, at = @At("HEAD"))
    private void steveparty$renderTokenBase(LivingEntityRenderState state, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        TokenBaseRenderState tokenState = (TokenBaseRenderState) state;
        if (!tokenState.steveparty$isToken() || state.invisible) return;
        matrices.push();
        TokenBaseRenderer.render(matrices, vertexConsumers, light, tokenState.steveparty$getBaseColor(),
                tokenState.steveparty$getBaseRadius(), tokenState.steveparty$getBaseHeight());
        matrices.pop();
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupTransforms(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V"))
    private void steveparty$raiseTokenModel(LivingEntityRenderState state, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        TokenBaseRenderState tokenState = (TokenBaseRenderState) state;
        if (!tokenState.steveparty$isToken() || state.baseScale <= 1.0E-4F) return;
        // The vanilla upside-down (Dinnerbone) transform already uses state.height, base included
        boolean flipped = state.flipUpsideDown && state.deathTime <= 0.0F && !state.usingRiptide
                && !state.isInPose(EntityPose.SLEEPING);
        if (flipped) return;
        matrices.translate(0.0F, tokenState.steveparty$getBaseHeight() / state.baseScale, 0.0F);
    }
}
