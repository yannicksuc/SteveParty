package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.render.geo.Blink;
import fr.lordfinn.steveparty.client.render.geo.GeoBones;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * The Mistigri's model (geo/entity/mistigri.geo.json, exported from the art's mistigri_v4.bbmodel). On top of the
 * keyframed animations:
 * <ul>
 *     <li>the bristled tail and the hackles on his back only show while he is angry (hidden, as if flat, otherwise);</li>
 *     <li>the whiskers are left to MistigriRenderer's see-through layer;</li>
 *     <li>his head follows where he looks (added to the animation's head);</li>
 *     <li>his glowing eye closes in a slow blink now and then, and stays shut while he naps
 *     ({@link #eyesClosed}: another texture, its glow off).</li>
 * </ul>
 */
public class MistigriModel extends DefaultedEntityGeoModel<MistigriEntity> {
    static final Identifier TEXTURE = Steveparty.id("textures/entity/mistigri.png");
    static final Identifier BLINK = Steveparty.id("textures/entity/mistigri_blink.png");
    private static final String[] BRISTLES = {"tail_1_bristle", "tail_2_bristle", "tail_3_bristle", "tail_4_bristle",
            "tail_5_bristle", "tail_6_bristle", "tail_7_bristle", "hackles_front", "hackles_back"};
    static final String[] WHISKERS = {"whisker_left", "whisker_right"};
    /** A slow blink: this long (ticks), once in this many, each Mistigri at its own moment. */
    private static final int BLINK_TICKS = 9, BLINK_EVERY = 140;

    public MistigriModel() {
        super(Steveparty.id("mistigri"));
    }

    /** The slow blink, or asleep (a nap in a loaf, sprawled on a chest). */
    static boolean eyesClosed(MistigriEntity mistigri) {
        if (mistigri.isAngry()) return false;
        if (mistigri.isLoafing()) return true; // a nap, or asleep on a chest
        return Blink.closed(mistigri.age, mistigri.getId(), 53, BLINK_EVERY, BLINK_TICKS);
    }

    @Override
    public Identifier getTextureResource(MistigriEntity mistigri) {
        return eyesClosed(mistigri) ? BLINK : TEXTURE;
    }

    @Override
    public void setCustomAnimations(MistigriEntity mistigri, long instanceId, AnimationState<MistigriEntity> state) {
        super.setCustomAnimations(mistigri, instanceId, state);
        AnimationProcessor<MistigriEntity> processor = getAnimationProcessor();
        boolean bristling = mistigri.isAngry() && !mistigri.isActing();
        GeoBones.hide(processor, BRISTLES, !bristling);
        // the whiskers are drawn see-through by their own layer (MistigriRenderer), not with the fur
        GeoBones.hide(processor, WHISKERS, true);
        GeoBone head = processor.getBone("head");
        if (head != null && !mistigri.isActing() && !mistigri.isLoafing()) {
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(head.getRotX() + MathHelper.clamp(data.headPitch(), -30, 30) * MathHelper.RADIANS_PER_DEGREE * 0.7f);
            head.setRotY(head.getRotY() + MathHelper.clamp(data.netHeadYaw(), -55, 55) * MathHelper.RADIANS_PER_DEGREE);
        }
    }
}
