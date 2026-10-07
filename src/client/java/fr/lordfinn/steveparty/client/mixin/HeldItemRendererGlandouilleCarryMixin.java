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

/** First person, carrying Glandouilles: both bare arms forward and close together (whatever the hands hold), no item. */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererGlandouilleCarryMixin {
    /** How far each arm comes in toward the middle (screen units), and turns in (degrees). */
    private static final float INWARD = 0.14f, TURN_IN = 5f;

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
        // Closer together than the vanilla arms, each a little turned in, as around a box held in front
        float side = arm == Arm.RIGHT ? 1f : -1f;
        matrices.translate(-side * INWARD, 0f, 0f);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side * TURN_IN));
        renderArmHoldingItem(matrices, vertexConsumers, light, equipProgress, hand == Hand.MAIN_HAND ? swingProgress : 0f, arm);
        matrices.pop();
    }
}
