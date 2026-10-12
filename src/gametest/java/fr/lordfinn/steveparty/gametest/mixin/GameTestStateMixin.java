package fr.lordfinn.steveparty.gametest.mixin;

import net.minecraft.test.GameTestState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A test's tasks scheduled while its tasks run wait until they are all run: the timed tasks' map, growing while it is
 * walked, crashed the test server (fastutil's iterator lost, "wrapped is null").
 */
@Mixin(GameTestState.class)
public abstract class GameTestStateMixin {
    @Unique
    private boolean steveparty$ticking;
    @Unique
    private final List<Map.Entry<Long, Runnable>> steveparty$scheduled = new ArrayList<>();

    @Shadow
    public abstract void runAtTick(long tick, Runnable runnable);

    @Inject(method = "tickTests", at = @At("HEAD"))
    private void steveparty$startTicking(CallbackInfo ci) {
        steveparty$ticking = true;
    }

    @Inject(method = "tickTests", at = @At("RETURN"))
    private void steveparty$stopTicking(CallbackInfo ci) {
        steveparty$ticking = false;
        List<Map.Entry<Long, Runnable>> scheduled = List.copyOf(steveparty$scheduled);
        steveparty$scheduled.clear();
        for (Map.Entry<Long, Runnable> task : scheduled) runAtTick(task.getKey(), task.getValue());
    }

    @Inject(method = "runAtTick", at = @At("HEAD"), cancellable = true)
    private void steveparty$laterWhileTicking(long tick, Runnable runnable, CallbackInfo ci) {
        if (!steveparty$ticking) return;
        steveparty$scheduled.add(Map.entry(tick, runnable));
        ci.cancel();
    }
}
