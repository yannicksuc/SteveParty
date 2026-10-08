package fr.lordfinn.steveparty.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
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
                        }))
                        // The camera: first person, third person from behind (F5) or from the front
                        .then(ClientCommandManager.literal("perspective")
                                .then(ClientCommandManager.argument("view", IntegerArgumentType.integer(0, 2)).executes(context -> {
                                    context.getSource().getClient().options.setPerspective(
                                            net.minecraft.client.option.Perspective.values()[IntegerArgumentType.getInteger(context, "view")]);
                                    return 1;
                                })))
                        // The GUI scale (0: auto)
                        .then(ClientCommandManager.literal("guiscale")
                                .then(ClientCommandManager.argument("scale", IntegerArgumentType.integer(0, 6)).executes(context -> {
                                    var client = context.getSource().getClient();
                                    client.options.getGuiScale().setValue(IntegerArgumentType.getInteger(context, "scale"));
                                    client.onResolutionChanged();
                                    return 1;
                                })))
                        // The token spell screen open: a circle drawn with the mouse, its radius a share (percent) of
                        // the biggest circle, as a hand would (a point every 3 window pixels, a little more than a turn)
                        .then(ClientCommandManager.literal("circle")
                                .then(ClientCommandManager.argument("percent", IntegerArgumentType.integer(1, 100)).executes(context -> {
                                    var client = context.getSource().getClient();
                                    if (!(client.currentScreen instanceof fr.lordfinn.steveparty.client.screens.TokenSpellScreen screen)) return 0;
                                    double radius = IntegerArgumentType.getInteger(context, "percent") / 100.0
                                            * Math.max(40, Math.min(screen.width, screen.height) / 2.0 - 6);
                                    double cx = screen.width / 2.0, cy = screen.height / 2.0;
                                    double step = 3 / client.getWindow().getScaleFactor();
                                    int points = (int) Math.ceil(1.08 * Math.PI * 2 * radius / step);
                                    screen.mouseClicked(cx + radius, cy, 0);
                                    for (int i = 1; i <= points; i++) {
                                        double angle = 1.08 * Math.PI * 2 * i / points;
                                        screen.mouseDragged(cx + radius * Math.cos(angle), cy + radius * Math.sin(angle), 0, 0, 0);
                                    }
                                    double end = 1.08 * Math.PI * 2;
                                    screen.mouseReleased(cx + radius * Math.cos(end), cy + radius * Math.sin(end), 0);
                                    return 1;
                                })))
                        // The wheel of the tool in hand, open, its cursor at (x, y) from its centre (GUI pixels)
                        .then(ClientCommandManager.literal("wheel")
                                .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer()).executes(context -> {
                                            double scale = context.getSource().getClient().getWindow().getScaleFactor();
                                            ToolWheel.reopen();
                                            ToolWheel.moveCursor(IntegerArgumentType.getInteger(context, "x") * scale,
                                                    IntegerArgumentType.getInteger(context, "y") * scale);
                                            return 1;
                                        }))))));
    }
}
