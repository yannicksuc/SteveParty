package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.pipe.PipeTravellerPose;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.AbstractZombieModel;
import net.minecraft.entity.mob.HostileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A biped in a pipe swims, arms ahead ({@link PipeTravellerPose#swimPose}), after this model's own posing. */
@Mixin(AbstractZombieModel.class)
public abstract class ZombieModelPipeSwimMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/mob/HostileEntity;FFFFF)V", at = @At("TAIL"))
    private void steveparty$swimInPipe(HostileEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                       float headYaw, float headPitch, CallbackInfo ci) {
        if (PipeTravellerPose.swimming()) PipeTravellerPose.swimPose((BipedEntityModel<?>) (Object) this, animationProgress);
    }
}
