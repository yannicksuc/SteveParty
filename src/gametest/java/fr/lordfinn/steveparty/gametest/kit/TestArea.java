package fr.lordfinn.steveparty.gametest.kit;

import fr.lordfinn.steveparty.gametest.mixin.ServerChunkManagerTestInvoker;
import fr.lordfinn.steveparty.gametest.mixin.ServerWorldTestAccessor;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * The land a test plays on, its template and around it, loaded and ticking entities before the test starts.
 * <p>
 * The game test runner only keeps its templates' own chunks loaded: an entity a few blocks beyond them (a strip 20
 * blocks long, a pig shot 10 blocks away) stood in a chunk that does not tick entities whenever the template lay near a
 * chunk's edge, the test then waiting in vain. And loading happens on other threads while the test server runs its
 * ticks back to back: on a busy machine, ticks pass before a chunk's entities are read. So the land is loaded on the
 * spot, the server thread waiting for it (in wall-clock time, not in the test's ticks).
 */
public final class TestArea {
    /** Tickets by how long they hold (a test's tick limit and a little more): they expire on their own. */
    private static final Map<Integer, ChunkTicketType<ChunkPos>> TICKETS = new HashMap<>();
    /** The longest wait for the land to be ready. */
    private static final long WAIT_NANOS = 30_000_000_000L;

    private TestArea() {
    }

    /**
     * Loads the land within {@code margin} blocks of the test's template for {@code ticks} ticks, and waits until every
     * chunk of it (the template's own included) ticks entities.
     */
    public static void load(TestContext context, int margin, int ticks) {
        ServerWorld world = context.getWorld();
        Box box = context.getTestBox().expand(margin, 0, margin);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = MathHelper.floor(box.minX) >> 4; cx <= MathHelper.floor(box.maxX) >> 4; cx++) {
            for (int cz = MathHelper.floor(box.minZ) >> 4; cz <= MathHelper.floor(box.maxZ) >> 4; cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        if (margin > 0) {
            ChunkTicketType<ChunkPos> ticket = TICKETS.computeIfAbsent(ticks + 20,
                    expiry -> ChunkTicketType.create("steveparty_gametest_" + expiry, Comparator.comparingLong(ChunkPos::toLong), expiry));
            for (ChunkPos chunk : chunks) world.getChunkManager().addTicket(ticket, chunk, 2, chunk); // level 31: entities tick
        }
        if (ready(world, chunks)) return;
        long deadline = System.nanoTime() + WAIT_NANOS;
        for (ChunkPos chunk : chunks) world.getChunk(chunk.x, chunk.z); // generated or read, on the spot
        while (!ready(world, chunks)) {
            if (System.nanoTime() > deadline) throw new GameTestException("the land around the test never loaded");
            ((ServerChunkManagerTestInvoker) world.getChunkManager()).steveparty$updateChunks(); // the tickets' levels
            world.getChunkManager().executeQueuedTasks();
            ((ServerWorldTestAccessor) world).steveparty$entityManager().tick(); // the entities read meanwhile
            LockSupport.parkNanos(1_000_000); // the loading threads' turn
        }
    }

    private static boolean ready(ServerWorld world, List<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) if (!world.shouldTickEntity(chunk.getStartPos())) return false;
        return true;
    }
}
