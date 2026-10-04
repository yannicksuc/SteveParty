package fr.lordfinn.steveparty.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.message.ChatVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Dev clients only ({@code -Dsteveparty.dev.showcaseCamera=true}, set by gradle/dev-server.gradle): lets the server set
 * up this client for photos (the art sources) without touching its window. The server sends
 * {@code tellraw <player> {"text":"#CAM <order>"}}; the message is not shown, the order runs at the end of the tick:
 * <ul>
 *   <li>{@code hud on|off} (F1), {@code chat on|off}, {@code toasts} (clears the toasts), {@code fov <degrees>},
 *   {@code gui <scale>}</li>
 *   <li>{@code press use|attack}: one click of that key (open the aimed block's screen, roll the dice in hand)</li>
 *   <li>{@code slot <0-8>}, {@code close} (closes the open screen), {@code wait <ticks>} (delays the next orders)</li>
 * </ul>
 */
public final class ShowcaseCamera {
    private static final Logger LOG = LoggerFactory.getLogger("steveparty-showcase");
    private static final String PREFIX = "#CAM ";
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int waitTicks;

    private ShowcaseCamera() {
    }

    public static void initialize() {
        if (!Boolean.getBoolean("steveparty.dev.showcaseCamera")) return;
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            String text = message.getString();
            if (!text.startsWith(PREFIX)) return true;
            QUEUE.addLast(text.substring(PREFIX.length()).trim());
            return false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(ShowcaseCamera::tick);
    }

    private static void tick(MinecraftClient client) {
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        while (!QUEUE.isEmpty() && waitTicks == 0) {
            run(client, QUEUE.pollFirst());
        }
    }

    private static void run(MinecraftClient client, String order) {
        String[] a = order.split("\\s+");
        try {
            switch (a[0]) {
                case "hud" -> client.options.hudHidden = !"on".equals(a[1]);
                case "chat" -> client.options.getChatVisibility().setValue("on".equals(a[1]) ? ChatVisibility.FULL : ChatVisibility.HIDDEN);
                case "toasts" -> client.getToastManager().clear();
                case "fov" -> client.options.getFov().setValue(Integer.parseInt(a[1]));
                case "gui" -> {
                    client.options.getGuiScale().setValue(Integer.parseInt(a[1]));
                    client.onResolutionChanged();
                }
                case "press" -> {
                    KeyBinding key = "attack".equals(a[1]) ? client.options.attackKey : client.options.useKey;
                    KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(key));
                }
                case "slot" -> {
                    if (client.player != null) client.player.getInventory().selectedSlot = Integer.parseInt(a[1]);
                }
                case "close" -> client.setScreen(null);
                case "wait" -> waitTicks = Integer.parseInt(a[1]);
                default -> LOG.warn("[showcase] unknown order {}", order);
            }
        } catch (RuntimeException e) {
            LOG.warn("[showcase] bad order {}", order, e);
        }
    }
}
