package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The bytes of the pages' pictures: one file per picture in {@code <world>/steveparty/minigame_pages/}, named by its
 * hash ({@code <hash>.png} or {@code <hash>.jpg}). Too heavy for an item or for the saved state, which is rewritten
 * whole at every change. The last pictures read stay in memory.
 */
public final class MiniGamePageImageStore {
    private static final int CACHED = 16;
    private static final Map<String, byte[]> CACHE = new LinkedHashMap<>(CACHED, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > CACHED;
        }
    };

    private MiniGamePageImageStore() {
    }

    public static Path directory(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("steveparty").resolve("minigame_pages");
    }

    private static Path file(MinecraftServer server, String hash, MiniGamePageImages.Format format) {
        return directory(server).resolve(hash + "." + format.extension());
    }

    /** Writes a picture under its hash. @return false if it could not be written */
    public static synchronized boolean write(MinecraftServer server, String hash, byte[] bytes) {
        MiniGamePageImages.Format format = MiniGamePageImages.formatOf(bytes);
        if (format == null || !MiniGamePageImage.isHash(hash)) return false;
        try {
            Path target = file(server, hash, format);
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            CACHE.put(hash, bytes);
            return true;
        } catch (IOException e) {
            Steveparty.LOGGER.error("Can't save the mini-game page picture {}", hash, e);
            return false;
        }
    }

    /** @return the bytes of a picture, null if it is not stored (or can't be read) */
    public static synchronized byte @Nullable [] read(MinecraftServer server, String hash) {
        if (!MiniGamePageImage.isHash(hash)) return null;
        byte[] cached = CACHE.get(hash);
        if (cached != null) return cached;
        for (MiniGamePageImages.Format format : MiniGamePageImages.Format.values()) {
            Path path = file(server, hash, format);
            if (!Files.isRegularFile(path)) continue;
            try {
                if (Files.size(path) > MiniGamePageImages.MAX_BYTES) return null;
                byte[] bytes = Files.readAllBytes(path);
                CACHE.put(hash, bytes);
                return bytes;
            } catch (IOException e) {
                Steveparty.LOGGER.error("Can't read the mini-game page picture {}", hash, e);
                return null;
            }
        }
        return null;
    }

    public static synchronized void delete(MinecraftServer server, String hash) {
        if (!MiniGamePageImage.isHash(hash)) return;
        CACHE.remove(hash);
        for (MiniGamePageImages.Format format : MiniGamePageImages.Format.values()) {
            try {
                Files.deleteIfExists(file(server, hash, format));
            } catch (IOException e) {
                Steveparty.LOGGER.warn("Can't delete the mini-game page picture {}", hash, e);
            }
        }
    }

    /** The memory is per world: forget it when the server stops. */
    public static synchronized void clearCache() {
        CACHE.clear();
    }
}
