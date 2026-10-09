package fr.lordfinn.steveparty.gametest.kit;

import net.minecraft.block.Block;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** What a test undoes when it ends, so that it leaves nothing behind for the tests running next to it. */
public final class TestCleanup {
    private static final Map<TestContext, List<Runnable>> AT_END = new WeakHashMap<>();

    private TestCleanup() {
    }

    /** What to undo when the test ends (a test has one final task: they are run together, in order). */
    public static void atEnd(TestContext context, Runnable task) {
        List<Runnable> tasks = AT_END.get(context);
        if (tasks == null) {
            List<Runnable> created = new ArrayList<>();
            AT_END.put(context, created);
            context.addFinalTask(() -> created.forEach(Runnable::run));
            tasks = created;
        }
        tasks.add(task);
    }

    /** Removes the block at the relative position {@code pos} if it is still {@code block}. */
    public static void removeIf(TestContext context, BlockPos pos, Block block) {
        if (context.getBlockState(pos).isOf(block)) context.removeBlock(pos);
    }
}
