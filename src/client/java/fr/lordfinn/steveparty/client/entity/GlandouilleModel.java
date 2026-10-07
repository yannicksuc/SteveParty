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
    /** How far up the brows hide (pixels): inside the cap, whose brim is 1 px above them at rest. */
    private static final float BROW_HIDE = 3.2f;
    private static final Map<GlandouilleVariant, Identifier> TEXTURES = new EnumMap<>(GlandouilleVariant.class);

    static {
        for (GlandouilleVariant variant : GlandouilleVariant.values()) {
            TEXTURES.put(variant, Steveparty.id(variant == GlandouilleVariant.CLASSIC
                    ? "textures/entity/glandouille.png" : "textures/entity/glandouille_" + variant.asString() + ".png"));
        }
    }

    public GlandouilleModel() {
        super(Steveparty.id("glandouille"));
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
        brow(processor.getBone("left_brow"), brows);
        brow(processor.getBone("right_brow"), brows);
        GeoBone cap = processor.getBone("cap");
        if (cap != null) cap.setHidden(!glandouille.hasHat());
    }

    private static void brow(GeoBone brow, float out) {
        if (brow == null) return;
        brow.setHidden(out < 0.03f);
        brow.setPosY((1 - out) * BROW_HIDE); // absolute: no animation moves the brows
    }
}
