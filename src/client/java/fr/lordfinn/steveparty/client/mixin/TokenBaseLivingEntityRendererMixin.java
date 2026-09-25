package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.token.TokenBaseRenderState;
import fr.lordfinn.steveparty.client.token.TokenBaseRenderer;
import fr.lordfinn.steveparty.client.token.TokenFootAnchor;
import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
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
 * <p>
 * Right before {@code model.setAngles} (model space: after the body yaw, the flip and the model scale), the model and
 * its feature layers are shifted horizontally by minus the {@link TokenFootAnchor}, so that the base is centred
 * between the mob's lowest limbs. The vertical position is untouched.
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

    @Inject(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/client/render/entity/state/EntityRenderState;)V"))
    private void steveparty$centreTokenOnFeet(LivingEntityRenderState state, MatrixStack matrices,
                                              VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!((TokenBaseRenderState) state).steveparty$isToken()) return;
        EntityModel<?> model = ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        TokenFootAnchor.Anchor anchor = TokenFootAnchor.cached(model);
        if (anchor == null) anchor = steveparty$computeAnchor(model, state);
        if (!anchor.isZero()) matrices.translate(-anchor.x(), 0.0F, -anchor.z());
    }

    /**
     * Foot anchor of {@code model} in a neutral pose: its angles for a fresh render state (standing still, age 0,
     * looking ahead), so that the poses set in code (spider legs, squid tentacles...) count. Falls back to the
     * model's default pose if it can't handle a blank state. The real setAngles, right after, poses it again.
     */
    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private TokenFootAnchor.Anchor steveparty$computeAnchor(EntityModel model, LivingEntityRenderState state) {
        try {
            LivingEntityRenderState neutral = (LivingEntityRenderState) ((EntityRenderer) (Object) this).createRenderState();
            neutral.baby = state.baby;
            neutral.ageScale = state.ageScale;
            neutral.baseScale = state.baseScale;
            model.setAngles(neutral);
        } catch (RuntimeException e) {
            model.resetTransforms();
        }
        return TokenFootAnchor.computeVanilla(model);
    }
}
