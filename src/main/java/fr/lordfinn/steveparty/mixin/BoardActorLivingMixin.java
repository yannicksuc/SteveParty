package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.service.BoardActors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The board actors are holograms (see {@link BoardActors}): no knockback, and killed by a command they drop nothing (no loot, no equipment, no experience). */
@Mixin(LivingEntity.class)
public abstract class BoardActorLivingMixin {
    @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramKnockback(CallbackInfo ci) {
        if (BoardActors.isHologram((Entity) (Object) this)) ci.cancel();
    }

    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramDrops(CallbackInfo ci) {
        if (BoardActors.isBoardActor((Entity) (Object) this)) ci.cancel();
    }
}
