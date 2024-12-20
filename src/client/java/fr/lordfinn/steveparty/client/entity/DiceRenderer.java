package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DiceRenderer extends GeoEntityRenderer<DiceEntity> {
    private int fakeValue = -999999;
    public DiceRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new DiceModel());
    }

    @Override
    public Identifier getTextureLocation(DiceEntity animatable) {
        MinecraftClient client = MinecraftClient.getInstance();
        long worldTicks = 0;
        if (client.world != null) {
            worldTicks = client.world.getTime();
        }
        if (animatable.isRolling()) {
            if (worldTicks % 4 == 0 || fakeValue == -999999)
                fakeValue = animatable.getRandomDiceValue();
            if (animatable.isRolling())
                return Identifier.of(Steveparty.MOD_ID, "textures/entity/dice/default_dice" + fakeValue + ".png");
        }
        return Identifier.of(Steveparty.MOD_ID, "textures/entity/dice/default_dice"+animatable.getRollValue()+".png");
    }

    @Override
    public boolean hasLabel(DiceEntity animatable, double distToCameraSq) {
        return false;
    }
}