package fr.lordfinn.steveparty.gametest.mixin;

import net.minecraft.test.GameTestState;
import net.minecraft.test.TestContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The test behind a context, to be told when it passes or fails (TestCleanup). */
@Mixin(TestContext.class)
public interface TestContextAccessor {
    @Accessor("test")
    GameTestState steveparty$test();
}
