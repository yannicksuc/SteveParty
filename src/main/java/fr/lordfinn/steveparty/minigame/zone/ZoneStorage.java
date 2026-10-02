package fr.lordfinn.steveparty.minigame.zone;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * The files of the bubbles, in {@code <world>/steveparty/zone_bubbles/}: what a session has to put back is on disk
 * before the world it changed can be, so that a server that crashed restores the zones and gives the inventories
 * back when it starts again.
 * <ul>
 *   <li>{@code sessions/<session>/session.dat}: the zone, its block entities and its entities when the session began;</li>
 *   <li>{@code sessions/<session>/journal.log}: the block each changed position held, appended as they change;</li>
 *   <li>{@code players/<player>.dat}: the real inventory of a player who holds a session one.</li>
 * </ul>
 * Nothing is written on the server thread: the writes go, in order, to one thread of their own. The world is never
 * saved before them: {@link #flush()} waits for them, and is called before every save.
 */
final class ZoneStorage {
    static final String SESSION_FILE = "session.dat";
    static final String JOURNAL_FILE = "journal.log";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SteveParty zone bubble IO");
        thread.setDaemon(true);
        return thread;
    });

    private ZoneStorage() {
    }

    static Path root(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("steveparty").resolve("zone_bubbles");
    }

    static Path sessionDirectory(MinecraftServer server, UUID session) {
        return root(server).resolve("sessions").resolve(session.toString());
    }

    static Path playersDirectory(MinecraftServer server) {
        return root(server).resolve("players");
    }

    static Path stashFile(MinecraftServer server, UUID player) {
        return playersDirectory(server).resolve(player + ".dat");
    }

    /** Writes a file whole (never half written: a temporary file takes its place once complete). */
    static void write(Path file, NbtCompound nbt) {
        IO.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Path temp = file.resolveSibling(file.getFileName() + ".tmp");
                NbtIo.writeCompressed(nbt, temp);
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException e) {
                Steveparty.LOGGER.error("Can't write the mini-game bubble file {}", file, e);
            }
        });
    }

    /** Adds bytes at the end of a file. */
    static void append(Path file, byte[] bytes) {
        IO.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    out.write(bytes);
                }
            } catch (IOException | RuntimeException e) {
                Steveparty.LOGGER.error("Can't write the mini-game bubble file {}", file, e);
            }
        });
    }

    /** Deletes a file, or a directory and all it holds. */
    static void delete(Path path) {
        IO.execute(() -> {
            if (!Files.exists(path)) return;
            try (Stream<Path> files = Files.walk(path)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            } catch (IOException | RuntimeException e) {
                Steveparty.LOGGER.error("Can't delete the mini-game bubble file {}", path, e);
            }
        });
    }

    /** Waits until everything asked so far is on disk. */
    static void flush() {
        try {
            IO.submit(() -> {
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Steveparty.LOGGER.error("The mini-game bubble files could not be flushed", e);
        }
    }

    /** @return the content of a file, null if it is missing or broken (read once the pending writes are done) */
    static @Nullable NbtCompound read(Path file) {
        flush();
        if (!Files.isRegularFile(file)) return null;
        try {
            return NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
        } catch (IOException | RuntimeException e) {
            Steveparty.LOGGER.error("Can't read the mini-game bubble file {}", file, e);
            return null;
        }
    }

    /** @return the bytes of a file, null if it is missing (read once the pending writes are done) */
    static byte @Nullable [] readBytes(Path file) {
        flush();
        if (!Files.isRegularFile(file)) return null;
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            Steveparty.LOGGER.error("Can't read the mini-game bubble file {}", file, e);
            return null;
        }
    }

    /** @return the files (or directories) right under a directory (once the pending writes are done) */
    static List<Path> list(Path directory) {
        flush();
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> files = Files.list(directory)) {
            return new ArrayList<>(files.toList());
        } catch (IOException e) {
            Steveparty.LOGGER.error("Can't list the mini-game bubble files of {}", directory, e);
            return List.of();
        }
    }
}
