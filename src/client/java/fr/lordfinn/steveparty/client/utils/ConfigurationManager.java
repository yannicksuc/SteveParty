package fr.lordfinn.steveparty.client.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigurationManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "steveparty_client_config.json";
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), CONFIG_FILE_NAME);

    // Default configuration values
    private static ClientConfig config = new ClientConfig();

    public static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, ClientConfig.class);
            } catch (IOException e) {
                System.err.println("Failed to read SteveParty client config: " + e.getMessage());
            }
        } else {
            saveConfig(); // Create a new config file if it doesn't exist
        }
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            System.err.println("Failed to save SteveParty client config: " + e.getMessage());
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
}
