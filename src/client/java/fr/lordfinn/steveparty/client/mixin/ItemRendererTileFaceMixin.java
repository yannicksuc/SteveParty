package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.items.TileItemFace;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tiles: the face of the tile item (its cartridges' faces in turn, its look), in the model's space (after its display
 * transform, before it is popped): in hand, on others, in the GUI, dropped and in item frames.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererTileFaceMixin {

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private void steveparty$tileFace(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                                     VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        TileItemFace.render(stack, matrices, vertexConsumers, light);
    }
}
