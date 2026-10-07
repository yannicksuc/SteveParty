package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * The mouse cursors of the mod's screens, through GLFW (1.21.1 has no cursor API): the hand (pointer) over what can be
 * clicked, and the pipette while a tile screen's pipette is on (it wins over the hand). A screen that shows one must
 * {@link #reset()} when it closes, so that it never stays on another screen.
 */
public final class HandCursor {
    private static final Identifier PIPETTE_TEXTURE = Steveparty.id("textures/gui/pipette_cursor.png");
    /** The tip of the pipette in its 32x32 cursor image (bottom left). */
    private static final int PIPETTE_HOT_X = 1, PIPETTE_HOT_Y = 30;

    private enum Kind { DEFAULT, HAND, PIPETTE }

    private static long hand;
    private static long pipette;
    private static Kind shown = Kind.DEFAULT;
    private static boolean pipetteOn;

    private HandCursor() {
    }

    /** Shows the hand while {@code over} is true, the default cursor otherwise (only calls GLFW on a change). */
    public static void update(boolean over) {
        show(pipetteOn ? Kind.PIPETTE : over ? Kind.HAND : Kind.DEFAULT);
    }

    /** The pipette cursor on (until turned off or {@link #reset()}). */
    public static void pipette(boolean on) {
        pipetteOn = on;
        show(on ? Kind.PIPETTE : Kind.DEFAULT);
    }

    /** Back to the default cursor (when the screen closes). */
    public static void reset() {
        pipetteOn = false;
        show(Kind.DEFAULT);
    }

    private static void show(Kind kind) {
        if (kind == shown) return;
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        long cursor = switch (kind) {
            case HAND -> {
                if (hand == 0) hand = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
                yield hand;
            }
            case PIPETTE -> {
                if (pipette == 0) pipette = createPipette();
                yield pipette;
            }
            case DEFAULT -> 0;
        };
        GLFW.glfwSetCursor(window, cursor);
        shown = kind;
    }

    /** The pipette cursor from its texture (0, the default cursor, if it can't be read). */
    private static long createPipette() {
        var resource = MinecraftClient.getInstance().getResourceManager().getResource(PIPETTE_TEXTURE);
        if (resource.isEmpty()) return 0;
        try (InputStream stream = resource.get().getInputStream(); NativeImage image = NativeImage.read(stream)) {
            int w = image.getWidth(), h = image.getHeight();
            ByteBuffer pixels = MemoryUtil.memAlloc(w * h * 4);
            try (GLFWImage glfwImage = GLFWImage.malloc()) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int abgr = image.getColor(x, y);
                        pixels.put((byte) (abgr & 0xFF)).put((byte) ((abgr >> 8) & 0xFF))
                                .put((byte) ((abgr >> 16) & 0xFF)).put((byte) ((abgr >>> 24) & 0xFF));
                    }
                }
                pixels.flip();
                glfwImage.set(w, h, pixels);
                return GLFW.glfwCreateCursor(glfwImage, PIPETTE_HOT_X, PIPETTE_HOT_Y);
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } catch (Exception e) {
            Steveparty.LOGGER.warn("Unreadable pipette cursor", e);
            return 0;
        }
    }
}
