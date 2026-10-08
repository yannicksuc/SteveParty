package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Glandouille's model (geo/entity/glandouille.geo.json, from the art sources). On top of the keyframed
 * animations: its brows always show, gently frowning when calm (a little up, a few degrees inward), lowered to the
 * model's full frown as it gets angry, its eyes and brows gone while it sleeps (a plain face), and its cap (with the
 * stem) gone while it has lost it. Nothing allocated per frame.
 */
public class GlandouilleModel extends DefaultedEntityGeoModel<GlandouilleEntity> {
    private static final Map<GlandouilleVariant, Identifier> TEXTURES = new EnumMap<>(GlandouilleVariant.class);
    private static final Map<GlandouilleVariant, Identifier> MODELS = new EnumMap<>(GlandouilleVariant.class);

    static {
        for (GlandouilleVariant variant : GlandouilleVariant.values()) {
            TEXTURES.put(variant, Steveparty.id("textures/entity/" + variant.modelName() + ".png"));
            MODELS.put(variant, Steveparty.id("geo/entity/" + variant.modelName() + ".geo.json"));
        }
    }

    public GlandouilleModel() {
        super(Steveparty.id("glandouille"));
    }

    /** Each variant its own model (same bones: the animations, glandouille.animation.json, are shared). */
    @Override
    public Identifier getModelResource(GlandouilleEntity animatable) {
        return MODELS.get(animatable.getVariant());
    }

    @Override
    public Identifier getTextureResource(GlandouilleEntity animatable) {
        return TEXTURES.get(animatable.getVariant());
    }

    @Override
    public void setCustomAnimations(GlandouilleEntity glandouille, long instanceId, AnimationState<GlandouilleEntity> animationState) {
        super.setCustomAnimations(glandouille, instanceId, animationState);
        AnimationProcessor<GlandouilleEntity> processor = getAnimationProcessor();
        float brows = MathHelper.lerp(animationState.getPartialTick(), glandouille.prevBrows, glandouille.brows);
        float hide = glandouille.getVariant().browHidePx;
        boolean asleep = glandouille.isSleeping();
        brow(processor.getBone("left_brow"), brows, hide, asleep);
        brow(processor.getBone("right_brow"), brows, hide, asleep);
        for (String eye : EYES) {
            GeoBone bone = processor.getBone(eye);
            if (bone == null) continue;
            bone.setHidden(asleep);
            // the brows hang on the eyes: they stay (the pupils hide themselves), unsquashed by the sleep animation
            bone.setChildrenHidden(false);
            if (asleep) {
                bone.setScaleX(1);
                bone.setScaleY(1);
                bone.setScaleZ(1);
            }
        }
        GeoBone cap = processor.getBone("cap");
        if (cap != null) cap.setHidden(!glandouille.hasHat());
    }

    private static final String[] EYES = {"left_eye", "right_eye", "left_pupil", "right_pupil"};

    /** How much of the model's frown (its brows' rotation) they keep when calm: a few degrees, grumpy, not angry. */
    private static final float CALM_FROWN = 0.35f;
    /** How much of {@code hide} (pixels) the calm brows sit higher than the angry ones. */
    private static final float CALM_LIFT = 0.2f;
    /** Asleep: the brows stay, just above the closed eyes, a little frowned (a grumpy nap, not an angry one). */
    private static final float SLEEP_FROWN = 0.4f, SLEEP_DROP = 1f;

    /** {@code out}: 0 calm, 1 angry (unused asleep); {@code hide}: how far up (pixels) the brows can go under the cap, its variant's. */
    private static void brow(GeoBone brow, float out, float hide, boolean asleep) {
        if (brow == null) return;
        // absolute: no animation moves the brows
        brow.setPosY(asleep ? -SLEEP_DROP : (1 - out) * CALM_LIFT * hide);
        if (brow.getInitialSnapshot() != null) {
            brow.setRotZ(brow.getInitialSnapshot().getRotZ() * (asleep ? SLEEP_FROWN : MathHelper.lerp(out, CALM_FROWN, 1f)));
        }
    }
}
