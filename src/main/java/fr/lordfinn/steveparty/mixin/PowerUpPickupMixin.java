package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player of a running party does not pick up power-ups (or dice without Infinity) past what the party lets them
 * carry ({@link PowerUpLimit}): at the limit the item stays on the ground; below it, only what fits is picked up, the
 * rest stays there as its own item.
 */
@Mixin(ItemEntity.class)
public abstract class PowerUpPickupMixin {
    @Inject(method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void steveparty$powerUpLimit(PlayerEntity player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getWorld().isClient || self.cannotPickup()) return;
        ItemStack stack = self.getStack();
        if (!PowerUpLimit.counts(stack)) return;
        int allowed = PowerUpLimit.allowed(player, stack);
        if (allowed >= stack.getCount()) return;
        if (self.getWorld().getTime() % 20 == 0) PowerUpLimit.tellFull(player);
        if (allowed <= 0) {
            ci.cancel();
            return;
        }
        // Only what fits is picked up: the rest stays on the ground
        ItemStack rest = stack.split(stack.getCount() - allowed);
        ItemEntity left = new ItemEntity(self.getWorld(), self.getX(), self.getY(), self.getZ(), rest);
        left.setToDefaultPickupDelay();
        self.getWorld().spawnEntity(left);
        self.setStack(stack);
    }
}
