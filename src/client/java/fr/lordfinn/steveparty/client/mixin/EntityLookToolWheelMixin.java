package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a tool's wheel is open, the mouse moves its cursor instead of the camera (the player keeps walking, sneaking...). */
@Mixin(Entity.class)
public class EntityLookToolWheelMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void steveparty$wheelCursor(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player && ToolWheel.moveCursor(cursorDeltaX, cursorDeltaY)) ci.cancel();
    }
}
