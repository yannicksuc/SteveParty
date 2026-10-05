package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.hammer.StencilHammerStrikes;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person Stencil Hammer strike: wind-up, smash onto the aimed point, squash on impact, recovery. */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererStencilHammerMixin {

    @Inject(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void steveparty$hammerStrike(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                         float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                         VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!(item.getItem() instanceof StencilGunItem)) return;
        float t = StencilHammerStrikes.strikeTicks(player.getId(), hand == Hand.MAIN_HAND, tickDelta);
        if (t < 0) return;
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        StencilHammerStrikes.applyFirstPerson(matrices, arm, t);
    }
}
