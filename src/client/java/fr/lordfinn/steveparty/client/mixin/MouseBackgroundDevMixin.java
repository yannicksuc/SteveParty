package fr.lordfinn.steveparty.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev clients started in the background (scripts/dev.ps1, {@code -Dsteveparty.dev.backgroundClient=true}) never grab
 * the mouse while the OS says their window isn't focused: otherwise joining the world confines the user's cursor to
 * a window sitting behind the others. Clicking in the window grabs it as usual. Without the property: vanilla.
 */
@Mixin(Mouse.class)
public class MouseBackgroundDevMixin {
    @Unique
    private static final boolean BACKGROUND_CLIENT = Boolean.getBoolean("steveparty.dev.backgroundClient");

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void steveparty$noGrabInTheBackground(CallbackInfo ci) {
        if (BACKGROUND_CLIENT && GLFW.glfwGetWindowAttrib(client.getWindow().getHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE) {
            ci.cancel();
        }
    }
}
