package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws the merchant's box bones with the faces of his block (see {@link BlockTexturedBones}), or on purpose with the
 * purple and black missing texture once the look of his box was taken with shears.
 */
public class HidingTraderEntityRenderLayer extends GeoRenderLayer<HidingTraderEntity> {
    public static final String CUBE_BONE_ID = BlockTexturedBones.CUBE_BONE_PREFIX;
    private final BlockTexturedBones boxBones = new BlockTexturedBones();

    public HidingTraderEntityRenderLayer(HidingTraderEntityRenderer renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(MatrixStack poseStack, HidingTraderEntity animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        if (!BlockTexturedBones.isBoxBone(bone)) return;
        // GeckoLib calls this with the pose stack already transformed for this bone (position, pivot, rotation,
        // scale), children bones get their own call: applying the bone transform again here would double the
        // flap rotations and every animated offset of the box.
        boxBones.renderBone(poseStack, bone, bufferSource, packedLight, renderer.getRenderColor(animatable, partialTick, packedLight).getColor(),
                animatable.isBoxGlitched() ? null : animatable.getBlockState(), animatable.getBlockPos());
    }
}
