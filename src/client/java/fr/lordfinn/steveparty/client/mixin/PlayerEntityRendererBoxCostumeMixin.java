package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeAnimatable;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the player's Box Costume (if worn) on him for this frame; drawn by {@link LivingEntityRendererBoxCostumeMixin}
 * (his name tag: EntityRendererNameTagMixin).
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererBoxCostumeMixin {

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void steveparty$boxCostume(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
                                       VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        BoxCostumeAnimatable box = BoxCostumeClient.boxOf(player);
        ((BoxCostumeRenderState) player).steveparty$setBoxCostume(box, BoxCostumeClient.isInsideBox(box));
    }
}
