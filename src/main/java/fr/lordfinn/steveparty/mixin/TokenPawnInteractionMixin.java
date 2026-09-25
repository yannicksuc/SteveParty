package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A token is a static pawn: its food does not make it eat, fall in love or grow up (no hearts, no munching). Other
 * player actions on it (shearing, brushing, milking...) and the items acting on entities (tokens, wand, name tag)
 * keep working: PASS lets the held item handle the click.
 */
@Mixin(AnimalEntity.class)
public abstract class TokenPawnInteractionMixin {
    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void steveparty$pawnsDoNotEat(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        AnimalEntity animal = (AnimalEntity) (Object) this;
        if (TokenBase.isToken(animal) && animal.isBreedingItem(player.getStackInHand(hand))) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }
}
