package fr.lordfinn.steveparty;

import fr.lordfinn.steveparty.mixin.LivingEntityMixin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public interface StatusEffectExtension {
    void onRemoved(LivingEntity livingEntity);
}
