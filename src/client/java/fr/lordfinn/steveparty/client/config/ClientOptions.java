package fr.lordfinn.steveparty.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import fr.lordfinn.steveparty.board.DestinationSwap;
import fr.lordfinn.steveparty.client.screens.DestinationSwapPromptScreen;
import fr.lordfinn.steveparty.payloads.custom.DestinationSwapPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;
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
 * The client options of the mod, saved in {@code config/steveparty-client.json}, changed in game or in the options
 * screen ({@link ClientOptionsScreen}, from Mod Menu).
 * <ul>
 *     <li>{@code destinationSwap}: a cartridge clicked on another one (or on a Tile item) swaps their destinations
 *     along with the items (true), or only the items (false); absent: not asked yet, the first such click asks
 *     ({@link DestinationSwapPromptScreen}). Sent to the server, which does the click.</li>
 * </ul>
 */
public final class ClientOptions {
    private static final Logger LOGGER = LoggerFactory.getLogger("steveparty");
    private static final String FILE_NAME = "steveparty-client.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Saved {
        @Nullable Boolean destinationSwap;
    }

    private static Saved saved = new Saved();
    private static boolean loaded;

    private ClientOptions() {
    }

    public static void initialize() {
        DestinationSwap.clientPreference = ClientOptions::destinationSwap;
        DestinationSwap.askClient = () -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (!(client.currentScreen instanceof DestinationSwapPromptScreen)) {
                    client.setScreen(new DestinationSwapPromptScreen(client.currentScreen));
                }
            });
        };
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sendDestinationSwap());
    }

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
            LOGGER.warn("Unreadable {}, using the default client options", FILE_NAME, e);
        }
    }

    public static DestinationSwap.Preference destinationSwap() {
        if (!loaded) load();
        if (saved.destinationSwap == null) return DestinationSwap.Preference.UNSET;
        return saved.destinationSwap ? DestinationSwap.Preference.ON : DestinationSwap.Preference.OFF;
    }

    /** Sets the option (null: ask again), saves it and tells the server. */
    public static void setDestinationSwap(@Nullable Boolean on) {
        if (!loaded) load();
        saved.destinationSwap = on;
        save();
        sendDestinationSwap();
    }

    private static void sendDestinationSwap() {
        if (ClientPlayNetworking.canSend(DestinationSwapPayload.ID)) {
            ClientPlayNetworking.send(new DestinationSwapPayload(destinationSwap()));
        }
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
            LOGGER.error("Failed to save the client options", e);
        }
    }
}
