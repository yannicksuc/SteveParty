package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.items.custom.BoxCostumeItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No cape over a Box Costume (like with an elytra): it would go through the box's walls. */
@Mixin(CapeFeatureRenderer.class)
public abstract class CapeFeatureRendererBoxCostumeMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$noCapeInBox(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
                                        float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw,
                                        float headPitch, CallbackInfo ci) {
        if (!BoxCostumeItem.getWorn(player).isEmpty()) ci.cancel();
    }
}
