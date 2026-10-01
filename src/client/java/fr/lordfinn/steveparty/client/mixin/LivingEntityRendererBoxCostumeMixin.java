package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeAnimatable;
import fr.lordfinn.steveparty.client.entity.costume.BoxCostumeClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a Box Costume wearer's box. Once he is fully inside it (hidden, closed on the ground), only the box is drawn:
 * no player model, held items, armour nor name tag.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererBoxCostumeMixin {

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$renderBoxCostume(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             int light, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState player) || !(state instanceof BoxCostumeRenderState costume)) return;
        BoxCostumeAnimatable box = costume.steveparty$getBoxCostume();
        if (box == null || state.invisible) return;
        // The model is drawn moved by the renderer's position offset (lowered while sneaking): the box stays on the ground
        @SuppressWarnings({"rawtypes", "unchecked"})
        Vec3d offset = ((LivingEntityRenderer) (Object) this).getPositionOffset(state);
        BoxCostumeClient.renderWorn(player, box, offset, matrices, vertexConsumers, light);
        if (costume.steveparty$isInBox()) ci.cancel();
    }
}
