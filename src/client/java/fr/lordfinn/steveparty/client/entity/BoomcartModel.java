package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Boomcart's model (geo/entity/boomcart.geo.json, from the art sources: the approved v13 preview): a mine cart's
 * body, its upper lip hinged at the front top, its prognathous jaw hinged at the front bottom, its eyes on two planes
 * just in front of the lip, and a "load" bone the renderer draws the TNT or the barrel from.
 * <p>
 * Its eyes are one texture per state, the same layout: grumpy slots at rest, a wider glow with the mouth open (fed,
 * roaring, "feed me"), wide-open sockets staring once lit, and in the last seconds the glow filling them, the eyes
 * popping out a size bigger ({@link #eyes}). The mouth's state comes from its lip, as the last frame
 * left it, so the triggered animations (eat, roar) open its eyes too.
 * <p>
 * It stands on four square wheels ({@link #LIFT} px tall under its body), which roll as far as it travelled
 * ({@link BoomcartEntity#clientWheelTravel}): a full turn per block, a square's four sides. Square, they lift the
 * whole cart a little each time they roll over a corner ({@link #rollWheels}).
 */
public class BoomcartModel extends DefaultedEntityGeoModel<BoomcartEntity> {
    public static final Identifier REST = Steveparty.id("textures/entity/boomcart.png");
    public static final Identifier OPEN = Steveparty.id("textures/entity/boomcart_open.png");
    public static final Identifier LIT = Steveparty.id("textures/entity/boomcart_lit.png");
    public static final Identifier BLOW = Steveparty.id("textures/entity/boomcart_blow.png");
    /** The last ticks of the fuse: white-hot fuse frames, the eyes at their widest. */
    public static final int BLOW_TICKS = 40;
    /** The eyes' scale in those last ticks. */
    private static final float BLOW_EYES = 1.2f;
    /** The lip lifted this much (radians) counts as an open mouth. */
    private static final float OPEN_LIP = 0.12f;
    /** The cart's floor this high above the ground (px): its wheels' axles, half a wheel. */
    public static final float LIFT = 2;
    private static final String[] WHEELS = {"wheel_front_right", "wheel_front_left", "wheel_back_right", "wheel_back_left"};

    public BoomcartModel() {
        super(Steveparty.id("boomcart"));
    }

    /** Its eye state: 0 at rest, 1 mouth open, 2 lit, 3 about to blow. */
    public static int eyes(BoomcartEntity boomcart) {
        if (boomcart.isLit()) return boomcart.getFuse() < BLOW_TICKS ? 3 : 2;
        return boomcart.isHungry() || Math.abs(boomcart.clientLipAngle) > OPEN_LIP ? 1 : 0;
    }

    @Override
    public Identifier getTextureResource(BoomcartEntity boomcart) {
        return switch (eyes(boomcart)) {
            case 3 -> BLOW;
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
        GeoBone eyes = getAnimationProcessor().getBone("eyes");
        if (eyes != null && eyes(boomcart) == 3) {
            eyes.setScaleX(BLOW_EYES);
            eyes.setScaleY(BLOW_EYES);
        }
        rollWheels(boomcart, animationState.getPartialTick());
    }

    /**
     * Turns the wheels by how far it travelled (forward: their tops toward the front, -z) and lifts the cart (root) as
     * a square rolling on its corner would: its centre half a side up when flat, half a diagonal on the corner.
     * Both set from the bones' base pose, never added to the last frame's.
     */
    private void rollWheels(BoomcartEntity boomcart, float partialTick) {
        float travel = MathHelper.lerp(partialTick, boomcart.clientWheelTravelLast, boomcart.clientWheelTravel);
        float angle = -MathHelper.TAU * travel;
        for (String name : WHEELS) {
            GeoBone wheel = getAnimationProcessor().getBone(name);
            if (wheel != null) wheel.setRotX(wheel.getInitialSnapshot().getRotX() + angle);
        }
        GeoBone root = getAnimationProcessor().getBone("root");
        if (root == null) return;
        float corner = Math.abs(angle) % MathHelper.HALF_PI;
        // a wheel's half side is its axle's height, LIFT
        root.setPosY(root.getInitialSnapshot().getOffsetY()
                + LIFT * (MathHelper.cos(corner) + MathHelper.sin(corner) - 1));
    }
}
