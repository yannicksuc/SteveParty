package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class StepControllerBlockEntityRenderer extends GeoBlockRenderer<StepControllerBlockEntity> {
    public StepControllerBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
        super(new DefaultedBlockGeoModel<>(Identifier.of(Steveparty.MOD_ID, "step_controller")));
    }

    @Override
    public @Nullable RenderLayer getRenderType(StepControllerBlockEntity animatable, Identifier texture, @Nullable VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityTranslucent(getTextureLocation(animatable));
    }
}
