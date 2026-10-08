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

/**
 * First person, carrying Glandouilles: the main hand is bare, low on its side, under the stack (whatever it holds); the
 * other hand is drawn as usual.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererGlandouilleCarryMixin {
    /** How far the main arm goes down (screen units). */
    private static final float DOWN = 0.12f;

    @Shadow
    private void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                      float equipProgress, float swingProgress, Arm arm) {
        throw new AssertionError();
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void steveparty$carryArms(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                      float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                      VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (hand != Hand.MAIN_HAND || !GlandouilleCarryClient.carrying(player)) return;
        ci.cancel();
        if (player.isInvisible()) return;
        matrices.push();
        matrices.translate(0f, -DOWN, 0f);
        renderArmHoldingItem(matrices, vertexConsumers, light, equipProgress, swingProgress, player.getMainArm());
        matrices.pop();
    }
}
