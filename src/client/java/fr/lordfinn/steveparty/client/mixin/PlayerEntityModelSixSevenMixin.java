package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.FirstPersonArm;
import fr.lordfinn.steveparty.client.entity.SixSevenClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player doing the « 6-7 » ({@link SixSevenClient}), seen from outside: the forearms out, the hands weighing in turn. */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelSixSevenMixin {
    /** After the biped pose, before the sleeves copy it. */
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            shift = At.Shift.AFTER))
    private void steveparty$sixSeven(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                     float headYaw, float headPitch, CallbackInfo ci) {
        if (FirstPersonArm.posing) return;
        SixSevenClient.pose((PlayerEntityModel<?>) (Object) this, entity);
    }
}
