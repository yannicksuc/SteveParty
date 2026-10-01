package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.items.custom.BoxCostumeBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player who is a block of the grid in his Box Costume ({@link BoxCostumeBlock}) is a hard obstacle, like a shulker
 * or a boat: other entities stand on his cube and bump into it.
 */
@Mixin(Entity.class)
public abstract class EntityBoxCostumeCollisionMixin {
    @Inject(method = "isCollidable", at = @At("HEAD"), cancellable = true)
    private void steveparty$boxCostumeBlock(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity player && BoxCostumeBlock.isBlockAligned(player)) cir.setReturnValue(true);
    }
}
