package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.squish.SquishAnimations;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Plays the client-only squish (tokenization) animation on the render state: scale, spin and wobble. */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererSquishMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void steveparty$squishAnimation(T entity, S state, float tickDelta, CallbackInfo ci) {
        SquishAnimations.apply(entity, state, tickDelta);
    }
}
