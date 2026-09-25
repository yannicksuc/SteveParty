package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.tokenspell.TokenSpellHand;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
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

/** Token spell: the first person hand holding the Tokenizer Wand follows the cursor while drawing on the stencil. */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererTokenSpellMixin {

    @Inject(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", ordinal = 0, shift = At.Shift.AFTER))
    private void steveparty$followTokenSpellCursor(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                                   float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                                   VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!TokenSpellHand.isActive() || !(item.getItem() instanceof TokenizerWandItem)) return;
        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        TokenSpellHand.apply(matrices, arm);
    }
}
