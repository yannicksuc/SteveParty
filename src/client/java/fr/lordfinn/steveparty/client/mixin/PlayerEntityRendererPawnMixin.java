package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.pawn.PawnPossessionClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player inside a pawn is not drawn at all: invisible, but their armour, held items and name would still show at
 * the pawn's feet.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererPawnMixin {

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$hideInsidePawn(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
                                           VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (player.isInvisible() && PawnPossessionClient.isHidden(player)) ci.cancel();
    }
}
