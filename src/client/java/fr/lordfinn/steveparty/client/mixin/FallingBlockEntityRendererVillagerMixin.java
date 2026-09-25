package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fr.lordfinn.steveparty.blocks.custom.VillagerBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.client.render.entity.FallingBlockEntityRenderer;
import net.minecraft.client.render.entity.state.FallingBlockEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The villager block is drawn by its block entity renderer (its render type is INVISIBLE, see VillagerBlock), but
 * while it falls there is no block entity: the falling block draws its baked model, as before.
 */
@Mixin(FallingBlockEntityRenderer.class)
public class FallingBlockEntityRendererVillagerMixin {

    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/render/entity/state/FallingBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getRenderType()Lnet/minecraft/block/BlockRenderType;"))
    private BlockRenderType steveparty$drawFallingVillagerBlock(BlockRenderType renderType,
                                                                 @Local(argsOnly = true) FallingBlockEntityRenderState state) {
        return state.blockState.getBlock() instanceof VillagerBlock ? BlockRenderType.MODEL : renderType;
    }
}
