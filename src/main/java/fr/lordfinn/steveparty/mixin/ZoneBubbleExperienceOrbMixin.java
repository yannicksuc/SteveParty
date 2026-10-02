package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.entity.ExperienceOrbEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Experience orbs on either side of a mini-game zone's border don't merge across it. */
@Mixin(ExperienceOrbEntity.class)
public abstract class ZoneBubbleExperienceOrbMixin {

    @Inject(method = "merge(Lnet/minecraft/entity/ExperienceOrbEntity;)V", at = @At("HEAD"), cancellable = true)
    private void steveparty$noMergeAcrossZoneBorder(ExperienceOrbEntity other, CallbackInfo ci) {
        if (!ZoneBorder.ACTIVE) return;
        ExperienceOrbEntity self = (ExperienceOrbEntity) (Object) this;
        if (ZoneBorder.across(self.getWorld(), self.getBlockPos(), other.getBlockPos())) ci.cancel();
    }
}
