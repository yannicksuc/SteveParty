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
 * Draws a Box Costume with the Boxed Trader's model and animations: only his box (walls and flaps with the faces of
 * the costume's block, arm holes and inner shadow with his texture), never the merchant himself. Drawn from the
 * wearer's feet, facing like his body.
 * <p>
 * Worn, the merchant's own lift of his box ({@code body} raised by 4 px when out) carries it up a player's body; the
 * fit to a player (widened, higher: only the head shows above the rim) grows with that
 * lift, so the box closed on the ground stays exactly a block. The wearer's limbs follow the box (see
 * PlayerEntityModelBoxCostumeMixin): arms through its arm holes, short legs under it, like the merchant's.
 */
public class BoxCostumeRenderer extends GeoObjectRenderer<BoxCostumeAnimatable> {
    /** The merchant's own parts: never drawn. */
    private static final Set<String> MERCHANT_BONES = Set.of("head", "right_arm", "left_arm", "right_left", "left_leg", "peek_eyes");
    private static final String BODY_BONE = "body", BOX_BONE = "cube_box";
    private static final String ARM_HOLE_BONE = "arm_hole_right";
    private static final String FLAP_BONE_PREFIX = "cube_flap";
    /** Flaps of the item icon: 115 degrees from closed (the merchant's hang at 200). */
    private static final float ICON_FLAP_ANGLE = (float) Math.toRadians(115.0);
    /** The merchant lifts his box by 4 px when he is out. */
    public static final float MERCHANT_LIFT = 4.0F;
    /**
     * Fully worn: the box widened around a player's body, and 5.5 px higher than the merchant's (floor at 9.5 px, rim
     * at 24.5 px: over the chin, the whole torso inside).
     */
    public static final float WAIST_WIDENING = 0.25F, WAIST_LIFT = 5.5F;

    private final BlockTexturedBones boxBones = new BlockTexturedBones();
    /** Scale of the waist fit: 1 for a player, 0 for the item icon (the merchant's box as is). */
    private float waistFit = 1.0F;

    public BoxCostumeRenderer() {
        super(new DefaultedEntityGeoModel<>(Steveparty.id("boxed_trader")));
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void renderForBone(MatrixStack poseStack, BoxCostumeAnimatable animatable, GeoBone bone, RenderLayer renderType,
                                      VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                                      int packedOverlay) {
                int renderColor = getRenderColor(animatable, partialTick, packedLight).getColor();
                boxBones.renderBone(poseStack, bone, bufferSource, packedLight, renderColor, animatable.getBlock(), animatable.getPos());
            }
        });
    }

    /** Draws the box from the current origin (the wearer's feet, rotated like his body). */
    public void renderBox(MatrixStack poseStack, BoxCostumeAnimatable animatable, VertexConsumerProvider bufferSource, int packedLight,
                          float partialTick, float waistFit) {
        this.animatable = animatable;
        this.waistFit = waistFit;
        defaultRender(poseStack, animatable, bufferSource, null, null, 0.0F, partialTick, packedLight);
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
        if (waistFit <= 0.0F && bone.getName().startsWith(FLAP_BONE_PREFIX)) {
            // The item icon: flaps standing open instead of hanging wide, so the whole box fits a slot
            float rotX = bone.getRotX(), rotZ = bone.getRotZ();
            if (rotX != 0.0F) bone.setRotX(Math.copySign(ICON_FLAP_ANGLE, rotX));
            if (rotZ != 0.0F) bone.setRotZ(Math.copySign(ICON_FLAP_ANGLE, rotZ));
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, renderColor);
            bone.setRotX(rotX);
            bone.setRotZ(rotZ);
            return;
        }
        if (!BOX_BONE.equals(bone.getName()) || waistFit <= 0.0F) {
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, renderColor);
            return;
        }
        GeoBone body = bone.getParent();
        float worn = body != null && BODY_BONE.equals(body.getName())
                ? Math.clamp(body.getPosY() / MERCHANT_LIFT, 0.0F, 1.0F) * waistFit : 0.0F;
        poseStack.push();
        // For the wearer's limbs: how far up the box is, and how open its arm holes are
        animatable.lift = worn;
        animatable.armHole = bone.getChildBones().stream().filter(child -> ARM_HOLE_BONE.equals(child.getName()))
                .findFirst().map(GeoBone::getScaleY).orElse(worn);
        poseStack.translate(0.0F, worn * WAIST_LIFT / 16.0F, 0.0F);
        float widening = 1.0F + worn * WAIST_WIDENING;
        poseStack.scale(widening, 1.0F, widening);
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, renderColor);
        poseStack.pop();
    }

    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int renderColor) {
        // Box bones: drawn with the block's faces by the render layer
        if (BlockTexturedBones.isBoxBone(bone) || BlockTexturedBones.isHiddenInside(bone, this.animatable.getBlock())) return;
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, renderColor);
    }
}
