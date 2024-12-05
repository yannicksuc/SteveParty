package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.StatusEffectExtension;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StatusEffect.class)
public abstract class StatusEffectMixin implements StatusEffectExtension {
    @Override
    public void onRemoved(LivingEntity livingEntity) {
    }
}
