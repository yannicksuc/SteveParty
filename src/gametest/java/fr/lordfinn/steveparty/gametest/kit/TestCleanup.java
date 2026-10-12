package fr.lordfinn.steveparty.gametest.kit;

import com.mojang.logging.LogUtils;
import fr.lordfinn.steveparty.gametest.mixin.TestContextAccessor;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.test.GameTestState;
import net.minecraft.test.TestContext;
import net.minecraft.test.TestListener;
import net.minecraft.test.TestRunContext;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a test undoes when it ends, so that it leaves nothing behind for the tests running next to it. Not through
 * {@code addFinalTask}: a final task completes the test on its first tick, before any of its waits ran.
 */
public final class TestCleanup {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<TestContext, List<Runnable>> AT_END = new WeakHashMap<>();

    private TestCleanup() {
    }

    /** What to undo when the test ends, passed or failed (run together, in order). */
    public static void atEnd(TestContext context, Runnable task) {
        List<Runnable> tasks = AT_END.get(context);
        if (tasks == null) {
            List<Runnable> created = new ArrayList<>();
            AT_END.put(context, created);
            ((TestContextAccessor) context).steveparty$test().addListener(new Ending(created));
            tasks = created;
        }
        tasks.add(task);
    }

    /** Removes the block at the relative position {@code pos} if it is still {@code block}. */
    public static void removeIf(TestContext context, BlockPos pos, Block block) {
        if (context.getBlockState(pos).isOf(block)) context.removeBlock(pos);
    }

    /**
     * Runs the tasks once, when the test passes or fails, right after the tick it ended in (it may end inside its own
     * wait loop, other waits of that tick still to run); one failing task does not keep the others from running.
     */
    private record Ending(List<Runnable> tasks) implements TestListener {
        @Override
        public void onStarted(GameTestState test) {
        }

        @Override
        public void onPassed(GameTestState test, TestRunContext context) {
            run(test);
        }

        @Override
        public void onFailed(GameTestState test, TestRunContext context) {
            run(test);
        }

        @Override
        public void onRetry(GameTestState lastState, GameTestState nextState, TestRunContext context) {
        }

        private void run(GameTestState test) {
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            MinecraftServer server = test.getWorld().getServer();
            server.send(new ServerTask(server.getTicks(), () -> runAll(test, pending)));
        }

        private static void runAll(GameTestState test, List<Runnable> pending) {
            for (Runnable task : pending) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    LOGGER.warn("Cleanup of {} failed", test.getTemplatePath(), e);
                }
            }
        }
    }
}
