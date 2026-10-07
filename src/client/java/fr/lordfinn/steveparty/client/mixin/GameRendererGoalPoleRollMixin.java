package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.flip.GoalPoleCameraRoll;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On a goal pole the view rolls over, gradually ({@link GoalPoleCameraRoll}): added where the view tilts when hurt, so
 * the world, the hand and the culling all follow it.
 */
@Mixin(GameRenderer.class)
public class GameRendererGoalPoleRollMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("TAIL"))
    private void steveparty$goalPoleRoll(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        float roll = GoalPoleCameraRoll.rollDegrees(tickDelta);
        if (roll != 0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
    }
}
