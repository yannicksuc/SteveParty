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
 * leans into its flight and banks into its turns, and turns to face a player holding its food; the arms flutter a
 * little behind the bob (follow-through), the eyes widen when excited, the core swells and beats with how full the
 * Mula is. The arm and eye effects are added on top of whatever the animation does with them. Nothing allocated per
 * frame.
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
        body.setRotY(-layer.yaw * MathHelper.RADIANS_PER_DEGREE);
        body.setRotZ(layer.roll * MathHelper.RADIANS_PER_DEGREE);
        body.setScaleX(layer.scaleXZ);
        body.setScaleY(layer.scaleY);
        body.setScaleZ(layer.scaleXZ);

        float flutter = layer.handFlutter * MathHelper.RADIANS_PER_DEGREE;
        GeoBone hand = processor.getBone("left_hand2");
        if (hand != null) hand.setRotZ(hand.getRotZ() + flutter);
        hand = processor.getBone("left_hand3");
        if (hand != null) hand.setRotZ(hand.getRotZ() - flutter);

        // wide eyes when a player holds its food (on top of whatever the eyes are doing)
        if (layer.eyeWiden != 1f) {
            widen(processor.getBone("eye_left"), layer.eyeWiden);
            widen(processor.getBone("eye_right"), layer.eyeWiden);
        }
        // the core swells and beats with how full it is
        GeoBone core = processor.getBone("core");
        if (core != null) {
            core.setScaleX(layer.coreScale);
            core.setScaleY(layer.coreScale);
            core.setScaleZ(layer.coreScale);
        }
    }

    private static void widen(GeoBone eye, float factor) {
        if (eye == null) return;
        eye.setScaleX(eye.getScaleX() * factor);
        eye.setScaleY(eye.getScaleY() * factor);
    }
}
