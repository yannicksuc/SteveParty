package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.BubbleFloatPose;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a Bubbler's bubble, a biped (player, zombie...) floats instead of sitting as on a mount: legs dangling and
 * swinging, arms drifting ({@link BubbleFloatPose#pose}); a player's sleeves and trousers follow (copied after).
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelBubbleRiderMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void steveparty$floatInBubble(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                          float headYaw, float headPitch, CallbackInfo ci) {
        if (entity.getVehicle() instanceof BubbleEntity) ((BipedEntityModel<?>) (Object) this).riding = false;
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void steveparty$floatPose(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                      float headYaw, float headPitch, CallbackInfo ci) {
        BubbleFloatPose.pose((BipedEntityModel<?>) (Object) this, entity, animationProgress);
    }
}
