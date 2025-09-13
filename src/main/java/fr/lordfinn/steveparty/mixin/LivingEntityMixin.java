package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.StatusEffectExtension;
import fr.lordfinn.steveparty.utils.JumpTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

import static fr.lordfinn.steveparty.items.ModItems.TRIPLE_JUMP_SHOES;

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

    @Inject(method = "jump", at = @At("TAIL"))
    private void onJump(CallbackInfo ci) {
        if (this.getWorld().isClient && (Object) this instanceof PlayerEntity player) {
            // Vérifie si le joueur porte tes bottes
            if (player.getEquippedStack(EquipmentSlot.FEET).isOf(TRIPLE_JUMP_SHOES)) {
                int combo = JumpTracker.getCombo(player);

                double multiplier = switch (combo) {
                    case 1 -> 1.5; // 2e saut
                    case 2 -> 2; // 3e saut
                    default -> 1.0; // normal
                };

                // Applique la vélocité boostée
                Vec3d vel = player.getVelocity();
                player.setVelocity(vel.x, vel.y * multiplier, vel.z);

                JumpTracker.incrementCombo(player);
            }
        }
    }
}
