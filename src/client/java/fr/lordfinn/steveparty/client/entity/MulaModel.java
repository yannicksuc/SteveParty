package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaMotion;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Mula's model, plus its procedural float layer ({@link MulaMotion}) applied after the keyframed animations: the
 * root bone "body" (untouched by the animations, pivot at the Mula's centre) bobs, squashes &amp; stretches, sways,
 * leans into its flight and banks into its turns; the arms flutter a little behind the bob (follow-through). The
 * arms' flutter is added on top of whatever the animation does with them. Nothing allocated per frame.
 * <p>
 * Signs follow the animation json (Blockbench) convention: GeckoLib stores x and y rotations negated.
 */
public class MulaModel extends DefaultedEntityGeoModel<MulaEntity> {
    private final MulaMotion.Layer layer = new MulaMotion.Layer();

    public MulaModel() {
        super(Steveparty.id("mula"));
    }

    @Override
    public void setCustomAnimations(MulaEntity mula, long instanceId, AnimationState<MulaEntity> animationState) {
        super.setCustomAnimations(mula, instanceId, animationState);
        AnimationProcessor<MulaEntity> processor = getAnimationProcessor();
        GeoBone body = processor.getBone("body");
        if (body == null) return;
        mula.getMotion().layer(animationState.getPartialTick(), layer);

        body.setPosX(layer.posX);
        body.setPosY(layer.posY);
        body.setRotX(-layer.pitch * MathHelper.RADIANS_PER_DEGREE);
        body.setRotZ(layer.roll * MathHelper.RADIANS_PER_DEGREE);
        body.setScaleX(layer.scaleXZ);
        body.setScaleY(layer.scaleY);
        body.setScaleZ(layer.scaleXZ);

        float flutter = layer.handFlutter * MathHelper.RADIANS_PER_DEGREE;
        GeoBone hand = processor.getBone("left_hand2");
        if (hand != null) hand.setRotZ(hand.getRotZ() + flutter);
        hand = processor.getBone("left_hand3");
        if (hand != null) hand.setRotZ(hand.getRotZ() - flutter);
    }
}
