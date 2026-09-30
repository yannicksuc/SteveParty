package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenBase;
import fr.lordfinn.steveparty.items.custom.TokenItem;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With the Tokenizer Wand in hand, using it on a mob is the wand's business: the mob's own interaction (a villager's
 * trades, feeding or taming, mounting, leads, name tags, sitting...) does not run. Both sides (client and server go
 * through {@link PlayerEntity#interact}), so no trade screen flashes. Spectators keep vanilla behaviour.
 * <p>
 * Same for the Token item when it has something to do with that mob (storing a token, placing one): a llama's chest
 * screen or mounting must not win. When the Token has nothing to do with it, the mob's own interaction runs as usual.
 */
@Mixin(PlayerEntity.class)
public class PlayerWandInteractMixin {

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void steveparty$wandFirst(Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.isSpectator() || !(entity instanceof MobEntity mob)) return;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() instanceof TokenItem token) {
            // The client can't run the capture, but must not start the mob's own interaction either
            if (player.getWorld().isClient && stack.get(ModComponents.ENTITY_DATA_COMPONENT) == null && TokenBase.isToken(mob)) {
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
            ActionResult result = token.useOnEntity(stack, player, mob, hand);
            if (result != ActionResult.PASS) cir.setReturnValue(result);
            return;
        }
        if (!(stack.getItem() instanceof TokenizerWandItem wand)) return;
        ActionResult result = wand.useOnEntity(stack, player, mob, hand);
        // Even a refusal (a boss, someone else's token) must not fall back to the mob's own interaction
        cir.setReturnValue(result == ActionResult.PASS ? ActionResult.FAIL : result);
    }
}
