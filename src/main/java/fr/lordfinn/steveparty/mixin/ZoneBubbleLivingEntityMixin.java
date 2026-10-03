package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No effect is given across the border of a mini-game zone in session: a splash potion, a lingering cloud, a tipped
 * arrow of the other side leave the players and the mobs of this one alone ({@link ZoneBorder#blocksEffect}).
 */
@Mixin(LivingEntity.class)
public abstract class ZoneBubbleLivingEntityMixin {

    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$noEffectAcrossZoneBorder(StatusEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (!ZoneBorder.ACTIVE || source == null) return;
        if (ZoneBorder.blocksEffect((LivingEntity) (Object) this, source)) cir.setReturnValue(false);
    }
}
