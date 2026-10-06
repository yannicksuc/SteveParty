package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fr.lordfinn.steveparty.effect.ModEffects;
import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * A creeper token is a game piece: it never explodes, whoever owns it. A fuse lit before the spell (the creeper was
 * already hissing) goes out, and nothing lights it again (flint and steel: see {@link TokenVanillaActionsMixin}).
 * <p>
 * The cloud a creeper leaves when it explodes carries its effects: never the token spell's transformation (the
 * players walking into it would shrink).
 */
@Mixin(CreeperEntity.class)
public abstract class TokenCreeperMixin {
    @Shadow
    @Final
    private static TrackedData<Boolean> IGNITED;
    @Shadow
    private int currentFuseTime;

    @Shadow
    public abstract void setFuseSpeed(int fuseSpeed);

    @Shadow
    public abstract int getFuseSpeed();

    @Inject(method = "tick", at = @At("HEAD"))
    private void steveparty$tokensHaveNoFuse(CallbackInfo ci) {
        CreeperEntity creeper = (CreeperEntity) (Object) this;
        if (!TokenBase.isToken(creeper)) return;
        if (getFuseSpeed() > 0) setFuseSpeed(-1);
        if (creeper.getDataTracker().get(IGNITED)) creeper.getDataTracker().set(IGNITED, false);
        currentFuseTime = 0;
    }

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void steveparty$tokensNeverExplode(CallbackInfo ci) {
        if (TokenBase.isToken((CreeperEntity) (Object) this)) ci.cancel();
    }

    @ModifyExpressionValue(method = "spawnEffectsCloud", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/mob/CreeperEntity;getStatusEffects()Ljava/util/Collection;"))
    private Collection<StatusEffectInstance> steveparty$noTokenSpellInCloud(Collection<StatusEffectInstance> effects) {
        if (effects.stream().noneMatch(effect -> effect.getEffectType().matches(ModEffects.SQUISHED))) return effects;
        return effects.stream().filter(effect -> !effect.getEffectType().matches(ModEffects.SQUISHED)).toList();
    }
}
