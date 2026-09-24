package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.StencilMakerBlockEntity;
import fr.lordfinn.steveparty.client.utils.StencilRenderUtils;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

public class StencilMakerBlockEntityRenderer implements BlockEntityRenderer<StencilMakerBlockEntity> {

    public StencilMakerBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
    }

    @Override
    public void render(StencilMakerBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ItemStack stencil = entity.getStencil();
        if (stencil == null || stencil.isEmpty()) return;
        Identifier texture = StencilResourceManager.getTexture(StencilItem.getShape(stencil), StencilResourceManager.Kind.METAL);
        if (texture == null) return;
        StencilRenderUtils.renderSymbol(
                matrices,
                vertexConsumers,
                light,
                overlay,
                texture,
                StencilRenderUtils.WHITE,
                false,
                (stack) -> stack.translate(0, 6.5f / 16f, 0)
        );
    }
}
