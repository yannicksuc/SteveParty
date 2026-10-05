package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import fr.lordfinn.steveparty.client.access.TelescopeRenderState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player at a Telescope's eyepiece ({@link PlayerEntityRendererTelescopeMixin}): his body turns to face the way the
 * tube points, his head straight on it, and he stands still (render's body yaw, head yaw and limb swing).
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererTelescopeMixin {
    @Unique
    private static final String RENDER = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isInPose(Lnet/minecraft/entity/EntityPose;)Z", ordinal = 0))
    private void steveparty$faceTheTube(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                        VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci,
                                        @Local(index = 7) LocalFloatRef bodyYaw, @Local(index = 9) LocalFloatRef headYaw) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;
        float ease = ((TelescopeRenderState) player).steveparty$telescopeEase();
        if (ease <= 0f) return;
        float look = player.getYaw(tickDelta);
        float turn = MathHelper.wrapDegrees(look - bodyYaw.get()) * ease;
        bodyYaw.set(bodyYaw.get() + turn);
        headYaw.set(MathHelper.wrapDegrees(look - bodyYaw.get()));
    }

    /** Standing still at the eyepiece. */
    @ModifyExpressionValue(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LimbAnimator;getSpeed(F)F"))
    private float steveparty$standStill(float limbDistance, @Local(argsOnly = true) LivingEntity entity) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return limbDistance;
        return limbDistance * (1f - ((TelescopeRenderState) player).steveparty$telescopeEase());
    }
}
