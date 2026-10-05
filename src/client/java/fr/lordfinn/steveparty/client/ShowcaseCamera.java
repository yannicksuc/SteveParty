package fr.lordfinn.steveparty.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.message.ChatVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dev clients only ({@code -Dsteveparty.dev.showcaseCamera=true}, set by gradle/dev-server.gradle): lets the server set
 * up this client for photos (the art sources) without touching its window. The server sends
 * {@code tellraw <player> {"text":"#CAM <order>"}}; the message is not shown, the order runs at the end of the tick:
 * <ul>
 *   <li>{@code hud on|off} (F1), {@code chat on|off}, {@code toasts} (clears the toasts), {@code fov <degrees>},
 *   {@code gui <scale>}</li>
 *   <li>{@code press use|attack}: one click of that key (open the aimed block's screen, roll the dice in hand)</li>
 *   <li>{@code slot <0-8>}, {@code close} (closes the open screen), {@code wait <ticks>} (delays the next orders)</li>
 *   <li>{@code view first|back|front} (F5), {@code record <folder> <frames>}: one frame per client tick (20 a second)
 *   into {@code <folder>/frame_NNNN.png} (an absolute path, or a folder of the run dir's screenshots/)</li>
 * </ul>
 */
public final class ShowcaseCamera {
    private static final Logger LOG = LoggerFactory.getLogger("steveparty-showcase");
    private static final String PREFIX = "#CAM ";
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int waitTicks;
    private static final ExecutorService WRITER = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "steveparty-showcase-frames");
        thread.setDaemon(true);
        return thread;
    });
    private static Path recordDir;
    private static int recordFrames, recordIndex;

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
        if (recordFrames > 0) recordFrame(client);
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        while (!QUEUE.isEmpty() && waitTicks == 0) {
            run(client, QUEUE.pollFirst());
        }
    }

    /**
     * One frame of a clip (20 a second: one per client tick), written as a PNG off the render thread. Read at the start
     * of the next frame (a task), when the framebuffer holds the last finished frame: read during the tick, it held
     * a frame still being drawn (shaders).
     */
    private static void recordFrame(MinecraftClient client) {
        Path file = recordDir.resolve(String.format("frame_%04d.png", recordIndex++));
        if (--recordFrames == 0) LOG.info("[showcase] recorded {} frames into {}", recordIndex, recordDir);
        client.execute(() -> saveFrame(client, file));
    }

    private static void saveFrame(MinecraftClient client, Path file) {
        NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
        WRITER.execute(() -> {
            try (image) {
                image.writeTo(file);
            } catch (IOException e) {
                LOG.warn("[showcase] frame {}", file, e);
            }
        });
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
                case "view" -> client.options.setPerspective(switch (a[1]) {
                    case "back" -> Perspective.THIRD_PERSON_BACK;
                    case "front" -> Perspective.THIRD_PERSON_FRONT;
                    default -> Perspective.FIRST_PERSON;
                });
                case "record" -> {
                    // a folder name (under the run dir's screenshots/) or an absolute path
                    recordDir = client.runDirectory.toPath().resolve("screenshots").resolve(a[1].replace('/', java.io.File.separatorChar));
                    Files.createDirectories(recordDir);
                    recordFrames = Integer.parseInt(a[2]);
                    recordIndex = 0;
                }
                default -> LOG.warn("[showcase] unknown order {}", order);
            }
        } catch (RuntimeException | IOException e) {
            LOG.warn("[showcase] bad order {}", order, e);
        }
    }
}
