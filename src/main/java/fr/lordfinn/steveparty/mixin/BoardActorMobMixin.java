package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.service.BoardActors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The board actors are holograms (see {@link BoardActors}): they pick nothing up. */
@Mixin(MobEntity.class)
public abstract class BoardActorMobMixin {
    @Inject(method = "canPickUpLoot", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramLoot(CallbackInfoReturnable<Boolean> cir) {
        if (BoardActors.isHologram((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
