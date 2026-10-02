package fr.lordfinn.steveparty.minigame.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The server settings (the mini-game bubble, the cap of Mula spawn sites), kept in {@code config/steveparty.json} (written with its defaults the
 * first time; a missing or broken file gives the defaults). Read once when the mod starts.
 */
public final class ZoneBubbleConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "steveparty.json";
    private static ZoneBubbleConfig current = new ZoneBubbleConfig();

    /** The whole feature: off, a session starts no bubble (nothing is journaled, swapped or guarded). */
    public boolean miniGameBubble = true;
    /** The longest side a zone may have, in blocks: a bigger zone starts no session. */
    public int miniGameBubbleMaxSize = 128;
    /** The most block positions a session remembers: past it, the blocks not changed yet can't be changed any more. */
    public int miniGameBubbleMaxJournal = 262_144;
    /** The most block entities (chests, signs...) a zone may hold when a session starts. */
    public int miniGameBubbleMaxBlockEntities = 1024;
    /** The most entities (players aside) a zone may hold when a session starts. */
    public int miniGameBubbleMaxEntities = 1024;
    /** The blocks put back per tick at the end of a session: a bigger journal is restored over several ticks. */
    public int miniGameBubbleRestorePerTick = 2048;
    /**
     * The most Mula spawn sites (the places where an ephemeride brought Mulas down) a dimension keeps: one more, and
     * the oldest is retired with its wild Mulas (MulaSpawnSites).
     */
    public int mulaMaxSites = 10;

    public static ZoneBubbleConfig get() {
        return current;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        ZoneBubbleConfig loaded = null;
        if (Files.isRegularFile(file)) {
            try {
                loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ZoneBubbleConfig.class);
            } catch (IOException | JsonParseException e) {
                Steveparty.LOGGER.warn("Can't read {}: the default settings are used", file, e);
            }
        }
        boolean write = loaded == null && !Files.exists(file);
        current = loaded == null ? new ZoneBubbleConfig() : loaded.sane();
        if (!write) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(current), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Steveparty.LOGGER.warn("Can't write {}", file, e);
        }
    }

    private ZoneBubbleConfig sane() {
        miniGameBubbleMaxSize = Math.max(1, miniGameBubbleMaxSize);
        miniGameBubbleMaxJournal = Math.max(1, miniGameBubbleMaxJournal);
        miniGameBubbleMaxBlockEntities = Math.max(0, miniGameBubbleMaxBlockEntities);
        miniGameBubbleMaxEntities = Math.max(0, miniGameBubbleMaxEntities);
        miniGameBubbleRestorePerTick = Math.max(16, miniGameBubbleRestorePerTick);
        mulaMaxSites = Math.max(1, mulaMaxSites);
        return this;
    }
}
