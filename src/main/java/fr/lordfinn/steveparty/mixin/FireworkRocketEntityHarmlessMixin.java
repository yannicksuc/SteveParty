package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The firework a die bursts into when it goes away is only seen: its explosion hurts no one (the Firecracker module
 * deals its own blast, see {@link DiceEntity}).
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityHarmlessMixin {
    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void steveparty$harmlessDiceFirework(CallbackInfo ci) {
        if (((FireworkRocketEntity) (Object) this).getCommandTags().contains(DiceEntity.HARMLESS_FIREWORK_TAG)) ci.cancel();
    }
}
