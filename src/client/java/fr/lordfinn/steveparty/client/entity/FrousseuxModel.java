package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Frousseux's model (geo/entity/frousseux.geo.json, from the art sources): a melted candle of wax, its drips on
 * an outer layer, its hands floating beside it. Its body's wax is drawn a little see-through (a ghost still; its
 * drips and eyes are opaque in the texture). On top of the keyframed animations: its flame's size by its health
 * ({@link FrousseuxEntity.Flame}) with a little flicker, gone once dead or blown out; its lids closed while it blinks
 * now and then and while it hides its eyes. Nothing allocated per frame.
 */
public class FrousseuxModel extends DefaultedEntityGeoModel<FrousseuxEntity> {
    /** It blinks once every this many ticks, for {@link #BLINK_TICKS}. */
    private static final int BLINK_EVERY = 90, BLINK_TICKS = 3;
    /**
     * Its outer layers: hidden from the main, one-sided pass, drawn two-sided by FrousseuxRenderer's ShellLayer, with
     * the body's own cube (hollow underneath, the inside of its walls shows from below).
     */
    static final String[] OVERLAY_BONES = {"body_overlay", "left_sleeve", "right_sleeve"};

    public FrousseuxModel() {
        super(Steveparty.id("frousseux"));
    }

    @Override
    public RenderLayer getRenderType(FrousseuxEntity animatable, Identifier texture) {
        return RenderLayer.getEntityTranslucentCull(texture);
    }

    @Override
    public void setCustomAnimations(FrousseuxEntity frousseux, long instanceId, AnimationState<FrousseuxEntity> animationState) {
        super.setCustomAnimations(frousseux, instanceId, animationState);
        float time = frousseux.age + animationState.getPartialTick() + frousseux.getId() * 13;
        GeoBone flame = getAnimationProcessor().getBone("flame");
        if (flame != null) {
            boolean out = frousseux.isBlownOut() || frousseux.deathTime > 0;
            flame.setHidden(out);
            if (!out) {
                float size = frousseux.getFlame().size;
                float partial = animationState.getPartialTick();
                // the breath of its flame: a little taller and thinner, then back, never in step
                float flicker = 0.05f * MathHelper.sin(time * 0.9f) + 0.03f * MathHelper.sin(time * 2.3f + 1.7f);
                flame.setScaleX(size * (1 - flicker * 0.5f));
                flame.setScaleZ(size * (1 - flicker * 0.5f));
                flame.setScaleY(size * (1 + flicker));
                // swaying about its foot: layered waves, and leaning against its flight and its turns
                float swayZ = 0.07f * MathHelper.sin(time * 0.31f) + 0.04f * MathHelper.sin(time * 0.77f + 1.1f)
                        + 0.02f * MathHelper.sin(time * 1.9f + 2.3f);
                float swayX = 0.05f * MathHelper.sin(time * 0.27f + 2.0f) + 0.03f * MathHelper.sin(time * 0.83f + 0.4f)
                        + 0.015f * MathHelper.sin(time * 2.1f);
                flame.setRotX(swayX + frousseux.flameLeanX(partial));
                flame.setRotZ(swayZ + frousseux.flameLeanZ(partial));
            }
        }
        for (String name : OVERLAY_BONES) {
            GeoBone overlay = getAnimationProcessor().getBone(name);
            if (overlay != null) overlay.setHidden(true);
        }
        GeoBone body = getAnimationProcessor().getBone("body");
        if (body != null) {
            body.setHidden(true);
            body.setChildrenHidden(false);
        }
        GeoBone lids = getAnimationProcessor().getBone("lids");
        if (lids != null) {
            boolean blink = Math.floorMod((int) time, BLINK_EVERY) < BLINK_TICKS;
            lids.setHidden(!(blink || frousseux.isShy()));
        }
    }
}
