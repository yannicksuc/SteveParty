package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererShouldFlipMixin {

    @Inject(method = "shouldFlipUpsideDown", at = @At("RETURN"), cancellable = true)
    private static void flipOnGoalPole(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        boolean original = cir.getReturnValue();
        if (entity instanceof PlayerEntity player) {
            boolean onGoalPole = player.getWorld().getBlockState(player.getBlockPos().down()).isOf(ModBlocks.GOAL_POLE);
            if (onGoalPole && !player.isClimbing()) {
                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(original);
            }
        }
    }
}
