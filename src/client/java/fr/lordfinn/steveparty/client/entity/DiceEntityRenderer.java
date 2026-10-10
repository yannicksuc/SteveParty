package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiceEntityRenderer extends GeoEntityRenderer<DiceEntity> {
    private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();

    public DiceEntityRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new DiceEntityModel());
    }

    @Override
    public Identifier getTextureLocation(DiceEntity animatable) {
        return textureOf(animatable);
    }

    /**
     * The face a die shows: its result once stopped; while it rolls, a number flickering at random, or the face its
     * server shows (a Slow die turning through its faces).
     */
    static Identifier textureOf(DiceEntity dice) {
        if (dice.isRolling() && !dice.isFaceShown()) {
            MinecraftClient client = MinecraftClient.getInstance();
            long worldTicks = client.world != null ? client.world.getTime() : 0;
            // Per-dice fake face, changing every 4 ticks (deterministic: no state shared between dice): one of the die's
            // own faces (by weight), or 1 to 10 for a plain die
            DiceFacesComponent forged = dice.getDieStack().get(DiceFacesComponent.TYPE);
            List<DiceFacesComponent.DiceFace> faces = forged == null ? List.of() : forged.faces();
            if (!faces.isEmpty()) {
                DiceFacesComponent.DiceFace face = flickerFace(dice, worldTicks, faces);
                return getTexture(face.kind(), face.value());
            }
            return getTexture(Kind.NORMAL, fakeValue(dice, worldTicks));
        }
        return getTexture(dice.getRollKind(), dice.getRollValue());
    }

    /** One of {@code faces} by weight, picked like {@link #fakeValue}. */
    private static DiceFacesComponent.DiceFace flickerFace(DiceEntity dice, long worldTicks, List<DiceFacesComponent.DiceFace> faces) {
        long hash = MathHelper.hashCode(dice.getId(), (int) (worldTicks >> 2), 0x5EED);
        int total = 0;
        for (DiceFacesComponent.DiceFace face : faces) total += face.weight();
        int pick = (int) Math.floorMod(hash ^ (hash >>> 32), (long) Math.max(1, total));
        for (DiceFacesComponent.DiceFace face : faces) {
            pick -= face.weight();
            if (pick < 0) return face;
        }
        return faces.getFirst();
    }

    private static int fakeValue(DiceEntity dice, long worldTicks) {
        long hash = MathHelper.hashCode(dice.getId(), (int) (worldTicks >> 2), 0x5EED);
        return (int) Math.floorMod(hash ^ (hash >>> 32), (long) (DiceEntity.MAX - DiceEntity.MIN + 1)) + DiceEntity.MIN;
    }

    /** One texture per face: textures/entity/dice/&lt;kind&gt;_dice&lt;value&gt;.png (the swap and blank faces have no value). */
    public static Identifier getTexture(Kind kind, int value) {
        String name = switch (kind) {
            case NORMAL -> "default_dice" + value;
            case PREMIUM -> "custom_dice" + value;
            case CURSED -> "cursed_dice" + value;
            case COIN -> "coin_dice" + value;
            case DEBT -> "debt_dice" + value;
            case SWAP -> "swap_dice";
            case BLANK -> "blank_dice";
        };
        return TEXTURES.computeIfAbsent(name, n -> Steveparty.id("textures/entity/dice/" + n + ".png"));
    }

    @Override
    public boolean hasLabel(DiceEntity animatable) {
        return false;
    }
}
