package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoRenderer;

public class DiceModel extends GeoModel<DiceEntity> {
    @Override
    public Identifier getModelResource(DiceEntity diceEntity, @Nullable GeoRenderer<DiceEntity> geoRenderer) {
        return Identifier.of(Steveparty.MOD_ID, "geo/entity/dice.geo.json");
    }

    @Override
    public Identifier getTextureResource(DiceEntity diceEntity, @Nullable GeoRenderer<DiceEntity> geoRenderer) {
        return Identifier.of(Steveparty.MOD_ID, "textures/entity/dice/default_dice"+diceEntity.getRollValue()+".png");
    }

    @Override
    public Identifier getAnimationResource(DiceEntity diceEntity) {
        return Identifier.of(Steveparty.MOD_ID, "animations/entity/dice.animation.json");
    }

    @Override
    public void setCustomAnimations(DiceEntity animatable, long instanceId, AnimationState<DiceEntity> animationState) {
        GeoBone head = getAnimationProcessor().getBone("head");

        if (head != null) {
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotX(entityData.headPitch() * MathHelper.RADIANS_PER_DEGREE);
            head.setRotY(entityData.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);
        }
    }
}