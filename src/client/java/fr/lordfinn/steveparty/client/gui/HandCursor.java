package fr.lordfinn.steveparty.client.gui;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * The hand (pointer) mouse cursor over what can be clicked in a screen: 1.21.1 has no cursor API, so it goes through
 * GLFW. A screen that shows it must {@link #reset()} when it closes, so that the hand never stays on another screen.
 */
public final class HandCursor {
    private static long hand;
    private static boolean shown;

    private HandCursor() {
    }

    /** Shows the hand while {@code over} is true, the default cursor otherwise (only calls GLFW on a change). */
    public static void update(boolean over) {
        if (over == shown) return;
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        if (over) {
            if (hand == 0) hand = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
            GLFW.glfwSetCursor(window, hand);
        } else {
            GLFW.glfwSetCursor(window, 0);
        }
        shown = over;
    }

    /** Back to the default cursor (when the screen closes). */
    public static void reset() {
        update(false);
    }
}
