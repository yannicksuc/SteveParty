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
 * animations: its brows slide out from under its cap as it gets angry (hidden under it when calm), and its cap (with
 * the stem) is gone while it has lost it. Nothing allocated per frame.
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
        brow(processor.getBone("left_brow"), brows, hide);
        brow(processor.getBone("right_brow"), brows, hide);
        GeoBone cap = processor.getBone("cap");
        if (cap != null) cap.setHidden(!glandouille.hasHat());
    }

    /** {@code hide}: how far up (pixels) the brows go inside the cap, its variant's. */
    private static void brow(GeoBone brow, float out, float hide) {
        if (brow == null) return;
        brow.setHidden(out < 0.03f);
        brow.setPosY((1 - out) * hide); // absolute: no animation moves the brows
    }
}
