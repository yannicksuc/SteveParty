package fr.lordfinn.steveparty.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Dev runs only (never registered in a released game): client commands that press the player's buttons, so that the
 * dev game can be driven from the outside like a player would, e.g. {@code /sptest click} for a left click.
 */
public final class DevClientCommands {
    private DevClientCommands() {
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommandManager.literal("sptest")
                        .then(ClientCommandManager.literal("click").executes(context -> {
                            // As the left mouse button pressed once: handled at the next tick like a real click
                            KeyBinding.onKeyPressed(InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_LEFT));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("use").executes(context -> {
                            KeyBinding.onKeyPressed(InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
                            return 1;
                        }))));
    }
}
