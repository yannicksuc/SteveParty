package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.hammer.StencilHammerStrikes;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A tiny camera shake when the striker's Stencil Hammer lands (with the view tilt, like being hurt). */
@Mixin(GameRenderer.class)
public class GameRendererStencilHammerShakeMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void steveparty$hammerShake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        StencilHammerStrikes.applyShake(matrices, tickDelta);
    }
}
