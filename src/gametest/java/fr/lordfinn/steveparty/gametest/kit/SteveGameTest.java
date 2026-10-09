package fr.lordfinn.steveparty.gametest.kit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.lang.reflect.Method;

/**
 * SteveParty's game tests: each one starts only once the land it plays on is loaded and ticks entities
 * ({@link TestArea#load}): its template and, for the classes whose tests play beyond it, {@link #landAround()} blocks
 * around it. The runner only keeps the templates' own chunks loaded: an entity beyond them did not tick whenever the
 * template happened to lie near a chunk's edge that run.
 */
public interface SteveGameTest extends FabricGameTest {
    /**
     * How far beyond its template a test of this class plays, in blocks (its land loaded on the spot: it costs the
     * server some time, only the classes that need it ask for it).
     */
    default int landAround() {
        return 0;
    }

    @Override
    default void invokeTestMethod(TestContext context, Method method) {
        GameTest test = method.getAnnotation(GameTest.class);
        TestArea.load(context, landAround(), test != null ? test.tickLimit() : 100);
        invokeWhenReady(context, method);
    }

    /** Runs the test itself, its land ready: what a test class overrides to wrap its tests. */
    default void invokeWhenReady(TestContext context, Method method) {
        FabricGameTest.super.invokeTestMethod(context, method);
    }
}
