package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A villager or wandering trader token is a static pawn: no trade screen, no head shake. PASS lets the held item
 * (token, wand, name tag...) handle the click.
 */
@Mixin({VillagerEntity.class, WanderingTraderEntity.class})
public abstract class TokenMerchantMixin {
    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void steveparty$pawnsDoNotTrade(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (TokenBase.isToken((Entity) (Object) this)) cir.setReturnValue(ActionResult.PASS);
    }
}
