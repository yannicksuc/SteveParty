package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.flip.GoalPoleFlipTracker;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Upside-down view while standing on a goal pole.
 * <p>
 * Previously done by returning {@code 360 - fov} from {@code GameRenderer#getFov}, which inverted the
 * projection (reversed FOV effects, flipped hand). The same picture is now obtained with a roll of the
 * camera around its forward axis, smoothly driven by the flip progress. The roll lives in the camera
 * rotation, so the view matrix and the frustum culling stay consistent; the hand is not affected.
 */
@Mixin(Camera.class)
public class CameraGoalPoleRollMixin {
    @Shadow @Final private Quaternionf rotation;

    @Inject(method = "update", at = @At("TAIL"))
    private void steveparty$rollOnGoalPole(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                           boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!(focusedEntity instanceof LivingEntity living)) return;
        float progress = GoalPoleFlipTracker.getCameraProgress(living, tickDelta);
        if (progress > 0.001F) {
            this.rotation.rotateZ((float) Math.PI * progress);
        }
    }
}
