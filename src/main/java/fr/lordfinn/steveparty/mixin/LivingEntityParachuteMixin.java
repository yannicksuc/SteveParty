package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.items.custom.parachute.Parachutes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The parachute ({@link Parachutes}): its rules each tick (server), its brake on a fall wherever the entity moves (a
 * client its own player, the server its mobs). A mob's dropped when it is removed: EntityParachuteMixin.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityParachuteMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void steveparty$parachuteTick(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getWorld().isClient && !self.isRemoved()) Parachutes.tick(self);
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void steveparty$parachuteBrake(Vec3d movementInput, CallbackInfo ci) {
        Parachutes.brake((LivingEntity) (Object) this);
    }
}
