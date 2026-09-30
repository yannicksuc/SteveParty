package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.hammer.StencilHammerFace;
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

/**
 * Stencil Hammer: its selected stencil drawn on the front drum face, in the model's space (after its display
 * transform, before it is popped): in hand, on others, in the GUI, dropped and in item frames.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererStencilHammerFaceMixin {

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;ZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private void steveparty$stencilHammerFace(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                                              VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model,
                                              boolean useInventoryModel, float z, CallbackInfo ci) {
        StencilHammerFace.render(stack, matrices, vertexConsumers, light, overlay);
    }
}
