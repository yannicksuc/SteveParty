package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A splash or lingering potion from outside bursting on or near a Bubbler's bubble pops it first
 * ({@link BubbleEntity#splashedBy}): its riders, out, then take the splash like anyone there. What its own riders throw
 * from inside flies out through it.
 */
@Mixin(PotionEntity.class)
public abstract class PotionEntityBubbleMixin {
    @Inject(method = "onCollision(Lnet/minecraft/util/hit/HitResult;)V", at = @At("HEAD"))
    private void steveparty$popBubblesAround(HitResult hit, CallbackInfo ci) {
        PotionEntity self = (PotionEntity) (Object) this;
        if (self.getWorld() instanceof ServerWorld world) BubbleEntity.splashedBy(world, self);
    }
}
