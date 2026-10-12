package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.effect.BubbledEffect;
import fr.lordfinn.steveparty.effect.ModEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bosses, board actors and mobs too big for a bubble never get the Bubbled effect ({@link BubbledEffect#canBubble}). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBubbledImmunityMixin {
    @Inject(method = "canHaveStatusEffect", at = @At("HEAD"), cancellable = true)
    private void steveparty$noBubbleForBosses(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (effect.getEffectType().matches(ModEffects.BUBBLED) && !BubbledEffect.canBubble((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
