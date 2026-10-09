package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.input.ScrollHandlers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mouse wheel of the tools that use it (see ScrollHandlers) instead of the hotbar. */
@Mixin(Mouse.class)
public class MouseScrollMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void steveparty$toolScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == client.getWindow().getHandle() && ScrollHandlers.dispatch(vertical)) ci.cancel();
    }
}
