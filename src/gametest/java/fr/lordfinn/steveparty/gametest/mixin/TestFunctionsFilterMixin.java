package fr.lordfinn.steveparty.gametest.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.test.TestFunction;
import net.minecraft.test.TestFunctions;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Runs only some tests: {@code ./gradlew runGametest -Ptests=PiggyBank,MagpieNest} passes
 * {@code -Dsteveparty.gametest.only=PiggyBank,MagpieNest}, and only the tests whose id ({@code class.method}, lower
 * case) contains one of the tokens (any case) are kept. Without the property every test runs. No test matched: the
 * run fails, never a silent green.
 */
@Mixin(TestFunctions.class)
public abstract class TestFunctionsFilterMixin {
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "getTestFunctions()Ljava/util/Collection;", at = @At("RETURN"), cancellable = true)
    private static void steveparty$onlyAskedTests(CallbackInfoReturnable<Collection<TestFunction>> cir) {
        String only = System.getProperty("steveparty.gametest.only");
        if (only == null || only.isBlank()) return;
        List<String> tokens = Arrays.stream(only.split(","))
            .map(token -> token.trim().toLowerCase(Locale.ROOT))
            .filter(token -> !token.isEmpty())
            .toList();
        List<TestFunction> kept = cir.getReturnValue().stream()
            .filter(test -> tokens.stream().anyMatch(test.templatePath().toLowerCase(Locale.ROOT)::contains))
            .toList();
        if (kept.isEmpty()) {
            String message = "No GameTest matches -Ptests=" + only + " (tokens are matched in class.method, any case)";
            LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        LOGGER.info("-Ptests={}: {} of {} GameTests kept", only, kept.size(), cir.getReturnValue().size());
        cir.setReturnValue(kept);
    }
}
