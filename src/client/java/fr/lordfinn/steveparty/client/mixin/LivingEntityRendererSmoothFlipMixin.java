package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
import fr.lordfinn.steveparty.client.flip.GoalPoleFlipTracker;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererSmoothFlipMixin<T extends LivingEntity, S extends LivingEntityRenderState & SmoothFlipState> {

    @Inject(method = "updateRenderState", at = @At("HEAD"))
    private void smoothFlip(T entity, S state, float tickDelta, CallbackInfo ci) {
        // The render state is shared by every entity of this renderer: the progress itself is stored
        // per entity (tick based, FPS independent) and only copied into the state here.
        boolean targetFlip = LivingEntityRenderer.shouldFlipUpsideDown(entity);
        state.setFlipProgress(GoalPoleFlipTracker.getModelProgress(entity, targetFlip, tickDelta));
    }
}
