package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.LootingBoxBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class LootingBoxBlockEntityRenderer extends GeoBlockRenderer<LootingBoxBlockEntity> {
    public LootingBoxBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
        super(new DefaultedBlockGeoModel<>(Steveparty.id("looting_box")));
    }

    @Override
    public void render(LootingBoxBlockEntity animatable, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight, int packedOverlay) {
        //int light = 0xF000F0;
        super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }
}