package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.GlandouilleCarryClient;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person, carrying Glandouilles: both bare arms forward (whatever the hands hold), none of the items. */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererGlandouilleCarryMixin {
    @Shadow
    private void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                      float equipProgress, float swingProgress, Arm arm) {
        throw new AssertionError();
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void steveparty$carryArms(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                      float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                      VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!GlandouilleCarryClient.carrying(player)) return;
        ci.cancel();
        if (player.isInvisible()) return;
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        matrices.push();
        renderArmHoldingItem(matrices, vertexConsumers, light, equipProgress, hand == Hand.MAIN_HAND ? swingProgress : 0f, arm);
        matrices.pop();
    }
}
