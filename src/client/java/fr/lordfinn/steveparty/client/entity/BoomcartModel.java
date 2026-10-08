package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Boomcart's model (geo/entity/boomcart.geo.json, from the art sources: the approved v12 preview): a mine cart's
 * body, its upper lip hinged at the front top, its prognathous jaw hinged at the front bottom, its eyes on two planes
 * just in front of the lip, and a "load" bone the renderer draws the TNT or the barrel from.
 * <p>
 * Its eyes are one texture per state, the same layout: grumpy slots at rest, a wider glow with the mouth open (fed,
 * roaring, "feed me"), panic sockets once lit ({@link #eyes}). The mouth's state comes from its lip, as the last frame
 * left it, so the triggered animations (eat, roar) open its eyes too.
 */
public class BoomcartModel extends DefaultedEntityGeoModel<BoomcartEntity> {
    public static final Identifier REST = Steveparty.id("textures/entity/boomcart.png");
    public static final Identifier OPEN = Steveparty.id("textures/entity/boomcart_open.png");
    public static final Identifier LIT = Steveparty.id("textures/entity/boomcart_lit.png");
    /** The lip lifted this much (radians) counts as an open mouth. */
    private static final float OPEN_LIP = 0.12f;

    public BoomcartModel() {
        super(Steveparty.id("boomcart"));
    }

    /** Its eye state: 0 at rest, 1 mouth open, 2 lit. */
    public static int eyes(BoomcartEntity boomcart) {
        if (boomcart.isLit()) return 2;
        return boomcart.isHungry() || Math.abs(boomcart.clientLipAngle) > OPEN_LIP ? 1 : 0;
    }

    @Override
    public Identifier getTextureResource(BoomcartEntity boomcart) {
        return switch (eyes(boomcart)) {
            case 2 -> LIT;
            case 1 -> OPEN;
            default -> REST;
        };
    }

    @Override
    public RenderLayer getRenderType(BoomcartEntity animatable, Identifier texture) {
        return RenderLayer.getEntityCutoutNoCull(texture);
    }

    @Override
    public void setCustomAnimations(BoomcartEntity boomcart, long instanceId, AnimationState<BoomcartEntity> animationState) {
        super.setCustomAnimations(boomcart, instanceId, animationState);
        GeoBone lip = getAnimationProcessor().getBone("lip");
        if (lip != null) boomcart.clientLipAngle = lip.getRotX();
    }
}
