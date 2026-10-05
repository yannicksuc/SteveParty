package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.pipe.PipeTravellerPose;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a pipe, a biped (player, zombie, skeleton...) lies head first like a swimmer, arms ahead
 * ({@link PipeTravellerPose#swimPose}); its armour too. Also after the models that pose their arms after the biped one
 * (zombies, skeletons, piglins...).
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelPipeSwimMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void steveparty$swimInPipe(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                       float headYaw, float headPitch, CallbackInfo ci) {
        if (PipeTravellerPose.swimming()) PipeTravellerPose.swimPose((BipedEntityModel<?>) (Object) this, animationProgress);
    }
}
