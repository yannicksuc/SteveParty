package fr.lordfinn.steveparty.client.mixin;

import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev clients started in the background (scripts/dev.ps1, {@code -Dsteveparty.dev.backgroundClient=true}) open
 * their window without taking the focus, so the user keeps typing / clicking where they were (no Alt+Tab needed).
 * Without the property: vanilla.
 */
@Mixin(Window.class)
public class WindowBackgroundDevMixin {
    @Unique
    private static final boolean BACKGROUND_CLIENT = Boolean.getBoolean("steveparty.dev.backgroundClient");

    @Inject(method = "<init>", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
    private void steveparty$openWithoutFocus(CallbackInfo ci) {
        if (BACKGROUND_CLIENT) {
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
    }
}
