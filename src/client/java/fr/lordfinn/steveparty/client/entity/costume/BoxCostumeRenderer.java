package fr.lordfinn.steveparty.client.entity.costume;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.entity.BlockTexturedBones;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Set;

/**
 * Draws a Box Costume with the Hiding Trader's model and animations: only his box (walls and flaps with the faces of
 * the costume's block, arm holes and inner shadow with his texture), never the merchant himself. Drawn from the
 * wearer's feet, facing like his body.
 * <p>
 * Worn, the merchant's own lift of his box ({@code body} raised by 4 px when out) puts it around a player's waist;
 * the waist fit (widened, a bit lower) grows with that lift, so the box closed on the ground stays exactly a block.
 */
public class BoxCostumeRenderer extends GeoObjectRenderer<BoxCostumeAnimatable> {
    /** The merchant's own parts: never drawn. */
    private static final Set<String> MERCHANT_BONES = Set.of("head", "right_arm", "left_arm", "right_left", "left_leg", "peek_eyes");
    private static final String BODY_BONE = "body", BOX_BONE = "cube_box";
    /** The merchant lifts his box by 4 px when he is out. */
    private static final float MERCHANT_LIFT = 4.0F;
    /**
     * Fully worn: the box widened so the arms swing inside it, and 1.5 px lower than the merchant's (a player's waist
     * is lower than the top of the merchant's box), the shirt still showing above it.
     */
    private static final float WAIST_WIDENING = 0.25F, WAIST_DROP = 1.5F;

    private final BlockTexturedBones boxBones = new BlockTexturedBones();
    /** Scale of the waist fit: 1 for a player, 0 for the item icon (the merchant's box as is). */
    private float waistFit = 1.0F;

    public BoxCostumeRenderer() {
        super(new DefaultedEntityGeoModel<>(Steveparty.id("hiding_trader")));
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void renderForBone(MatrixStack poseStack, BoxCostumeAnimatable animatable, GeoBone bone, RenderLayer renderType,
                                      VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                                      int packedOverlay, int renderColor) {
                boxBones.renderBone(poseStack, bone, bufferSource, packedLight, renderColor, animatable.getBlock());
            }
        });
    }

    /** Draws the box from the current origin (the wearer's feet, rotated like his body). */
    public void renderBox(MatrixStack poseStack, BoxCostumeAnimatable animatable, VertexConsumerProvider bufferSource, int packedLight,
                          float partialTick, float waistFit) {
        this.animatable = animatable;
        this.waistFit = waistFit;
        defaultRender(poseStack, animatable, bufferSource, null, null, partialTick, packedLight);
    }

    @Override
    public void preRender(MatrixStack poseStack, BoxCostumeAnimatable animatable, BakedGeoModel model, @Nullable VertexConsumerProvider bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, renderColor);
        // GeoObjectRenderer centres objects on a block: ours is drawn from the wearer's feet
        poseStack.translate(-0.5F, -0.51F, -0.5F);
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, BoxCostumeAnimatable animatable, GeoBone bone, RenderLayer renderType,
                                  VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int renderColor) {
        if (MERCHANT_BONES.contains(bone.getName())) return;
        if (!BOX_BONE.equals(bone.getName()) || waistFit <= 0.0F) {
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, renderColor);
            return;
        }
        GeoBone body = bone.getParent();
        float worn = body != null && BODY_BONE.equals(body.getName())
                ? Math.clamp(body.getPosY() / MERCHANT_LIFT, 0.0F, 1.0F) * waistFit : 0.0F;
        poseStack.push();
        poseStack.translate(0.0F, -worn * WAIST_DROP / 16.0F, 0.0F);
        float widening = 1.0F + worn * WAIST_WIDENING;
        poseStack.scale(widening, 1.0F, widening);
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, renderColor);
        poseStack.pop();
    }

    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int renderColor) {
        // Box bones: drawn with the block's faces by the render layer
        if (BlockTexturedBones.isBoxBone(bone)) return;
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, renderColor);
    }
}
