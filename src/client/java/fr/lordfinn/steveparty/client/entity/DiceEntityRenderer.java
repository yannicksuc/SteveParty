package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DiceEntityRenderer extends GeoEntityRenderer<DiceEntity> {
    private int fakeValue = -999999;
    public DiceEntityRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new DiceEntityModel());
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
                return Steveparty.id("textures/entity/dice/default_dice" + fakeValue + ".png");
        }
        return Steveparty.id("textures/entity/dice/default_dice"+animatable.getRollValue()+".png");
    }

    @Override
    public boolean hasLabel(DiceEntity animatable, double distToCameraSq) {
        return false;
    }
}