package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.gui.StencilGunHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sneak + mouse wheel with a stencil gun cycles the gun's stencils / colours instead of the hotbar. */
@Mixin(Mouse.class)
public class MouseScrollMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void steveparty$stencilGunScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == client.getWindow().getHandle() && StencilGunHud.onScroll(vertical)) ci.cancel();
    }
}
