package fr.lordfinn.steveparty.gametest.kit;

import net.minecraft.test.TestContext;

import java.util.function.BooleanSupplier;

/** Waiting, tick by tick, for something to happen in a test. */
public final class TestWait {
    private TestWait() {
    }

    /** Runs {@code then} as soon as {@code condition} holds, checked every tick, failing after {@code ticks}. */
    public static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
            return;
        }
        context.assertTrue(ticks > 0, "timed out: " + what);
        context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
    }
}
