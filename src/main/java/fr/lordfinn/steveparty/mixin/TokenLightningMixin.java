package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lightning leaves a token as it is: not set on fire (it would set its neighbours alight), not charged (a charged
 * creeper pawn), not turned into another mob (a pig into a zombified piglin, a villager into a witch, a mooshroom's
 * colour). Tokens do not attract lightning either: see {@link TokenLightningTargetMixin}.
 */
@Mixin({Entity.class, CreeperEntity.class, PigEntity.class, VillagerEntity.class, MooshroomEntity.class})
public abstract class TokenLightningMixin {
    @Inject(method = "onStruckByLightning", at = @At("HEAD"), cancellable = true)
    private void steveparty$tokensIgnoreLightning(ServerWorld world, LightningEntity lightning, CallbackInfo ci) {
        if (TokenBase.isToken((Entity) (Object) this)) ci.cancel();
    }
}
