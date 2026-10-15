package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.parachute.ParachuteClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the parachute of whoever carries one, a player or any mob, over its hitbox (see {@link ParachuteClient}). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererParachuteMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void steveparty$renderParachute(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        ParachuteClient.render(entity, tickDelta, matrices, vertexConsumers);
    }
}
