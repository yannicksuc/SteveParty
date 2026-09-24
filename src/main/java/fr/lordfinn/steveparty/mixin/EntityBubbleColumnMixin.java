package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.blocks.custom.PlasticBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player riding a plastic piece in a bubble column moves at the piece's speed, which is twice what the column
 * allows: the column leaves them alone (it would cap their speed at 0.7 up, 0.3 down).
 */
@Mixin(Entity.class)
public abstract class EntityBubbleColumnMixin {
    @Inject(method = {"onBubbleColumnCollision", "onBubbleColumnSurfaceCollision"}, at = @At("HEAD"), cancellable = true)
    private void steveparty$ridingPlastic(boolean drag, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof PlayerEntity && PlasticBlock.isRidingPlastic(self.getWorld(), self)) ci.cancel();
    }
}
