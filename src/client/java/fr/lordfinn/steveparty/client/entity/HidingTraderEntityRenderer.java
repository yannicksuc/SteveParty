package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import static fr.lordfinn.steveparty.client.entity.HidingTraderEntityRenderLayer.CUBE_BONE_ID;


public class HidingTraderEntityRenderer extends GeoEntityRenderer<HidingTraderEntity> {

    public HidingTraderEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Identifier.of(Steveparty.MOD_ID, "hiding_trader")));
        addRenderLayer(new HidingTraderEntityRenderLayer(this));
    }

    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int renderColor) {
        if (bone.getName().startsWith(CUBE_BONE_ID)) return;
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, renderColor);
    }
}