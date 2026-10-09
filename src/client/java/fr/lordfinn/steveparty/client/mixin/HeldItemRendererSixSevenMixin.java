package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lordfinn.steveparty.client.entity.SixSevenClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * First person « 6-7 » ({@link SixSevenClient}): both hands go up and down in turn, whatever they hold; an empty off
 * hand shows (as a bare arm) to weigh with the other one.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererSixSevenMixin {
    @Shadow
    private void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress,
                                      float swingProgress, Arm arm) {
        throw new AssertionError();
    }

    @WrapOperation(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void steveparty$sixSevenHands(HeldItemRenderer renderer, AbstractClientPlayerEntity player, float tickDelta, float pitch,
                                          Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int light, Operation<Void> original) {
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float lift = SixSevenClient.handLift(player, arm == Arm.RIGHT ? 1f : -1f, swingProgress);
        if (lift == 0f) {
            original.call(renderer, player, tickDelta, pitch, hand, swingProgress, item, equipProgress, matrices, vertexConsumers, light);
            return;
        }
        matrices.push();
        matrices.translate(0f, lift, 0f);
        if (hand == Hand.OFF_HAND && item.isEmpty() && !player.isInvisible() && SixSevenClient.showsOffHand(player)) {
            renderArmHoldingItem(matrices, vertexConsumers, light, equipProgress, swingProgress, arm);
        } else {
            original.call(renderer, player, tickDelta, pitch, hand, swingProgress, item, equipProgress, matrices, vertexConsumers, light);
        }
        matrices.pop();
    }
}
