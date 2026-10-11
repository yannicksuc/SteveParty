package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriDieEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Mistigri's loaded die: the mod's die model and animations with its cursed faces (flickering 1 to 3 while it
 * tumbles), {@link MistigriDieEntity#SIZE} blocks wide (juggled between his fore paws).
 */
public class MistigriDieRenderer extends GeoEntityRenderer<MistigriDieEntity> {
    private static final Identifier[] FACES = {Steveparty.id("textures/entity/dice/cursed_dice1.png"),
            Steveparty.id("textures/entity/dice/cursed_dice2.png"), Steveparty.id("textures/entity/dice/cursed_dice3.png")};

    public MistigriDieRenderer(EntityRendererFactory.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public Identifier getModelResource(MistigriDieEntity die) {
                return Steveparty.id("geo/entity/dice.geo.json");
            }

            @Override
            public Identifier getTextureResource(MistigriDieEntity die) {
                return FACES[face(die) - 1];
            }

            @Override
            public Identifier getAnimationResource(MistigriDieEntity die) {
                return Steveparty.id("animations/entity/dice.animation.json");
            }
        });
        withScale(MistigriDieEntity.SIZE); // the model is a block wide
        this.shadowRadius = 0.25f;
    }

    /** The face shown: its result once stopped, a low face flickering while it tumbles. */
    static int face(MistigriDieEntity die) {
        if (!die.isRolling()) return die.getFace();
        return 1 + Math.floorMod(die.age / 3 + die.getId(), 3);
    }
}
