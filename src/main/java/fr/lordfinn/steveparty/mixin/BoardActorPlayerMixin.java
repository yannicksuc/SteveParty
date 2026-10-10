package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.service.BoardActors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The board actors are holograms (see {@link BoardActors}): nothing a player does with a use on them does anything
 * (no taming, riding, bucket, screen, lead, name tag, shears, saddle...), whatever is in hand.
 */
@Mixin(PlayerEntity.class)
public abstract class BoardActorPlayerMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void steveparty$hologramUse(Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (BoardActors.isHologram(entity)) cir.setReturnValue(ActionResult.PASS);
    }
}
