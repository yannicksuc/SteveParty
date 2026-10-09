package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.StatusEffectExtension;
import fr.lordfinn.steveparty.items.custom.BoxCostumeBlock;
import fr.lordfinn.steveparty.items.custom.BoxCostumeItem;
import fr.lordfinn.steveparty.items.custom.jumpshoes.JumpShoes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    // 1.21.1: one call per removed effect (expired, removed or cleared)
    @Inject(method = "onStatusEffectRemoved", at = @At("HEAD"))
    protected void callOnStatusEffectsRemovedForEntity(StatusEffectInstance statusEffectInstance, CallbackInfo ci) {
        if (!this.getWorld().isClient) {
            ((StatusEffectExtension) statusEffectInstance.getEffectType().value()).steveparty$onRemoved((LivingEntity) (Object) this);
        }
    }

    /** A player hidden in a Box Costume: mobs notice him from much closer, like a mob wearing its own head. */
    @Inject(method = "getAttackDistanceScalingFactor", at = @At("RETURN"), cancellable = true)
    private void steveparty$hiddenInBox(Entity entity, CallbackInfoReturnable<Double> cir) {
        if ((Object) this instanceof PlayerEntity player && BoxCostumeItem.isHiddenInBox(player)) {
            cir.setReturnValue(cir.getReturnValueD() * BoxCostumeItem.HIDDEN_DETECTION_FACTOR);
        }
    }

    /** A player who is a block of the grid in his Box Costume is not pushed around by those bumping into him. */
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void steveparty$boxCostumeBlock(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity player && BoxCostumeBlock.isBlockAligned(player)) {
            cir.setReturnValue(false);
        }
    }

    /** The Triple Jump Shoes' chained jumps, done by the client that moves the player (JumpShoesClient). */
    @Inject(method = "jump", at = @At("TAIL"))
    private void steveparty$jumpShoes(CallbackInfo ci) {
        if (this.getWorld().isClient && (Object) this instanceof PlayerEntity player && JumpShoes.wears(player)) {
            JumpShoes.clientJump.accept(player);
        }
    }
}
