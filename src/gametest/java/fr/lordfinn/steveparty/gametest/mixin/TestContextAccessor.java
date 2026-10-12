package fr.lordfinn.steveparty.gametest.mixin;

import net.minecraft.test.GameTestState;
import net.minecraft.test.TestContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The test behind a context (TestCleanup): its listeners say when it really ends. */
@Mixin(TestContext.class)
public interface TestContextAccessor {
    @Accessor("test")
    GameTestState steveparty$test();
}
