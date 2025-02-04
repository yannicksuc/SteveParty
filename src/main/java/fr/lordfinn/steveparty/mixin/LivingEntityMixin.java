package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.StatusEffectExtension;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

//
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "onStatusEffectsRemoved", at = @At("HEAD"))
    protected void callOnStatusEffectsRemovedForEntity(Collection<StatusEffectInstance> effects, CallbackInfo ci) {
        if (!this.getWorld().isClient) {
            for (StatusEffectInstance statusEffectInstance : effects) {
                ((StatusEffectExtension) statusEffectInstance.getEffectType().value()).steveparty$onRemoved((LivingEntity) (Object) this);
            }
        }
    }
}
