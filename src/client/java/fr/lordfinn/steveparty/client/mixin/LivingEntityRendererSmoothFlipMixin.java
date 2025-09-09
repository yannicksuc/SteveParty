package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.access.SmoothFlipState;
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
        boolean targetFlip = LivingEntityRenderer.shouldFlipUpsideDown(entity);
        float current = state.getFlipProgress();
        float target = targetFlip ? 1.0F : 0.0F;

        // Smooth interpolation
        float speed = 0.1F; // Adjust for faster/slower animation
        state.setFlipProgress(current + (target - current) * speed);
    }
}
