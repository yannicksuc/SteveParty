package fr.lordfinn.steveparty.client.gui.wheel;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * How well the player knows each tool's wheel (this computer only, in the config folder): the first times a wheel
 * opens it puts one sector forward, until that sector has been picked once or the wheel opened {@link #LEARNING} times.
 */
final class ToolWheelHints {
    /** Openings during which the featured sector is put forward. */
    private static final int LEARNING = 3;
    private static final String LEARNED = "learned";
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("steveparty-wheel-hints.properties");
    private static Properties properties;

    private ToolWheelHints() {
    }

    /** Whether the wheel {@code key} still puts its featured sector forward. */
    static boolean onboarding(String key) {
        String value = properties().getProperty(key, "0");
        if (LEARNED.equals(value)) return false;
        try {
            return Integer.parseInt(value) < LEARNING;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** The wheel {@code key} opened once more. */
    static void opened(String key) {
        if (!onboarding(key)) return;
        int count;
        try {
            count = Integer.parseInt(properties().getProperty(key, "0"));
        } catch (NumberFormatException e) {
            count = LEARNING;
        }
        properties().setProperty(key, Integer.toString(count + 1));
        save();
    }

    /** Its featured sector was picked: the wheel {@code key} is known. */
    static void learned(String key) {
        if (LEARNED.equals(properties().getProperty(key))) return;
        properties().setProperty(key, LEARNED);
        save();
    }

    private static Properties properties() {
        if (properties == null) {
            properties = new Properties();
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    properties.load(reader);
                } catch (IOException e) {
                    Steveparty.LOGGER.warn("Could not read {}: {}", FILE, e.getMessage());
                }
            }
        }
        return properties;
    }

    private static void save() {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            properties.store(writer, "Steve Party: the tool wheels already learned");
        } catch (IOException e) {
            Steveparty.LOGGER.warn("Could not write {}: {}", FILE, e.getMessage());
        }
    }
}
