package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpieEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Pie: a black-and-white magpie, blue-green sheen on its wings and long tail; the coin in its beak only while it
 * carries one (its {@code coin} bone).
 */
public class MagpieRenderer extends GeoEntityRenderer<MagpieEntity> {
    public MagpieRenderer(EntityRendererFactory.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public Identifier getModelResource(MagpieEntity magpie) {
                return Steveparty.id("geo/entity/magpie.geo.json");
            }

            @Override
            public Identifier getTextureResource(MagpieEntity magpie) {
                return Steveparty.id("textures/entity/magpie.png");
            }

            @Override
            public Identifier getAnimationResource(MagpieEntity magpie) {
                return Steveparty.id("animations/entity/magpie.animation.json");
            }

            @Override
            public void setCustomAnimations(MagpieEntity magpie, long instanceId, AnimationState<MagpieEntity> animationState) {
                super.setCustomAnimations(magpie, instanceId, animationState);
                GeoBone coin = getAnimationProcessor().getBone("coin");
                if (coin != null) coin.setHidden(!magpie.isCarrying());
            }
        });
        this.shadowRadius = 0.2f;
    }
}
