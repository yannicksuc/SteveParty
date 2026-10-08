package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.token.GeoPoses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib mobs: once a posed pawn is animated for the frame, its bones are frozen in its pose (see {@link GeoPoses}). */
@Mixin(value = GeoModel.class, remap = false)
public abstract class GeoModelPawnPoseMixin<T extends GeoAnimatable> {
    @Inject(method = "handleAnimations", at = @At("RETURN"))
    private void steveparty$pawnPose(T animatable, long instanceId, AnimationState<T> animationState, float partialTick,
                                     CallbackInfo ci) {
        GeoPoses.apply((GeoModel<T>) (Object) this, animatable);
    }
}
