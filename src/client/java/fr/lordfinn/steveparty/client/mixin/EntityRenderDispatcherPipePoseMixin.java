package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.lordfinn.steveparty.client.pipe.PipeTravellerPose;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A traveller in a pipe is drawn lying along it, squashed and stretched, without a shadow ({@link PipeTravellerPose}). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherPipePoseMixin {
    @WrapOperation(method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/EntityRenderer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void steveparty$pipePose(EntityRenderer<?, ?> renderer, EntityRenderState state, MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                     Operation<Void> original, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true) float tickDelta) {
        boolean pushed = PipeTravellerPose.push(renderer, entity, state, tickDelta, matrices);
        try {
            original.call(renderer, state, matrices, consumers, light);
        } finally {
            PipeTravellerPose.end();
            if (pushed) matrices.pop();
        }
    }

    @WrapOperation(method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/EntityRenderer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;getShadowRadius(Lnet/minecraft/client/render/entity/state/EntityRenderState;)F"))
    private float steveparty$noShadowInPipes(EntityRenderer<?, ?> renderer, EntityRenderState state, Operation<Float> original,
                                             @Local(argsOnly = true) Entity entity) {
        return PipeTravellerPose.castsShadow(entity) ? original.call(renderer, state) : 0;
    }
}
