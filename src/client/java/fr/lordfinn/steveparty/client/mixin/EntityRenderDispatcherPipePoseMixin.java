package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lordfinn.steveparty.client.pipe.PipeTravellerPose;
import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A traveller in a pipe is drawn lying along it, squashed and stretched, without a shadow ({@link PipeTravellerPose}).
 * Drawn there and then: the translucent layers of its model (a player's skin) would otherwise wait for the end of the
 * frame, after the translucent blocks, and the stained glass of its pipe (or any tinted glass in front) would hide it.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherPipePoseMixin {
    @WrapOperation(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void steveparty$pipePose(EntityRenderer<Entity> renderer, Entity entity, float yaw, float tickDelta, MatrixStack matrices,
                                     VertexConsumerProvider consumers, int light, Operation<Void> original) {
        boolean pushed = PipeTravellerPose.push(renderer, entity, tickDelta, matrices);
        try {
            original.call(renderer, entity, yaw, tickDelta, matrices, consumers, light);
            if (entity.getVehicle() instanceof PipeCarrierEntity && consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw();
        } finally {
            PipeTravellerPose.end();
            if (pushed) matrices.pop();
        }
    }

    @WrapOperation(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;getShadowRadius(Lnet/minecraft/entity/Entity;)F"))
    private float steveparty$noShadowInPipes(EntityRenderer<Entity> renderer, Entity entity, Operation<Float> original) {
        return PipeTravellerPose.castsShadow(entity) ? original.call(renderer, entity) : 0;
    }
}
