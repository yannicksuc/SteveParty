package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.pawn.PawnPossessionClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inside a pawn, the first person view is the statue's: no hands nor held items in front of it. */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererPawnMixin {

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$noHandsInsidePawn(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
                                              ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (PawnPossessionClient.isLocalPlayerInside(player)) ci.cancel();
    }
}
