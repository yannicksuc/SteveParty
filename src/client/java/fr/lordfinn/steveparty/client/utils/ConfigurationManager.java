package fr.lordfinn.steveparty.client.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
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

public class ConfigurationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("steveparty");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "steveparty_client_config.json";
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);

    // Default configuration values
    private static ClientConfig config = new ClientConfig();

    public static void loadConfig() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
                // An empty file deserializes to null
                config = loaded != null ? loaded : new ClientConfig();
            } catch (IOException | JsonParseException e) {
                LOGGER.error("Failed to read SteveParty client config, using defaults", e);
                config = new ClientConfig();
            }
        } else {
            saveConfig(); // Create a new config file if it doesn't exist
        }
    }

    /** Writes to a temporary file then moves it over the config, so a crash never leaves a truncated file. */
    public static synchronized void saveConfig() {
        Path temp = CONFIG_FILE.resolveSibling(CONFIG_FILE_NAME + ".tmp");
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            try {
                Files.move(temp, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save SteveParty client config", e);
        }
    }

    public static int getPartyStepsHudX() {
        return config.partyStepsHudX;
    }

    public static void setPartyStepsHudX(int hudX) {
        config.partyStepsHudX = hudX;
        saveConfig();
    }

    public static int getPartyStepsHudY() {
        return config.partyStepHudY;
    }

    public static void setPartyStepsHudY(int hudY) {
        config.partyStepHudY = hudY;
        saveConfig();
    }

    /** Updates both coordinates with a single write. */
    public static void setPartyStepsHudPosition(int hudX, int hudY) {
        config.partyStepsHudX = hudX;
        config.partyStepHudY = hudY;
        saveConfig();
    }
}
