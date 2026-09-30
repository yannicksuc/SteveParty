package fr.lordfinn.steveparty.client.gui.cartridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Client option of the cartridge menus: the electric current animation (on by default). Turned off with the lightning
 * button of a cartridge's label, or by the game's « Hide Lightning Flashes » accessibility option. Saved in
 * {@code config/steveparty-cartridge-gui.json}.
 */
public final class CartridgeGuiConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("steveparty");
    private static final String FILE_NAME = "steveparty-cartridge-gui.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Saved {
        boolean currentAnimation = true;
    }

    private static Saved saved = new Saved();
    private static boolean loaded;

    private CartridgeGuiConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static void load() {
        loaded = true;
        Path file = file();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Saved read = GSON.fromJson(reader, Saved.class);
            if (read != null) saved = read;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Unreadable {}, using the default cartridge menu options", FILE_NAME, e);
        }
    }

    /** The player's own choice (the lightning button). */
    public static boolean currentAnimation() {
        if (!loaded) load();
        return saved.currentAnimation;
    }

    /** Whether the current animation plays: the player's choice, unless the game hides lightning flashes. */
    public static boolean animate() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hideFlashes = client != null && client.options != null && client.options.getHideLightningFlashes().getValue();
        return currentAnimation() && !hideFlashes;
    }

    public static void setCurrentAnimation(boolean on) {
        if (!loaded) load();
        saved.currentAnimation = on;
        save();
    }

    /** Writes to a temporary file then moves it over the options, so a crash never leaves a truncated file. */
    private static void save() {
        Path file = file();
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(saved, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save the cartridge menu options", e);
        }
    }
}
