package fr.lordfinn.steveparty.gametest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import net.minecraft.test.GameTestState;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Each failure said in the console as it happens (test, why), not only in the report.
 * <p>
 * A test failing inside one of its waits empties its waits while they are being gone through: the next one read
 * crashes the whole test server, the failure unreported. Once the test has ended, that crash is only logged.
 */
@Mixin(GameTestState.class)
public abstract class GameTestStateMixin {
    @Unique
    private static final Logger STEVEPARTY$LOGGER = LogUtils.getLogger();

    @WrapMethod(method = "tickTests")
    private void steveparty$endedTestsDoNotCrash(Operation<Void> original) {
        try {
            original.call();
        } catch (RuntimeException e) {
            GameTestState test = (GameTestState) (Object) this;
            STEVEPARTY$LOGGER.warn("{} (ended: {}) broke within its waits", test.getTemplatePath(), test.isCompleted(), e);
            if (!test.isCompleted()) test.fail(e);
        }
    }

    @Inject(method = "fail", at = @At("HEAD"))
    private void steveparty$sayWhy(Throwable throwable, CallbackInfo ci) {
        GameTestState test = (GameTestState) (Object) this;
        if (!test.isCompleted()) STEVEPARTY$LOGGER.warn("FAILED {}: {}", test.getTemplatePath(), throwable.toString());
    }
}
