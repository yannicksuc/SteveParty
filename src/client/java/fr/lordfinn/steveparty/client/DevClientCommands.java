package fr.lordfinn.steveparty.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/**
 * Dev runs only (never registered in a released game): client commands that press the player's buttons, so that the
 * dev game can be driven from the outside like a player would, e.g. {@code /sptest click} for a left click.
 */
public final class DevClientCommands {
    private DevClientCommands() {
    }

    /** A mouse position pinned by {@code /sptest cursor} (GUI pixels), or null. */
    private static double[] pinnedCursor;

    /** Puts the mouse at the pinned position (the window may be in the background: no real cursor moves). */
    private static void pinCursor(net.minecraft.client.MinecraftClient client) {
        if (pinnedCursor == null) return;
        try {
            double scale = client.getWindow().getScaleFactor();
            for (String name : new String[]{"x", "y"}) {
                Field field = net.minecraft.client.Mouse.class.getDeclaredField(name);
                field.setAccessible(true);
                field.setDouble(client.mouse, pinnedCursor[name.equals("x") ? 0 : 1] * scale);
            }
        } catch (ReflectiveOperationException ignored) {
            pinnedCursor = null;
        }
    }

    public static void initialize() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(DevClientCommands::pinCursor);
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
                        // The mouse wheel turned (vertical notches, + up), through Mouse#onMouseScroll like a real one
                        .then(ClientCommandManager.literal("scroll")
                                .then(ClientCommandManager.argument("notches", IntegerArgumentType.integer(-10, 10)).executes(context -> {
                                    var client = context.getSource().getClient();
                                    int notches = IntegerArgumentType.getInteger(context, "notches");
                                    client.execute(() -> {
                                        try {
                                            java.lang.reflect.Method scroll = net.minecraft.client.Mouse.class.getDeclaredMethod(
                                                    "onMouseScroll", long.class, double.class, double.class);
                                            scroll.setAccessible(true);
                                            scroll.invoke(client.mouse, client.getWindow().getHandle(), 0.0, (double) notches);
                                        } catch (ReflectiveOperationException e) {
                                            throw new IllegalStateException(e);
                                        }
                                    });
                                    return 1;
                                })))
                        // The Stencil Hammer in hand: its refill screen
                        .then(ClientCommandManager.literal("hammer").executes(context -> {
                            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                    new fr.lordfinn.steveparty.payloads.custom.ToolWheelPayload(
                                            fr.lordfinn.steveparty.payloads.custom.ToolWheelPayload.Action.HAMMER_OPEN, 0));
                            return 1;
                        }))
                        // The mouse pinned at (x, y) in GUI pixels of the window, as if it hovered there ("off": released)
                        .then(ClientCommandManager.literal("cursor")
                                .then(ClientCommandManager.literal("off").executes(context -> {
                                    pinnedCursor = null;
                                    return 1;
                                }))
                                .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer()).executes(context -> {
                                            pinnedCursor = new double[]{IntegerArgumentType.getInteger(context, "x"),
                                                    IntegerArgumentType.getInteger(context, "y")};
                                            pinCursor(context.getSource().getClient());
                                            return 1;
                                        }))))
                        // A left click of the open screen where the mouse is pinned (a slot picked or put down)
                        .then(ClientCommandManager.literal("screenclick").executes(context -> {
                            var client = context.getSource().getClient();
                            client.execute(() -> {
                                if (client.currentScreen == null || pinnedCursor == null) return;
                                client.currentScreen.mouseClicked(pinnedCursor[0], pinnedCursor[1], 0);
                                client.currentScreen.mouseReleased(pinnedCursor[0], pinnedCursor[1], 0);
                            });
                            return 1;
                        }))
                        // Hitboxes shown or not (F3 + B)
                        .then(ClientCommandManager.literal("hitboxes")
                                .then(ClientCommandManager.argument("shown", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(context -> {
                                    context.getSource().getClient().getEntityRenderDispatcher().setRenderHitboxes(
                                            com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "shown"));
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
