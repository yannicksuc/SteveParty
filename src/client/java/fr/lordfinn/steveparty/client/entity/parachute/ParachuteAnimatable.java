package fr.lordfinn.steveparty.client.entity.parachute;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Client: the canopy being drawn, one pass at a time (see ParachuteRenderer): the texture of this pass and its tint.
 * No animation of its own: its unfolding and sway are the renderer's transforms, so any canopy model drops in.
 */
public class ParachuteAnimatable implements GeoAnimatable {
    /** An undyed canopy, as painted; a dyed one: its white cloth, then its stripes in grey, tinted. */
    static final Identifier TEXTURE = Steveparty.id("textures/entity/parachute.png");
    static final Identifier WHITE = Steveparty.id("textures/entity/parachute_white.png");
    static final Identifier STRIPES = Steveparty.id("textures/entity/parachute_stripes.png");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    Identifier texture = TEXTURE;
    int color = 0xFFFFFFFF;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return RenderUtil.getCurrentTick();
    }
}
