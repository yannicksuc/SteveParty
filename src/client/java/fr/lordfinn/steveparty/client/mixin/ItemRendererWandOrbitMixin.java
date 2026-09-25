package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.tokenspell.WandOrbit;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kamek's shapes orbiting the Tokenizer Wand's jewel, drawn with the held wand (first and third person). */
@Mixin(ItemRenderer.class)
public class ItemRendererWandOrbitMixin {

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("TAIL"))
    private void steveparty$wandOrbit(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                                      VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model,
                                      CallbackInfo ci) {
        if (stack.getItem() instanceof TokenizerWandItem && WandOrbit.shows(mode)) {
            WandOrbit.render(mode, leftHanded, matrices, vertexConsumers, model);
        }
    }
}
