package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a tool's wheel is open, its own cursor points: no crosshair in the middle of it. */
@Mixin(InGameHud.class)
public class InGameHudWheelCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void steveparty$noCrosshairOnTheWheel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ToolWheel.isOpen()) ci.cancel();
    }
}
