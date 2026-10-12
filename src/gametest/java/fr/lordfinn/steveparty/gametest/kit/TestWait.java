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

    /**
     * Runs {@code each} on every tick from the next one until the test ends. Unlike {@code runAtEveryTick}, which adds
     * all the ticks left at once, it may be called from within a wait: adding them while the waits are gone through
     * breaks the test.
     */
    public static void everyTick(TestContext context, Runnable each) {
        context.waitAndRun(1, () -> {
            each.run();
            everyTick(context, each);
        });
    }

    /**
     * Gives the server's other threads (chunks loaded, generated, saved; entities read) a few milliseconds. The test
     * server runs its ticks back to back: a test waiting for them by counting ticks alone loses the race on a busy
     * machine. Called on each tick a wait for such work comes up empty.
     */
    public static void letLoadersWork() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
