package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeRenderer;
import fr.lordfinn.steveparty.items.custom.BoxCostumeItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cape of a player in a Box Costume: none while he hides in the box (sneaking); standing, it hangs outside the
 * box, against its back wall and under its back flap, instead of through the box's walls.
 */
@Mixin(CapeFeatureRenderer.class)
public abstract class CapeFeatureRendererBoxCostumeMixin {
    /**
     * From the cape's own place (2 px behind the body's centre, at the neck) to the back flap's hinge: the box's back
     * wall, 8 px from its centre widened by the waist fit, half a pixel out, and the hinge 1 px under the neck
     * (BoxCostumeRenderer: the flaps' hinges 14 px over the box's floor, itself 9.5 px over the feet).
     */
    @Unique
    private static final float STEVEPARTY$BACK = (8F * (1F + BoxCostumeRenderer.WAIST_WIDENING) + 0.5F - 2F) / 16F,
            STEVEPARTY$DOWN = 1F / 16F;

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$capeOutOfTheBox(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
                                            float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw,
                                            float headPitch, CallbackInfo ci) {
        if (BoxCostumeItem.getWorn(player).isEmpty()) return;
        if (BoxCostumeItem.isHiddenInBox(player)) {
            ci.cancel();
            return;
        }
        // Model space: y down, z towards the back
        matrices.translate(0F, STEVEPARTY$DOWN, STEVEPARTY$BACK);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;FFFFFF)V",
            at = @At("RETURN"))
    private void steveparty$capeBack(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
                                     float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw,
                                     float headPitch, CallbackInfo ci) {
        if (!BoxCostumeItem.getWorn(player).isEmpty() && !BoxCostumeItem.isHiddenInBox(player)) {
            matrices.translate(0F, -STEVEPARTY$DOWN, -STEVEPARTY$BACK);
        }
    }
}
