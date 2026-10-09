package fr.lordfinn.steveparty.gametest.kit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.lang.reflect.Method;

/**
 * SteveParty's game tests. The runner only keeps the templates' own chunks loaded: a class whose tests play beyond
 * the template says how far ({@link #landAround()}), and each of its tests starts once that land is loaded and ticks
 * entities ({@link TestArea#load}). An entity there used not to tick whenever the template lay near a chunk's edge.
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
