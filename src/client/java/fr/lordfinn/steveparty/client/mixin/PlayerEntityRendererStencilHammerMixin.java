package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.StencilHammerRenderState;
import fr.lordfinn.steveparty.client.hammer.StencilHammerStrikes;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the player's Stencil Hammer strike (if any) on him for this frame, for the arm pose. */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererStencilHammerMixin {

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void steveparty$hammerStrike(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
                                         VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        float ticks = StencilHammerStrikes.strikeTicks(player.getId(), tickDelta);
        boolean mainHand = StencilHammerStrikes.strikesWithMainHand(player.getId());
        ((StencilHammerRenderState) player).steveparty$setHammerStrike(ticks, mainHand ? player.getMainArm() : player.getMainArm().getOpposite());
    }
}
