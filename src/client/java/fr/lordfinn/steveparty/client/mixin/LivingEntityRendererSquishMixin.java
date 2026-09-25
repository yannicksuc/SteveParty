package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SquishStretchState;
import fr.lordfinn.steveparty.client.squish.SquishAnimations;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plays the client-only squish (tokenization) animation on the render state: scale steps and jelly squash and
 * stretch.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererSquishMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void steveparty$squishAnimation(T entity, S state, float tickDelta, CallbackInfo ci) {
        // Render states are reused from one frame to the next: no deformation unless an animation sets one
        if (state instanceof SquishStretchState stretch) stretch.steveparty$setStretch(1.0F);
        SquishAnimations.apply(entity, state, tickDelta);
    }

    /** Squash and stretch from the feet (after the base scale, before the body rotation), keeping the volume. */
    @Inject(method = "setupTransforms", at = @At("HEAD"))
    private void steveparty$squashAndStretch(S state, MatrixStack matrices, float animationProgress, float bodyYaw, CallbackInfo ci) {
        if (!(state instanceof SquishStretchState stretchState)) return;
        float stretch = stretchState.steveparty$getStretch();
        if (Math.abs(stretch - 1.0F) < 1.0E-3F || stretch <= 0) return;
        float width = (float) (1.0 / Math.sqrt(stretch));
        matrices.scale(width, stretch, width);
    }
}
