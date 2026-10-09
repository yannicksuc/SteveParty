package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.render.geo.GeoBones;
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
    /** Offset of the whole model (pixels): the body cube spans y 2..11 in the geo, drawn at 0..9 (its hitbox). */
    public static final float BODY_Y = -2f;

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
        // the body cube's bottom is 2 px above the model's origin in the geo: brought down onto the feet, so the model
        // sits in its hitbox (MulaEntity.MODEL_SIZE) instead of floating above it; the bob stays centred on it
        body.setPosY(layer.posY + BODY_Y);
        body.setRotX(-layer.pitch * MathHelper.RADIANS_PER_DEGREE);
        body.setRotY(-layer.yaw * MathHelper.RADIANS_PER_DEGREE);
        body.setRotZ(layer.roll * MathHelper.RADIANS_PER_DEGREE);
        body.setScaleX(layer.scaleXZ);
        body.setScaleY(layer.scaleY);
        body.setScaleZ(layer.scaleXZ);

        float flutter = layer.handFlutter * MathHelper.RADIANS_PER_DEGREE;
        GeoBone hand = processor.getBone("left_hand2");
        GeoBones.addRotZ(hand, flutter);
        hand = processor.getBone("left_hand3");
        GeoBones.addRotZ(hand, -flutter);

        // eyelids follow the eyes: hidden while they are open, coming down from the top as they close, covering them
        // entirely once they are shut (sleeping, blinking) so no eye white shows
        lid(processor.getBone("lid_left"), processor.getBone("eye_left"));
        lid(processor.getBone("lid_right"), processor.getBone("eye_right"));

        // wide eyes when a player holds its food (on top of whatever the eyes are doing)
        if (layer.eyeWiden != 1f) {
            widen(processor.getBone("eye_left"), layer.eyeWiden);
            widen(processor.getBone("eye_right"), layer.eyeWiden);
        }
        // the core swells and beats with how full it is
        GeoBones.scale(processor.getBone("core"), layer.coreScale);
    }

    /** Eye scale y at or below which the lid covers the whole eye, and at or above which it is hidden. */
    private static final float SHUT = 0.12f, OPEN = 0.6f;

    private static void lid(GeoBone lid, GeoBone eye) {
        if (lid == null || eye == null) return;
        float closed = MathHelper.clamp((OPEN - eye.getScaleY()) / (OPEN - SHUT), 0f, 1f);
        lid.setHidden(closed < 0.02f);
        lid.setScaleY(Math.max(0.02f, closed));
    }

    private static void widen(GeoBone eye, float factor) {
        if (eye == null) return;
        GeoBones.multiplyScaleXY(eye, factor);
    }
}
