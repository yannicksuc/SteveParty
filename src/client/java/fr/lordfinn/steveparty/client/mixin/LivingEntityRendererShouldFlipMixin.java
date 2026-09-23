package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.flip.GoalPoleFlipTracker;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererShouldFlipMixin {

    @Inject(method = "shouldFlipUpsideDown", at = @At("RETURN"), cancellable = true)
    private static void flipOnGoalPole(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && GoalPoleFlipTracker.isOnGoalPole(entity)) {
            cir.setReturnValue(true);
        }
    }
}
