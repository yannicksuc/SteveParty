package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiceEntityRenderer extends GeoEntityRenderer<DiceEntity> {
    private static final Map<Integer, Identifier> TEXTURES = new ConcurrentHashMap<>();

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
            // Per-dice fake face, changing every 4 ticks (deterministic: no state shared between dice)
            return getTexture(fakeValue(animatable, worldTicks));
        }
        return getTexture(animatable.getRollValue());
    }

    private static int fakeValue(DiceEntity dice, long worldTicks) {
        long hash = MathHelper.hashCode(dice.getId(), (int) (worldTicks >> 2), 0x5EED);
        return (int) Math.floorMod(hash ^ (hash >>> 32), (long) (DiceEntity.MAX - DiceEntity.MIN + 1)) + DiceEntity.MIN;
    }

    private static Identifier getTexture(int value) {
        return TEXTURES.computeIfAbsent(value, v -> Steveparty.id("textures/entity/dice/default_dice" + v + ".png"));
    }

    @Override
    public boolean hasLabel(DiceEntity animatable, double distToCameraSq) {
        return false;
    }
}
