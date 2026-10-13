package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Frousseux is a ghost: arrows, tridents, snowballs... fly through it (nothing of theirs hurts it, see
 * {@link FrousseuxEntity#canHurtIt}), never bouncing off it. A thrown potion still bursts on it: potions are what
 * hurts it.
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityFrousseuxMixin {
    @Inject(method = "canHit(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void steveparty$throughFrousseux(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof FrousseuxEntity && !((Object) this instanceof PotionEntity)) cir.setReturnValue(false);
    }
}
