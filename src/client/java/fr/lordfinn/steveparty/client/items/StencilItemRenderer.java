package fr.lordfinn.steveparty.client.items;

import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class StencilItemRenderer extends ItemRenderer {
    public StencilItemRenderer(BakedModelManager bakedModelManager, ItemColors colors, BuiltinModelItemRenderer builtinModelItemRenderer) {
        super(bakedModelManager, colors, builtinModelItemRenderer);
    }

    @Override
    public void renderItem(ItemStack stack, ModelTransformationMode transformationMode, int light, int overlay, MatrixStack matrices, VertexConsumerProvider vertexConsumers, @Nullable World world, int seed) {
        super.renderItem(stack, transformationMode, light, overlay, matrices, vertexConsumers, world, seed);

    }
}
