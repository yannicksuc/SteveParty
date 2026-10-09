package fr.lordfinn.steveparty.client.minigame;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.minigame.MiniGamePageImages;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Picks a picture on the player's computer for a mini-game page: the system's file window (PNG and JPEG), then the
 * picture is read, shrunk and compressed ({@link MiniGamePageImages#prepare}) off the render thread.
 */
public final class PageImagePicker {
    /** Files bigger than this are not even opened. */
    private static final long MAX_FILE_BYTES = 40L * 1024 * 1024;
    /** Pictures with more pixels than this are not decoded (memory). */
    private static final long MAX_PIXELS = 12_000L * 12_000L;

    /**
     * What came out of a pick.
     *
     * @param bytes the picture as it will be sent (null: nothing to send)
     * @param image the same picture, decoded, to show it at once
     * @param error why there is no picture (null when the player just closed the window)
     */
    public record Result(byte @Nullable [] bytes, @Nullable BufferedImage image, @Nullable Text error) {
        public boolean cancelled() {
            return bytes == null && error == null;
        }
    }

    private PageImagePicker() {
    }

    /** Opens the file window; {@code done} is called on the render thread with the picture, ready to be sent. */
    public static void pick(Consumer<Result> done) {
        // Texts are read here, on the render thread
        String title = Text.translatable("gui.steveparty.mini_game_page.image.dialog").getString();
        String filter = Text.translatable("gui.steveparty.mini_game_page.image.dialog.filter").getString();
        run(() -> {
            String path = openDialog(title, filter);
            return path == null ? new Result(null, null, null) : load(Path.of(path));
        }, done);
    }

    /** Reads a file the player dropped on the window; {@code done} is called on the render thread. */
    public static void read(Path file, Consumer<Result> done) {
        run(() -> load(file), done);
    }

    private static void run(Supplier<Result> work, Consumer<Result> done) {
        Thread thread = new Thread(() -> {
            Result result;
            try {
                result = work.get();
            } catch (Throwable e) {
                Steveparty.LOGGER.warn("Can't read the picture of a mini-game page", e);
                result = failure("unreadable");
            }
            Result finished = result;
            MinecraftClient.getInstance().execute(() -> done.accept(finished));
        }, "SteveParty page picture");
        thread.setDaemon(true);
        thread.start();
    }

    private static @Nullable String openDialog(String title, String filter) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(3);
            filters.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg"));
            filters.flip();
            return TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, filter, false);
        }
    }

    private static Result failure(String reason) {
        return new Result(null, null, Text.translatable("gui.steveparty.mini_game_page.image.error." + reason));
    }

    private static Result load(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg")) return failure("format");
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) return failure("too_big");
            BufferedImage source = decode(file);
            if (source == null) return failure("unreadable");
            byte[] bytes = MiniGamePageImages.prepare(source);
            if (bytes == null || !MiniGamePageImages.isAcceptable(bytes)) return failure("unreadable");
            // What the others will see: the compressed picture, not the file
            BufferedImage shown = MiniGamePageImages.decode(bytes);
            return shown == null ? failure("unreadable") : new Result(bytes, shown, null);
        } catch (IOException | RuntimeException e) {
            Steveparty.LOGGER.warn("Can't read the picture {}", file, e);
            return failure("unreadable");
        }
    }

    /** Decodes the file, unless its header announces a picture too big to hold in memory. */
    private static @Nullable BufferedImage decode(Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) return null;
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }
}
