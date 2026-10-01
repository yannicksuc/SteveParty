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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    /** A player hidden in a Box Costume: mobs notice him from much closer, like a mob wearing its own head. */
    @Inject(method = "getAttackDistanceScalingFactor", at = @At("RETURN"), cancellable = true)
    private void steveparty$hiddenInBox(Entity entity, CallbackInfoReturnable<Double> cir) {
        if ((Object) this instanceof PlayerEntity player && fr.lordfinn.steveparty.items.custom.BoxCostumeItem.isHiddenInBox(player)) {
            cir.setReturnValue(cir.getReturnValueD() * fr.lordfinn.steveparty.items.custom.BoxCostumeItem.HIDDEN_DETECTION_FACTOR);
        }
    }

    /** A player who is a block of the grid in his Box Costume is not pushed around by those bumping into him. */
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void steveparty$boxCostumeBlock(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity player && fr.lordfinn.steveparty.items.custom.BoxCostumeBlock.isBlockAligned(player)) {
            cir.setReturnValue(false);
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
