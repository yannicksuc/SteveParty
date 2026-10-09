package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.magpie.WildMagpieEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** A wild Pie: the Common pot Pie's model and animations, its colour's texture (MagpieVariant), never a coin. */
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
                if (coin != null) coin.setHidden(true);
            }
        });
        this.shadowRadius = 0.2f;
    }
}
