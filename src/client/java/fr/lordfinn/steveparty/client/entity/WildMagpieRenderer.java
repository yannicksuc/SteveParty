package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.magpie.WildMagpieEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * A wild Pie: the Common pot Pie's model and animations, its colour's texture (MagpieVariant); in its beak what it
 * carries (WildMagpieEntity#getCarried): the model's own coin for one of the mod's coins, any other shiny thing drawn
 * small at the coin's place.
 */
public class WildMagpieRenderer extends GeoEntityRenderer<WildMagpieEntity> {
    public WildMagpieRenderer(EntityRendererFactory.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public Identifier getModelResource(WildMagpieEntity magpie) {
                return Steveparty.id("geo/entity/magpie.geo.json");
            }

            @Override
            public Identifier getTextureResource(WildMagpieEntity magpie) {
                return magpie.getVariant().texture;
            }

            @Override
            public Identifier getAnimationResource(WildMagpieEntity magpie) {
                return Steveparty.id("animations/entity/magpie.animation.json");
            }

            @Override
            public void setCustomAnimations(WildMagpieEntity magpie, long instanceId, AnimationState<WildMagpieEntity> animationState) {
                super.setCustomAnimations(magpie, instanceId, animationState);
                GeoBone coin = getAnimationProcessor().getBone("coin");
                if (coin != null) coin.setHidden(!magpie.getCarried().isOf(ModItems.COIN));
            }
        });
        this.shadowRadius = 0.2f;
        addRenderLayer(new BlockAndItemGeoLayer<>(this, (bone, magpie) -> {
            ItemStack carried = magpie.getCarried();
            return "coin".equals(bone.getName()) && !carried.isEmpty() && !carried.isOf(ModItems.COIN) ? carried : null;
        }, (bone, magpie) -> null) {
            @Override
            protected void renderStackForBone(MatrixStack poseStack, GeoBone bone, ItemStack stack, WildMagpieEntity animatable,
                                              VertexConsumerProvider bufferSource, float partialTick, int packedLight, int packedOverlay) {
                poseStack.translate(0, -0.05, -0.04);
                poseStack.scale(0.35F, 0.35F, 0.35F);
                super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
            }
        });
    }
}
