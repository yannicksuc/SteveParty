package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import fr.lordfinn.steveparty.client.token.MobPoses;
import fr.lordfinn.steveparty.client.token.TokenBaseRenderState;
import fr.lordfinn.steveparty.client.token.TokenBaseRenderer;
import fr.lordfinn.steveparty.entities.TokenBase;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
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
            tokenState.steveparty$setTokenBase(true, TokenBaseRenderer.colorOf(entity), TokenBase.baseHeight(entity),
                    TokenBaseRenderer.radiusFor(entity));
        } else {
            tokenState.steveparty$setTokenBase(false, 0, 0.0F, 0.0F);
        }
        // A posed mob pawn: its pose's states for this frame (put back at the end of it)
        MobPoses.Pose pose = tokenState.steveparty$isToken() && !(entity instanceof PlayerPawnEntity)
                ? MobPoses.of(entity, ((LivingEntityRenderer<?, ?>) (Object) this).getModel()) : null;
        tokenState.steveparty$setPoseFrame(pose == null ? null : MobPoses.begin(entity, pose));
        if (!tokenState.steveparty$isToken() || entity.isInvisible()) return;
        matrices.push();
        TokenBaseRenderer.render(matrices, vertexConsumers, light, tokenState.steveparty$getBaseColor(),
                tokenState.steveparty$getBaseRadius(), tokenState.steveparty$getBaseHeight());
        matrices.pop();
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void steveparty$endPose(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        TokenBaseRenderState tokenState = (TokenBaseRenderState) entity;
        MobPoses.Frame frame = tokenState.steveparty$getPoseFrame();
        if (frame == null) return;
        MobPoses.end(entity, frame);
        tokenState.steveparty$setPoseFrame(null);
    }

    /**
     * A still pawn: head in line with the body (head yaw and pitch, render's locals, before they are used); a posed
     * one: where its pose looks.
     */
    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isInPose(Lnet/minecraft/entity/EntityPose;)Z", ordinal = 0))
    private void steveparty$stillPawnHead(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci,
                                          @Local(index = 9) LocalFloatRef headYaw, @Local(index = 10) LocalFloatRef pitch) {
        if (!((TokenBaseRenderState) entity).steveparty$isToken()) return;
        MobPoses.Frame frame = ((TokenBaseRenderState) entity).steveparty$getPoseFrame();
        headYaw.set(frame == null ? 0.0F : frame.pose().headYaw());
        pitch.set(frame == null ? 0.0F : frame.pose().headPitch());
    }

    /** ...every idle animation driven by time (tails, wings, tentacles, floating...) frozen on the frame it became a token... */
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getAnimationProgress(Lnet/minecraft/entity/LivingEntity;F)F"))
    private float steveparty$stillPawnAge(float animationProgress, @Local(argsOnly = true) LivingEntity entity) {
        if (!((TokenBaseRenderState) entity).steveparty$isToken()) return animationProgress;
        MobPoses.Frame frame = ((TokenBaseRenderState) entity).steveparty$getPoseFrame();
        if (frame != null) return frame.pose().age();
        int pawnAge = ((TokenizedEntityInterface) entity).steveparty$getPawnAge();
        return pawnAge >= 0 ? pawnAge : animationProgress;
    }

    /** ...and legs at rest while the board slides it. */
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LimbAnimator;getSpeed(F)F"))
    private float steveparty$stillPawnLegs(float limbDistance, @Local(argsOnly = true) LivingEntity entity) {
        if (!((TokenBaseRenderState) entity).steveparty$isToken()) return limbDistance;
        MobPoses.Frame frame = ((TokenBaseRenderState) entity).steveparty$getPoseFrame();
        return frame == null ? 0.0F : frame.pose().limbDistance();
    }

    /** A posed pawn: where its legs are in their stride... */
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LimbAnimator;getPos(F)F"))
    private float steveparty$posedLimbs(float limbAngle, @Local(argsOnly = true) LivingEntity entity) {
        MobPoses.Frame frame = ((TokenBaseRenderState) entity).steveparty$getPoseFrame();
        return frame == null ? limbAngle : frame.pose().limbAngle();
    }

    /** ...and its arm swing. */
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getHandSwingProgress(Lnet/minecraft/entity/LivingEntity;F)F"))
    private float steveparty$posedSwing(float swing, @Local(argsOnly = true) LivingEntity entity) {
        MobPoses.Frame frame = ((TokenBaseRenderState) entity).steveparty$getPoseFrame();
        return frame == null ? swing : frame.pose().swing();
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
