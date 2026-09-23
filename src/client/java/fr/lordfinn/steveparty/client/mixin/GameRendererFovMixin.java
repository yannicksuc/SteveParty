package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void modifyFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            BlockPos posUnder = client.player.getBlockPos().down();
            boolean onGoalPole = client.player.getWorld().getBlockState(posUnder).isOf(ModBlocks.GOAL_POLE)
                    || client.player.getWorld().getBlockState(posUnder.down()).isOf(ModBlocks.GOAL_POLE);

            if (onGoalPole && !client.player.isClimbing()) {
                float progress = 1;
                float flippedFov = (180 - cir.getReturnValueF()) + 180f * progress; // example: add to FOV
                cir.setReturnValue(flippedFov);
            }
        }
    }
}
