package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.items.custom.parachute.Parachutes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob carrying a parachute removed for good (killed, despawned, discarded) drops it ({@link Parachutes#removed}). */
@Mixin(Entity.class)
public abstract class EntityParachuteMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void steveparty$parachuteDrop(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity living && !self.getWorld().isClient && reason.shouldDestroy()) Parachutes.removed(living);
    }
}
