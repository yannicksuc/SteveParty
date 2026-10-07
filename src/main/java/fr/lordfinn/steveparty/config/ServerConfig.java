package fr.lordfinn.steveparty.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The server settings of the mod, all in {@code config/steveparty.json}: the mini-game bubble, the cap of Mula spawn
 * sites, the extra switchable blocks. Written with its defaults the first time, and again when it lacks a setting this
 * version knows; a missing or broken file gives the defaults. Read when the mod starts and every time a server does
 * (the switchable blocks also on {@code /reload}).
 * <p>
 * The switchable blocks used to be in a file of their own, {@code config/steveparty/server.json}
 * ({@code "switchable_blocks"}): found, its list is taken into this file once, and it is renamed
 * {@code server.json.migrated}.
 */
public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "steveparty.json";
    /** The former file of the switchable blocks, and its key. */
    private static final String OLD_SWITCHABLE_FILE = "steveparty/server.json";
    private static final String OLD_SWITCHABLE_KEY = "switchable_blocks";
    private static ServerConfig current = new ServerConfig();

    /**
     * « Remise en état des arènes », the master switch: off, no round is played in a bubble anywhere, whatever its page
     * says (nothing is journaled, swapped or guarded). On, the pages with a zone and their « Remettre l'arène en état »
     * option on have one.
     */
    public boolean miniGameBubble = true;
    /** The longest side a zone may have, in blocks: a bigger zone starts no session. */
    public int miniGameBubbleMaxSize = 128;
    /** The most block positions a session remembers: past it, the blocks not changed yet can't be changed any more. */
    public int miniGameBubbleMaxJournal = 262_144;
    /** The most block entities (chests, signs...) a zone may hold when a session starts. */
    public int miniGameBubbleMaxBlockEntities = 1024;
    /** The most entities (players aside) a zone may hold when a session starts. */
    public int miniGameBubbleMaxEntities = 1024;
    /** The blocks put back per tick at the end of the sessions, all zones being restored sharing it: a bigger journal is restored over several ticks. */
    public int miniGameBubbleRestorePerTick = 2048;
    /**
     * Blocks a mini-game zone may not hold, besides those of the block tag {@code steveparty:zone_forbidden}: an id
     * ({@code modid:block}), a tag ({@code #namespace:tag}) or a whole mod ({@code modid:*}). See
     * {@code ZoneForbidden}.
     */
    public List<String> miniGameBubbleForbiddenBlocks = new ArrayList<>();
    /** Items the members of a session may not pick up, use or take from a container: same entries, same tag (of items). */
    public List<String> miniGameBubbleForbiddenItems = new ArrayList<>();
    /** Entities a mini-game zone may not hold: same entries, same tag (of entity types). */
    public List<String> miniGameBubbleForbiddenEntities = new ArrayList<>();
    /**
     * The most Mula spawn sites (the places where an ephemeride brought Mulas down) a dimension keeps: one more, and
     * the oldest is retired with its wild Mulas (MulaSpawnSites).
     */
    public int mulaMaxSites = 10;
    /**
     * The most tamed Mulas following one player at a time (MulaEscorts): the others stay where they are until a place
     * frees up (one sits down, is given away, is left in another dimension...).
     */
    public int mulaMaxFollowers = 16;
    /**
     * Extra blocks the hop switch can make disappear, besides the block tag {@code steveparty:switchable}: block ids
     * ({@code "minecraft:stone"}) or block tags ({@code "#minecraft:wool"}). Blocks with a block entity are ignored.
     * See {@code SwitchableConfig}.
     */
    public List<String> switchableBlocks = new ArrayList<>();

    public static ServerConfig get() {
        return current;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        current = loadFrom(FabricLoader.getInstance().getConfigDir());
    }

    /** The settings of the config directory {@code configDir} (its file written or migrated when needed). */
    public static ServerConfig loadFrom(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        ServerConfig loaded = null;
        boolean complete = true;
        if (Files.isRegularFile(file)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                loaded = GSON.fromJson(text, ServerConfig.class);
                // written again with the settings it does not have yet (their defaults), so that they can be found
                JsonObject there = GSON.fromJson(text, JsonObject.class);
                if (loaded != null && there != null) complete = there.keySet().containsAll(GSON.toJsonTree(loaded).getAsJsonObject().keySet());
            } catch (IOException | JsonParseException e) {
                Steveparty.LOGGER.warn("Can't read {}: the default settings are used", file, e);
            }
        }
        boolean write = loaded == null ? !Files.exists(file) : !complete;
        ServerConfig config = loaded == null ? new ServerConfig() : loaded.sane();
        Path old = configDir.resolve(OLD_SWITCHABLE_FILE);
        // never over a file there but broken: it stays as it is, for its owner to mend
        boolean migrate = Files.isRegularFile(old) && (loaded != null || !Files.exists(file));
        if (migrate) {
            List<String> blocks = readOldSwitchableBlocks(old);
            // the entries of this file win: the old list only fills an empty one
            if (blocks != null && config.switchableBlocks.isEmpty()) config.switchableBlocks = blocks;
            write = true;
        }
        if (!write) return config;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);
            if (migrate) {
                Files.move(old, old.resolveSibling(old.getFileName() + ".migrated"), StandardCopyOption.REPLACE_EXISTING);
                Steveparty.LOGGER.info("The switchable blocks of {} are now in {} (\"switchableBlocks\")", old, file);
            }
        } catch (IOException e) {
            Steveparty.LOGGER.warn("Can't write {}", file, e);
        }
        return config;
    }

    /**
     * {@code /reload}: the switchable blocks as the file says now, the other settings unchanged until the next start
     * (as before they were in one file).
     */
    public static void reloadSwitchableBlocks() {
        Path file = file();
        if (!Files.isRegularFile(file)) return;
        try {
            ServerConfig read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ServerConfig.class);
            if (read != null) current.switchableBlocks = read.switchableBlocks == null ? new ArrayList<>() : read.switchableBlocks;
        } catch (IOException | JsonParseException e) {
            Steveparty.LOGGER.warn("Can't read {}: the switchable blocks are unchanged", file, e);
        }
    }

    /** The list of the former file, null if it can't be read (it is renamed all the same: its list was empty or broken). */
    private static @Nullable List<String> readOldSwitchableBlocks(Path old) {
        try {
            JsonObject root = GSON.fromJson(Files.readString(old, StandardCharsets.UTF_8), JsonObject.class);
            JsonArray array = root == null ? null : root.getAsJsonArray(OLD_SWITCHABLE_KEY);
            if (array == null) return null;
            List<String> blocks = new ArrayList<>();
            for (JsonElement entry : array) blocks.add(entry.getAsString());
            return blocks;
        } catch (IOException | JsonParseException | IllegalStateException | ClassCastException e) {
            Steveparty.LOGGER.warn("Can't read {}: its switchable blocks are not taken", old, e);
            return null;
        }
    }

    private ServerConfig sane() {
        miniGameBubbleMaxSize = Math.max(1, miniGameBubbleMaxSize);
        miniGameBubbleMaxJournal = Math.max(1, miniGameBubbleMaxJournal);
        miniGameBubbleMaxBlockEntities = Math.max(0, miniGameBubbleMaxBlockEntities);
        miniGameBubbleMaxEntities = Math.max(0, miniGameBubbleMaxEntities);
        miniGameBubbleRestorePerTick = Math.max(16, miniGameBubbleRestorePerTick);
        if (miniGameBubbleForbiddenBlocks == null) miniGameBubbleForbiddenBlocks = new ArrayList<>();
        if (miniGameBubbleForbiddenItems == null) miniGameBubbleForbiddenItems = new ArrayList<>();
        if (miniGameBubbleForbiddenEntities == null) miniGameBubbleForbiddenEntities = new ArrayList<>();
        if (switchableBlocks == null) switchableBlocks = new ArrayList<>();
        mulaMaxSites = Math.max(1, mulaMaxSites);
        mulaMaxFollowers = Math.max(1, mulaMaxFollowers);
        return this;
    }
}
